# 分级维修闭环实施记录

依据：用户 2026-09-13 批准的分级维修闭环计划。

## 约束与接口

使用当前功能工作树；保持 8080/5173，不改共享数据库、不提交密码、不推送或合并。
故障仍是一类事件；不实现救援车队、换车、成本、货损。
等级 MINOR / ASSISTANCE_REQUIRED；阶段 WAITING_RESCUE / REPAIRING / RECOVERED。
时间均为仿真时间。旧请求与旧场景保持 legacy 行为。

## Task 1: 后端分级故障、配置与兼容

在 TransportRandomEvent 中增加 breakdownLevel、breakdownPhase、rescueWaitMinutes、repairMinutes、repairStartTime、recoveryProcessedTime、recoveryOutcome、breakdownRuleVersion，以及恢复保护所需的原阶段标识。plannedEndTime 继续表示预计恢复，resolvedTime 是模型结束；恢复失败不覆盖其他业务状态，recoveryOutcome 解释原因。
一般故障 MINOR 直接维修，需外援 ASSISTANCE_REQUIRED 等待再维修。阶段由仿真时间推进，半开区间，大步长跨过阶段仍正确；幂等。等待及维修时 BREAKDOWN，速度 0，禁止提前到达、卸货、重调度、超时清理。恢复核对原任务及阶段，保留载货和剩余工作，不重复结算。跨过维修结束只结算其后的驾驶工作。已有旧记录允许新字段 null，并保持恢复行为兼容；新生成的旧接口事件也应保存恢复保护所需信息。

POST random-events/trigger 增加可选 breakdownLevel/rescueWaitMinutes/repairMinutes；新机制需等级，不能与 durationMinutes 混用。MINOR 默认等待 0、维修 60；ASSISTANCE_REQUIRED 默认等待 30、维修 90。非零阶段整数 30..180，总计 <=240，一般等待只能 0；拥堵不能接收故障字段。旧签名调用保持兼容。错误参数 400，冲突 409。
DTO 加入上述公开字段，active/monitor/history 都能读取，归档保留。旧记录显示原版故障维修。

独立 BreakdownDecisionPolicy 不改变已有类型/天气抽取；新场景可选 breakdownPolicy 配置包含 version=breakdown-v2、minorProbability=.7、minorRepairMin/Max=30/60、rescueWaitMin/Max=30/60、assistanceRepairMin/Max=60/120。自动触发仍使用现有 .02 小时概率；自动 v2 发生后用独立 seed/loop/vehicleId 混合流抽等级和各时长。参数需验证上述范围及最大总长。新 preset 创建写入 v2；旧 JSON/已保存场景/import 缺少该字段保持 null/legacy，不默认偷偷升级。场景运行应用、锁定、重置恢复旧配置，归档有实际抽取结果与版本。

测试先行：控制器真实请求绑定/校验；阶段边界、重复 tick、原任务失效、原阶段更换、车辆状态更换、旧接口/旧事件恢复；30+90 故障在150结算仅算后30分钟，含天气变化；相同随机输入可复现且不扰动原抽取；新旧场景配置导入、启动与重置。现有定向32项作为回归，不要求修理已知全量基线问题。

## Task 2: 前端与地图恢复

故障表单独立等级、等待/维修时长；拥堵原时长不变。事件详情显示上述阶段、时间及计时模拟说明，旧记录清晰兜底。统一悬浮窗与后端监控载重、任务编号、状态。修复刷新后异步路线及图标恢复，后端仍唯一推进权威；故障红色且不移动，恢复继续原路段。
WeatherPanel 中场景参数说明需区分 legacy 与 breakdown-v2（v2 自动故障类型权重和分阶段时长），不能继续把新场景显示成单一60..120分钟维修。保持自动开关明确可见。界面中恢复失败需显示 recoveryOutcome 的含义，不能仅看到 RECOVERED 就宣称原任务已恢复。
已定位暂停刷新问题：后端 stop 会 pauseAndCancelPending 路线队列，刷新后内存路线缓存消失，重建请求被拒绝。实现本次运行的 sessionStorage 路线缓存（键关联 runId、assignment、起终点，验证有限合法坐标；有界容量，异常降级不崩溃），成功规划后及时保存，刷新时先读取；重置只清理本模块缓存。暂停时不偷偷恢复后端或路线队列。未缓存路线明确显示待恢复路线并在用户恢复后重试；天气运行定时轮询应补画缺失的 active 任务，而非只查询 new 任务。图标增加稳定 data-vehicle-id 标识，状态换图也保留，便于实际位置验收。
先编写针对生产行为的测试，再最小实现；前端测试及 Vite 构建，桌面/手机组件检查。不得以源码字符串匹配冒充行为测试。

## Task 3: 实机验证与文档

重启最新后端，使用独立 QA 库 vehicle_scheduler_weather_qa_20260911；构造两票货多站任务，分别触发两等级故障；验证暂停、刷新图标出现、故障坐标冻结、恢复移动、最终交付与库存、重复请求和归档。保存证据并如实记录未完成项。不以组件模拟或构建替代真实地图验收。

## 进度

- Task 1: complete（55c3ff4c..30dc89b7）。新增后端机制提交 1ab0810f，审查修复提交 30dc89b7；定向 50 项通过，独立复审通过。修复 JSON 小数时长、参数400优先、原任务失效原因、ACTIVE故障到期边界门禁。报告见 breakdown-v2-backend-report.md。
- Task 2: 主实现提交 0dc3b295，审查修复提交 99c31233。审查发现的三项问题已修复：允许合法整数分钟、天气运行周期补绘缺失active路线、接入历史结果并在事件结束后更新。2026-09-18 前端21项测试通过，三项定向复审PASS；Vite构建及前期桌面/手机组件检查通过。不能替代 Task 3 的真实地图验收。
- Task 3: 部分通过（2026-09-17）。最新后端 d11a52c8 重启后，真实 API 验证两等级、400/409、暂停计时、恢复及最终交付；数据库确认四节点、两票货及库存正确。运行 ef176fd9-f402-4e19-a02c-4d3c01e11975 已归档并重置 QA 数据。地图刷新与恢复移动仍待独立浏览器验收。

## 接口预检

| 配合任务 | 共享约束 | 结论 |
|---|---|---|
| 1 / 2 | 等级、阶段、时间字段 | 按上面固定名称传递，前端不推算恢复 |
| 1 / 3 | 旧场景不升级，新场景带 v2 | 实机测试须显式新建场景 |
| 2 / 3 | 刷新后 marker 恢复 | 不能只检查地图容器，必须检查图标坐标 |
| 1 | 旧 duration 与新阶段互斥 | 兼容保留原服务重载，入口先校验 |
| 2 | 旧数据显示与新模型 | legacy 文案明确，展示后端权威快照 |
| 3 | 数据隔离与固定端口 | 重置仅 QA，不影响共享库 |
