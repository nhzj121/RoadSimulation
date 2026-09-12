# 天气模块向实验沙箱交接

本模块负责天气环境与驾驶进度。沙箱同学负责方案/批次界面、实验循环与指标分析。本周不修改算法对比循环。

## 场景与运行分离

场景保存天气完整时间表；每次加载场景创建独立运行编号。所有时间片是相对仿真起点的分钟偏移，区间为 `[startMinute,endMinute)`。时间表结束后晴天系数为 1。

相同天气场景提供相同外部环境，不要求两种调度算法的每辆车受到相同影响。车辆出发时间不同，天气暴露差异是实验结果的一部分。现有单车故障/拥堵仍按车辆状态筛选，不保证跨算法同一序列；只比较天气时请关闭单车自动事件。

## HTTP 接口

| 方法与路径（前缀 `/api/simulation`） | 用途 |
|---|---|
| `POST /weather/scenarios` | 按 `name,preset,seed` 生成并保存场景；preset 为 BASELINE/DEMO/AUTO |
| `GET /weather/scenarios` | 已保存场景列表 |
| `POST /weather/scenarios/import` | 导入完整 JSON 时间表，生成新场景 ID |
| `GET /weather/scenarios/{id}` | 导出场景；回放使用其时间表，不重抽随机数 |
| `GET /weather/current` | 当前天气、系数、下一变化时间、场景/运行编号与人工干预标记 |
| `GET /weather/runs/{id}` | 导出场景与该次运行的驾驶阶段、事件记录 |
| `POST /start` | 新增可选 `scenarioId` 与 `externalExperimentId` |
| `GET /monitor/active` | 新增 `weather` 和车辆驾驶进度字段 |

天气端点直接返回 JSON；普通仿真接口沿用 `success/message/data` 包装。前端 API 适配层兼容两者。

创建自动场景：将 `examples/weather-auto-request.json` POST 到 `/weather/scenarios`。保存返回的 `id`，再启动：

```json
{"scenarioId": 1, "externalExperimentId": "batch-A", "strategy": "ORIGINAL"}
```

三套完整时间表分别见 `examples/weather-baseline.json`、`examples/weather-demo.json`、`examples/weather-auto.json`，POST 到 `/weather/scenarios/import` 即可。自动示例与 weather-v1 固定种子生成器的一致性由测试检查。`weather-auto-request.json` 仅是创建请求，不是完整时间表，不能代替已导出的场景用于归档。

时间片必须从 0 开始、连续有序、无重叠且长度为正；天气为 SUNNY/RAIN/SNOW/FOG，系数在 `(0,1]`。非法导入返回 400。运行后锁定选择，暂停/恢复沿用原运行；重置后可选择其他场景。旧客户端启动不传场景保持兼容路径。

## 进度与统计口径

每段扣减正常行驶工作量：`仿真秒数 × 天气系数 × 拥堵系数`，故障系数为 0。驾驶所需初始工作量取原状态的正常时长。天气或拥堵未结束时，剩余工作量归零仍可以到达。

- `drivingProgress`：0～1；`remainingDrivingSeconds`：剩余的正常驾驶工作量，不是当前天气下的预计剩余时间。
- `effectiveSpeedFactor`：后端合并后的系数，前端不得再乘一次天气或拥堵。
- `drivingPhaseKey`、`drivingLegIndex`：区分任务、驾驶阶段、多站节点；到达请求携带 `phaseKey,legIndex`。
- `affectedSeconds`：驾驶阶段受到降速或故障影响的仿真时长。
- `lostWorkSeconds`：因降速/暂停损失的正常行驶工作量，与最终任务晚到时间不同。
- `modelCompletedTime`：积分模型完成时间；`observedCompletedTime`：主循环/请求实际观察到完成的时间。
- 旧 `delaySeconds` 仍表示单车事件持续时长，界面显示“已持续”。

示例：正常工作量 60 分钟、全程雨天系数 0.8，模型在 75 分钟完成；每 30 分钟检查一次可能到 90 分钟才处理。不能把两个时间混作同一个指标。

## 集成边界

`WeatherEnvironmentService.load` 读取保存场景，`at` 查询当前已加载运行在指定仿真时刻的天气；`DrivingProgressService.integrate` 提供纯区间积分，可用于沙箱单独的进度对象。

服务调用示例（由沙箱维护者接入其隔离运行，本周未接入算法对比循环）：

```java
WeatherScenarioDTO scene = weatherEnvironmentService.load(scenarioId);
// 无共享运行状态的天气查询：偏移来自沙箱自己的仿真起点。
var slice = WeatherEnvironmentService.sliceAt(scene, elapsedSimulationSeconds);
double weatherFactor = slice == null ? 1.0 : slice.speedFactor();
// 调用方先按天气/事件边界切段，p 必须属于自己的任务、驾驶阶段和路段。
double effectiveFactor = brokenDown ? 0.0 : weatherFactor * congestionFactor;
DrivingProgressService.integrate(p, segmentStart, segmentEnd, effectiveFactor);
// 普通仿真运行的导出；不要把其它实验的结果写入这个运行。
Map<String, Object> archived = weatherEnvironmentService.exportRun(runId);
```

沙箱需要先保留自己的实验配置、初始车辆/任务快照和仿真起点，再在独立运行中装载相同天气时间表。请勿直接与正在运行的普通仿真共享可变运行状态。现有算法对比模式没有在本周自动启用天气，接入时必须由其维护者明确调用环境/进度服务并处理运行隔离。

本周继续使用 MySQL/JPA，不要求采用老师示例中的 PostGIS、InfluxDB 或 Parquet。暂不调整成本函数，天气系数与生成权重是演示假设，实验报告应一并导出说明。
