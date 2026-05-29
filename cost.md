# Cost 成本函数实现总结

本文档总结当前 `cost-1` 分支上已经实现的成本函数体系。

## 1. 总体结构

当前成本函数分为两层：

```text
单车成本：
CostA, CostB, CostC, CostD, CostE, CostG, CostH, CostI

全局成本：
CostF + 所有车辆平均 TotalCost
```

说明：

- `CostA~E/G/H/I` 用于计算每辆车自己的 `TotalCost`。
- `CostF` 是未分配任务惩罚，属于全局成本，不强行归因到某辆车。
- 所有单车 Cost 都先计算原始值，再基于当前车辆集合做动态 Min-Max 归一化。

接口：

```text
GET /api/simulation/vehicle-costs
```

主要实现位置：

```text
src/main/java/org/example/roadsimulation/service/GetCostService.java
src/main/java/org/example/roadsimulation/controller/SimulationController.java
src/main/java/org/example/roadsimulation/dto/VehicleCostDTO.java
src/main/java/org/example/roadsimulation/dto/VehicleCostSummaryDTO.java
```

测试位置：

```text
src/test/java/org/example/roadsimulation/service/GetCostServiceVehicleCostTest.java
```

## 2. 动态 Min-Max 归一化

当前没有使用固定归一化范围，也没有使用：

```text
某辆车 Cost / 全车队该 Cost 总和
```

而是使用当前车辆集合的动态 Min-Max：

```text
NormCostX_i = (CostX_i - CostX_min) / (CostX_max - CostX_min)
```

其中：

```text
CostX_min = 当前所有车辆 CostX 的最小值
CostX_max = 当前所有车辆 CostX 的最大值
```

防呆：

```text
如果 CostX_max <= CostX_min，则 NormCostX_i = 0
如果结果小于 0，则截断为 0
如果结果大于 1，则截断为 1
如果结果为 NaN 或 Infinity，则返回 0
```

## 3. CostA：直接运行成本

设计目标：

衡量车辆运行中的绝对空耗，主要关注等待时间和空驶里程。

公式：

```text
CostA_i = 0.5 * Twaiting_i + 0.5 * Dempty_i
```

字段来源：

```text
Twaiting_i =
  vehicle.loadingWaitTime
+ vehicle.unloadingWaitTime
+ vehicle.waitingAssignmentTime

Dempty_i = vehicle.emptyDrivingDistance
```

单位：

```text
Twaiting_i：小时
Dempty_i：公里
```

## 4. CostB：运输效率惩罚成本

设计目标：

衡量车辆运行效率，关注空驶比例、等待比例和最差等待情况。

公式：

```text
CostB_i =
  0.4 * (Dempty_i / Dtotal_i)
+ 0.5 * (Twaiting_i / Ttransport_i)
+ 0.1 * Tworst_i
```

当前实现：

```text
Tworst_i 使用 Twaiting_i / Ttransport_i 作为代理值
```

字段来源：

```text
Dempty_i = vehicle.emptyDrivingDistance
Dtotal_i = vehicle.totalDrivingDistance
Twaiting_i = 车辆等待时间
Ttransport_i = vehicle.totalDrivingTime
```

防呆：

```text
如果 Dtotal_i <= 0，则 Dempty_i / Dtotal_i = 0
如果 Ttransport_i <= 0，则 Twaiting_i / Ttransport_i = 0
```

## 5. CostC：重量运能浪费成本

设计目标：

衡量车辆重量维度的装载利用率。

公式：

```text
CapacityUtilization_i = Cactual_i / Ctheory_i
CostC_i = 1 - CapacityUtilization_i
```

字段来源：

```text
Ctheory_i = vehicle.maxLoadCapacity * loadedDistance_i
Cactual_i = vehicle.currentLoad * loadedDistance_i
loadedDistance_i = max(0, vehicle.totalDrivingDistance - vehicle.emptyDrivingDistance)
```

防呆：

```text
如果 Ctheory_i <= 0，则 CostC_i = 0
CostC_i 会被截断到 0~1
```

## 6. CostD：综合经济损耗成本

设计目标：

衡量经济层面的综合损耗，包含运能浪费、时间损耗和最差损耗代理。

公式：

```text
CostD_i =
  0.4 * utilizationWasteCost_i
+ 0.4 * (Twaiting_i + Ttransport_i)
+ 0.2 * worstLossProxy_i
```

其中：

```text
utilizationWasteCost_i = 1 - CapacityUtilization_i
worstLossProxy_i = utilizationWasteCost_i + (Twaiting_i / Ttransport_i)
```

说明：

当前 `CostD` 是一个代理经济损耗模型，后续如果有真实油耗、司机成本、过路费等字段，可以替换为更真实的货币成本。

## 7. CostE：车辆负载均衡成本

设计目标：

衡量单车工作量相对车队平均工作量的偏离程度，避免少数车辆过忙或过闲。

单车工作量：

```text
Workload_i = Tdriving_i + 0.5 * Twaiting_i
```

车队平均工作量：

```text
AvgWorkload = Σ Workload_i / n
```

成本公式：

```text
CostE_i = abs(Workload_i - AvgWorkload) / AvgWorkload
```

防呆：

```text
如果 AvgWorkload <= 0，则 CostE_i = 0
```

说明：

这里使用的是“单车偏离平均值”，不是全局变异系数。因为当前目标是计算每辆车自己的 `TotalCost`。

## 8. CostF：未分配任务惩罚

设计目标：

防止调度策略通过不分配困难任务来降低车辆成本。

公式：

```text
CostF = unassignedTaskCount / totalTaskCount
```

字段来源：

```text
totalTaskCount = shipmentItemRepository.count()
unassignedTaskCount = 状态为 ShipmentItemStatus.NOT_ASSIGNED 的 ShipmentItem 数量
```

防呆：

```text
如果 totalTaskCount <= 0，则 CostF = 0
```

说明：

`CostF` 是全局成本，不进入某辆车的 `TotalCost`。

## 9. CostG：路径绕路成本

设计目标：

衡量车辆实际行驶距离相对任务基准路线距离的额外比例。

公式：

```text
CostG_i =
  max(0, actualRouteDistance_i - baseRouteDistance_i)
  /
  baseRouteDistance_i
```

字段来源：

```text
actualRouteDistance_i = 该车所有 assignment.totalDrivingDistance 之和
baseRouteDistance_i = 该车所有 assignment.route.distance 之和
```

兜底逻辑：

```text
如果 assignment.totalDrivingDistance <= 0：
    assignmentActual = assignment.route.distance + assignment.emptyDrivingDistance

如果 assignment.route.distance <= 0：
    assignmentBase = assignmentActual - assignment.emptyDrivingDistance
```

防呆：

```text
如果 baseRouteDistance_i <= 0，则 CostG_i = 0
```

说明：

当前 `CostG` 实际上表示“额外行驶比例”，可能包含空驶、多点任务、拼车绕路等额外距离。

## 10. CostH：体积浪费成本

设计目标：

补充重量运能成本的不足，衡量车辆货箱体积利用率。

公式：

```text
VolumeUtilization_i = actualVolume_i / cargoVolume_i
CostH_i = 1 - VolumeUtilization_i
```

字段来源：

```text
actualVolume_i = 该车所有 assignment.shipmentItems.volume 之和
cargoVolume_i = vehicle.cargoVolume
```

防呆：

```text
如果 cargoVolume_i <= 0，则 CostH_i = 0
CostH_i 会被截断到 0~1
```

说明：

如果实际体积超过车辆容积，当前成本会被截断为 0。超体积本身应由启发式算法或硬约束层处理。

## 11. CostI：时间超限成本

设计目标：

衡量车辆任务实际执行耗时是否超过路线预计耗时。

公式：

```text
CostI_i =
  max(0, actualAssignmentHours_i - estimatedAssignmentHours_i)
  /
  estimatedAssignmentHours_i
```

字段来源：

```text
actualAssignmentHours_i =
  Σ Duration.between(assignment.startTime, assignment.endTime)

estimatedAssignmentHours_i =
  Σ assignment.route.estimatedTime
```

统计条件：

只统计满足以下条件的任务：

```text
assignment.startTime != null
assignment.endTime != null
assignment.route != null
assignment.route.estimatedTime > 0
```

防呆：

```text
如果 estimatedAssignmentHours_i <= 0，则 CostI_i = 0
如果 actualAssignmentHours_i <= estimatedAssignmentHours_i，则 CostI_i = 0
```

## 12. 单车 TotalCost

单车总成本公式：

```text
TotalCost_i =
  0.15 * NormCostA_i
+ 0.15 * NormCostB_i
+ 0.18 * NormCostC_i
+ 0.12 * NormCostD_i
+ 0.12 * NormCostE_i
+ 0.10 * NormCostG_i
+ 0.08 * NormCostH_i
+ 0.10 * NormCostI_i
```

权重和为 1。

当前没有把 `CostF` 放入单车总成本，因为未分配任务通常无法准确归因到某一辆车。

## 13. 全局 GlobalCost

全局成本公式：

```text
GlobalCost =
  0.75 * averageVehicleTotalCost
+ 0.25 * unassignedTaskCost
```

其中：

```text
averageVehicleTotalCost = 所有车辆 TotalCost 的平均值
unassignedTaskCost = CostF
```

## 14. 接口返回内容

接口：

```text
GET /api/simulation/vehicle-costs
```

返回内容包括：

```text
weightA~weightI
globalWeightVehicleCost
globalWeightUnassignedCost
costA~costI 的 min/max
vehicleCount
totalTaskCount
unassignedTaskCount
unassignedTaskCost
averageTotalCost
globalCost
vehicleCosts
```

每个 `vehicleCosts` 元素包括：

```text
vehicleId
licensePlate
costA~costI
normalizedCostA~normalizedCostI
totalCost
totalWaitingHours
totalTransportHours
emptyDistanceKm
totalDistanceKm
theoryCapacity
actualCapacity
workload
averageWorkload
actualRouteDistanceKm
baseRouteDistanceKm
actualVolume
cargoVolume
actualAssignmentHours
estimatedAssignmentHours
```

## 15. 测试方式

纯单元测试：

```text
src/test/java/org/example/roadsimulation/service/GetCostServiceVehicleCostTest.java
```

测试覆盖：

```text
CostA
CostB
CostC
CostD
CostE
CostF
CostG
CostH
CostI
动态 Min-Max min/max
归一化值
单车 TotalCost
averageTotalCost
GlobalCost
```

理论运行命令：

```bash
bash mvnw -Dtest=GetCostServiceVehicleCostTest test
```

注意：

当前仓库中已有 `ProcessingChainServiceV3Test` 编译失败，原因是它引用了不存在的：

```text
POI.POIType.FACTORY
```

因此 Maven 在 `testCompile` 阶段会被该文件挡住。主代码打包可以通过：

```bash
bash mvnw -Dmaven.test.skip=true package
```
