# 随机运输事件分支与 master 功能整合记录

## 1. 工作位置与交付边界

- 工作树：`C:/Users/19522/.codex/worktrees/random-events-integration/RoadSimulation`。
- 本地分支：`codex/random-events-integration-20260929`。
- master 基点：`08f0b432e5268e7b8a9ee14cceeaa88b634103da`。
- 待合并分支提交：`7a8d061d6fbec3c21d51067968d093e85daedfd1`。
- Git 文本冲突及本轮适配已经完成。本记录随整合分支的本地合并提交保存；不推送、不合入 master。
- 本地收尾将原合并内容、后续修正及必要新增文件一起精确暂存，避免只提交早期索引而遗漏已验收的最终实现。
- 主项目工作区的已跟踪文件未修改。未访问、迁移或写入本机 `vehicle_scheduler`。

本文描述本次整合后的行为。事件分支随合并引入的早期报告与示例只作为历史开发材料，不代表本次最终语义。

## 2. 生命周期与天气的权威关系

保留 master 的规范仿真 tick、AssignmentLeg 实际进度、后端到达判定、装卸及交付结算。前端到达/装货回调仅作为兼容确认，不再改变业务状态。

普通 start 和 step 都先建立天气运行，再推进主循环。正式运行的天气场景取代旧周期环境；旧周期实现保留给历史及兼容测试，不叠加到天气上。未指定天气场景时使用晴天。

每个 tick 按天气边界、拥堵/故障开始结束边界和换车交接边界切片：

1. 自动事件在 tick 开始处作出决定。
2. 到期修复与可完成交接在 tick 结束边界结算。
3. 实际距离、实际行驶时间、能耗和排放按各有效窗口推进，暂停窗口不产生行驶里程。
4. master 仍负责路段完成、后续装卸和库存结算；重复 tick 不重复记账。

监控卸货状态时投影刚完成的路段，包括最后一个路段；不能错误显示下一待行驶路段或倒退到倒数第二段。

### 天气来源

- `SEEDED_GENERATION`：按种子、生成器版本、时间片编号和间隔派生独立随机流。延长覆盖时长不改变已有时间上的天气，也不消耗需求、司机等随机领域的流。原末片被截断时，其结束时间随延长改变，这是覆盖长度变化，不是天气重抽。
- `EXPLICIT_TIMELINE`：用户提供从 0 开始、连续、不重叠的天气片段。不自动填补空洞。
- 普通天气场景：时间线结束后显式退回 `SUNNY`；不是隐式重抽天气。
- 沙箱运行规格：根据 `totalLoops × tickDurationSeconds` 编译完整时间线。生成模式必须覆盖整个时长；显式模式不足则拒绝，发布结果的超时策略为 `REJECT`。
- 当前生成限制：最长一年（525600 分钟），最多 10000 片；速度系数在 `(0,1]`，四项天气权重非负且总和为正。权重顺序为 `SUNNY/RAIN/SNOW/FOG`。

天气和单车事件配置独立。天气导入的旧事件字段不再隐式启用自动事件；自动事件是否启用，由启动选项和冻结的事件配置决定。运行开始后不允许热修改，暂停再启动沿用原冻结配置，reset 后才能选择新配置。

## 3. 司机、换车与资源占用

- 任务必须有可用、与执行车辆关联的司机。偏好只用于稳定排序，不放宽资源约束。
- 普通分配与换车预约共用司机占用服务，使用数据库行锁并刷新锁定实体，避免两个事务都依据旧的 IDLE 缓存完成预约。
- 替换车必须使用自身关联的空闲司机，并同时预约车辆、司机。
- 原司机在等待交接期间继续占用；交接成功后才释放。失败/取消仅释放该事件自己的预约。
- 不修改 `driver_vehicle` 静态关联。交接以仿真时间写入司机历史，记录原/新司机。
- 正在预约的司机不能被普通状态修改或删除接口释放；偏好更新也取得行锁，避免覆盖并发预约状态。
- 取消任务不会把 SCRAPPED 车辆恢复为 IDLE，也不会释放其他事件持有的替换车预约。
- 手动事件通过主循环生命周期锁，不能与 tick 或 reset 交叉写入；reset 期间返回冲突。
- 保留“不在应用启动时随机补种司机”的 master 规则。

## 4. 换车后的实际执行事实与评价

新增 `transport_execution_segment`，逐 tick/片段记录实际车辆、司机、时间窗、距离、载重、额定容量、能耗和排放。路段唯一片段键为 `leg_id + loop_index + fragment_index`。

AssignmentLeg 的原规划车辆和路线不改写。换车前后共用同一规划路段，但执行片段归属实际执行者；不同车辆排放档位在路段汇总中标为 `MULTIPLE`。评价读取片段容量和能耗，不能一直使用原车容量评价换车后的执行。

容量缺失时片段允许保留 NULL，并将能耗事实标为 INVALID；不得因此阻断运输。缺失的评价事实不伪装成零。

旧周期环境中的网络覆盖、环境风险等未由事件天气建模的指标保持缺失状态，因此评价快照可能为 PARTIAL。天气作用于实际行驶和能耗，不等于已有所有环境指标都能量化。

旧计划成本和部分兼容指标仍保留原口径。本次不宣称所有成本接口已统一为实际执行账本，也未修改优化算法目标。

## 5. 沙箱运行规格 v2

新增独立的 `SandboxRunSpecificationV2`、编译结果与不可变 Revision。保持既有基线、场景选择/覆盖、随机协议和车辆初态规则；不复用 ScenarioSnapshot v0。

v2 用 `weather`、`events` 替代 v1 的周期 `environment`，包含：

- 原有场景 Revision 引用及其哈希；
- 仿真时钟与轮数、PRODUCTION 需求、调度算法配置；
- 车辆初始化和司机行为配置；
- 顶层种子和既有随机协议；
- 天气来源、生成参数或显式时间线；
- 事件是否启用、是否自动触发、拥堵/故障参数和故障决策版本。

顶层种子派生天气及事件领域子种子。既有随机领域 ID 不改名；仅追加 `WEATHER_TIMELINE`、`TRANSPORT_EVENT_DECISION`、`TRANSPORT_EVENT_DURATION`、`TRANSPORT_BREAKDOWN_DETAIL`。固定初始 POI 仍优先于随机初态。

发布结果冻结完整天气时间线及事件配置，新增天气、事件配置指纹，并纳入运行规格/准备事实核验。显示名称和说明不改变业务指纹；同族相同定义复用 Revision。

已发布 v1 JSON 和哈希原样保留。旧周期环境不自动转换为天气：新正式草稿/发布必须是 v2；v1 仍可导出、历史编译、准备与校验，但不能作为新的正式运行上下文。

运行上下文验证 v2 发布产物、控制标记、车辆初态及指纹。全部随机入口使用同一个版本无关的顶层种子入口。

默认模板：`src/main/resources/sandbox/runs/default-production-original-v2.json`。模板引用已有 `all-eligible` 场景 Revision，实际发布前必须确认控制库中存在对应 Revision 和哈希，不自动创建或改写场景。

### 状态与 CLI

新增控制结构 `sandbox-control-schema/v4`：安全标记增加运行契约版本、天气指纹和事件配置指纹。基础/场景重新准备清空活动运行标记，但保留已发布场景和运行规格历史。

准备的终态仍为 `RUN_SPEC_READY`，不是可执行仿真；沙箱启动阻断器继续生效。

独立入口为 `org.example.roadsimulation.sandbox.cli.SandboxWorkspaceCli`，不启动普通 Spring 应用。IDEA 程序参数示例：

```text
--command=compile-run-spec --run-spec=classpath:sandbox/runs/default-production-original-v2.json
--command=save-run-draft --run-spec=classpath:sandbox/runs/default-production-original-v2.json
--command=publish-run-spec --run-spec-key=production-weather-v2
--command=prepare-run --run-spec-key=production-weather-v2 --revision=<实际发布编号>
--command=verify-run
--command=export-run-spec --run-spec-key=production-weather-v2 --revision=<实际发布编号>
```

以上每行是一次独立调用。沿用 `SANDBOX_DB_URL`、`SANDBOX_DB_USER`、`SANDBOX_DB_PASSWORD`；密码只来自进程环境。编译运行规格需要读取已发布场景，因此该命令也需要沙箱连接配置。不能回退连接源库。

`breakdown-v3` 当前继续保持原分支固定的轻微故障概率 0.6、换车概率 0.1；不能把“有配置字段”解释为所有概率都已开放任意修改。

## 6. 数据库迁移与首次本机启动

**本次没有执行本机源库迁移，也没有运行源库上的普通仿真。**

业务库增量脚本：`src/main/resources/sandbox/schema/migrations/20260929-transport-weather-integration.sql`。针对当前 MariaDB 10.4，包含司机预约列、车辆状态及预约约束、天气/事件/换车/实际执行片段表和相关扩展列。脚本可重复执行，不更新基线业务记录，不包含 USE/DROP DATABASE。

沙箱业务表 DDL 同步到 `sandbox-schema/v1` 的兼容结构，控制库管理员预置脚本升级到 v4，保留已发布历史。基础/场景/运行准备使用版本化 DDL 重建业务表。

普通环境和沙箱环境均使用 `ddl-auto=validate`。因此旧本机数据库未完成结构迁移时，普通应用会校验失败，这是预期的安全行为，不能通过改回 `update` 掩盖。

实际迁移前必须另行确认数据库、备份及结构差异。若曾运行早期事件实现，还要检查 `transport_random_event.vehicle_id/assignment_id` 上的旧外键：事件历史采用快照引用，不应随任务删除受阻。仓库的 `RandomEventSchemaMigration` 现在仅是显式维护工具，不再在应用启动时自动删约束；上述增量 SQL 不猜测或删除未知外键名。

密码保持 `${DB_PASSWORD:}` 和沙箱环境变量，未写入源码或本记录。

## 7. 本次验证记录

最终复验使用 JDK 17.0.18，编译目标 `release 17`：273 项、0 失败、0 错误、1 跳过，Maven `BUILD SUCCESS`（2026-09-29 20:03:42，Asia/Shanghai）。

其中 10 项 Testcontainers 测试均实际执行且通过：基础准备 2 项、场景准备 2 项、运行准备 3 项、结构/迁移/并发预约 3 项。唯一跳过的是需显式开启的源库司机基线采集工具测试，未为验收开启源库写入或重新采集。其余单元与 H2 回归共 263 项（含该跳过项）。

前端四组行为测试已复验：43 项全部通过；`vue-tsc --noEmit` 和 Vite 生产构建通过（1528 模块）。JS 主包约 1327 KiB，存在超过 500 KiB 的包体警告，未改变依赖版本或锁文件。

复验命令：

```powershell
# JAVA_HOME 指向实际 JDK 17；使用本机 Maven 或 mvnw.cmd。
mvn -o '-Dtest=*Test,!ProductionFourChainDockerAuditTest,!ProductionFourChainRealDispatchDockerAuditTest,TransportIntegrationSchemaIT,SandboxWorkspacePreparerIT,SandboxScenarioWorkspacePreparerIT,SandboxRunWorkspacePreparerIT' test

# 在 Vue3 目录执行。
node --test tests/weather-animation.test.cjs tests/breakdown-v2.test.cjs tests/replacement-rendering.test.cjs tests/replacement-recovery.test.cjs
npm run build
```

数据库集成只使用 Docker 中的 MariaDB 10.4.32 临时容器，固定测试库名 `vehicle_scheduler_sandbox`。不使用 XAMPP 自动回退。保留测试日志于工作树 `target/surefire-reports/`，不把生成日志提交到仓库。

验收覆盖基础/场景/运行重复恢复与 A→B→A、历史 v1 原文不变、v2 发布与指纹核验、实际随机流调用、基础准备清除活动运行标记但保留历史、全部 JPA 实体 validate、显式业务迁移重复执行、司机并发预约。

旧 ScenarioSnapshot v0 设计 JSON 放入 `src/test/resources/sandbox/archive/`，只服务历史兼容测试，不参与正式基础恢复。

最终工作树对原 HEAD 的 `git diff --check` 通过，`git ls-files -u` 为空，主项目工作区已跟踪文件仍无改动。此次本地收尾在提交前另行核验最终索引，提交后核验两个父提交、干净工作区及 master 引用未移动。未改动已验收的实现代码，仅更新本记录并进行 Git 收尾。

## 8. 剩余限制与建议后续操作

1. 未进行本机真实高德地图交互和完整浏览器人工验收。需要检查长时间运行、手动事件、无替换车/无空闲司机、换车后刷新、暂停恢复、重复 reset。
2. 普通天气运行的活动上下文仍在内存中；本次不承诺 JVM 重启后从数据库自动续跑。运行历史可导出，但不是完整重演功能。
3. 若天气冻结已经提交而后续启动配置回调失败，运行保持停止，可能需要 reset 后重新选择；不应误认为已启动成功。
4. 执行片段和事件查询可能随运行量增加而增长，评价当前按路段读取片段；大规模性能优化未列入本次。
5. 旧零距离、正耗时兼容路段仍按 master 的时间型逻辑处理，不当成道路行驶；天气不用于该兼容路段距离计算。
6. 前端构建的包体警告应记录为性能风险，不等同于类型或构建失败。
7. 沙箱尚未开放可执行仿真、路径冻结、每轮重演和最终比较实验。本次只能完成 v2 定义、发布、准备与校验。

本地合并提交按用户确认的范围收尾。后续建议先审阅本次提交与迁移，另行授权本机数据库迁移及人工运行；远程推送和合入 master 均等待后续明确授权。本次不执行数据库迁移、实际仿真、远程推送或 master 合并。
