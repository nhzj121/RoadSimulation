# Y 形加工链（多链合并）功能实现总结

## 执行日期
2026-03-28

## 功能概述

实现了 Y 形加工链（多链合并）功能，支持两条或多条独立的加工链在某一点汇合，形成一条新的加工链继续加工。

### 应用场景示例

```
加工链 A: 铁矿→钢铁         加工链 B: 木材→木板
┌──────────────────┐       ┌──────────────────┐
│ 工序 A1: 采矿     │       │ 工序 B1: 伐木     │
│ 工序 A2: 冶炼     │       │ 工序 B2: 加工     │
└────────┬─────────┘       └────────┬─────────┘
         │                          │
         │ 输出：钢铁 (80 吨)         │ 输出：木板 (90 吨)
         └──────────┬───────────────┘
                    │
                    ▼
          ┌──────────────────┐
          │  加工链 C: 家具   │
          │  工序 C1: 组装    │ ← 合并点
          │  工序 C2: 包装    │
          └──────────────────┘
                    │
                    ▼
                成品：家具 (161 吨)
```

---

## 一、数据模型变更

### 1.1 ProcessingChain 实体新增字段

```java
/**
 * 前驱加工链 IDs（用于 Y 形加工链合并）
 * 例如：加工链 C 的 predecessorChainIds = [A.id, B.id]
 */
@ElementCollection
@CollectionTable(name = "processing_chain_predecessors", 
                 joinColumns = @JoinColumn(name = "chain_id"))
@Column(name = "predecessor_chain_id")
private Set<Long> predecessorChainIds = new HashSet<>();

/**
 * 合并工序 ID（在本链中的哪个工序进行合并）
 */
@Column(name = "merge_stage_id")
private Long mergeStageId;

/**
 * 输入物料 JSON（描述从各上游链输入的物料）
 * 格式：{"chainId_A": "SEMIF_STEEL", "chainId_B": "SEMIF_WOOD"}
 */
@Column(name = "input_materials", columnDefinition = "TEXT")
private String inputMaterials;
```

### 1.2 Shipment 实体新增字段

```java
/**
 * 上游运单 IDs（用于 Y 形加工链合并）
 * 例如：运单 C 的 upstreamShipmentIds = [A.id, B.id]
 */
@ElementCollection
@CollectionTable(name = "shipment_upstream_relations", 
                 joinColumns = @JoinColumn(name = "shipment_id"))
@Column(name = "upstream_shipment_id")
private Set<Long> upstreamShipmentIds = new HashSet<>();
```

### 1.3 新增辅助方法

**ProcessingChain**:
- `getPredecessorChainIds()` / `setPredecessorChainIds()`
- `addPredecessorChainId(Long chainId)`
- `isMergeChain()` - 判断是否是合并加工链

**Shipment**:
- `getUpstreamShipmentIds()` / `setUpstreamShipmentIds()`
- `addUpstreamShipmentId(Long shipmentId)`
- `isMergeShipment()` - 判断是否是合并运单

---

## 二、数据库变更

### 2.1 新增数据表

```sql
-- 加工链前驱关系表
CREATE TABLE processing_chain_predecessors (
    chain_id BIGINT NOT NULL,
    predecessor_chain_id BIGINT NOT NULL,
    PRIMARY KEY (chain_id, predecessor_chain_id),
    FOREIGN KEY (chain_id) REFERENCES processing_chain(id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_chain_id) REFERENCES processing_chain(id) ON DELETE CASCADE
);

-- 运单上游关系表
CREATE TABLE shipment_upstream_relations (
    shipment_id BIGINT NOT NULL,
    upstream_shipment_id BIGINT NOT NULL,
    PRIMARY KEY (shipment_id, upstream_shipment_id),
    FOREIGN KEY (shipment_id) REFERENCES shipment(id) ON DELETE CASCADE,
    FOREIGN KEY (upstream_shipment_id) REFERENCES shipment(id) ON DELETE CASCADE
);
```

### 2.2 加工链表新增字段

```sql
ALTER TABLE processing_chain 
ADD COLUMN merge_stage_id BIGINT,
ADD COLUMN input_materials TEXT;
```

---

## 三、Repository 层变更

### 3.1 ProcessingChainRepository

```java
/**
 * 查找所有以前驱加工链 ID 为条件的合并加工链
 */
@Query("SELECT pc FROM ProcessingChain pc JOIN pc.predecessorChainIds pId WHERE pId = :predecessorChainId")
List<ProcessingChain> findByPredecessorChainId(@Param("predecessorChainId") Long predecessorChainId);

/**
 * 查找所有合并加工链（有前驱的加工链）
 */
@Query("SELECT pc FROM ProcessingChain pc WHERE SIZE(pc.predecessorChainIds) > 0")
List<ProcessingChain> findAllMergeChains();
```

### 3.2 ShipmentRepository

```java
/**
 * 根据多个加工链 ID 和加工状态查询运单（用于 Y 形加工链合并）
 */
List<Shipment> findByProcessingChainIdInAndProcessingStatus(
    List<Long> chainIds, 
    Shipment.ProcessingStatus status
);

/**
 * 查找所有合并运单（有上游运单的运单）
 */
@Query("SELECT s FROM Shipment s WHERE SIZE(s.upstreamShipmentIds) > 0 AND s.processingShipment = true")
List<Shipment> findAllMergeShipments();

/**
 * 根据上游运单 ID 查找下游合并运单
 */
@Query("SELECT s FROM Shipment s JOIN s.upstreamShipmentIds uId WHERE uId = :upstreamShipmentId")
List<Shipment> findByUpstreamShipmentId(@Param("upstreamShipmentId") Long upstreamShipmentId);
```

---

## 四、Service 层变更

### 4.1 ProcessingChainServiceV2 接口新增方法

```java
/**
 * 创建合并运单（Y 形加工链的下游运单）
 * @param upstreamShipmentIds 上游运单 ID 列表
 * @param downstreamChainId 下游加工链 ID
 * @param createdBy 创建人
 * @return 合并运单
 */
Shipment createMergeShipment(List<Long> upstreamShipmentIds, Long downstreamChainId, String createdBy);

/**
 * 检查并自动创建合并运单
 * 当上游所有运单完成后，自动创建下游合并运单
 * @param completedShipmentId 刚完成的运单 ID
 */
void checkAndAutoCreateMergeShipment(Long completedShipmentId);
```

### 4.2 ProcessingChainServiceV3Impl 实现逻辑

**createMergeShipment 方法流程**：
1. 验证所有上游运单已完成
2. 获取下游加工链
3. 计算合并后的总输入重量（所有上游运单的实际输出重量之和）
4. 创建合并运单
5. 为下游链的每个工序创建物料项

**checkAndAutoCreateMergeShipment 方法流程**：
1. 查找所有以当前运单所在加工链为前驱的下游加工链
2. 检查下游链的所有前驱链是否都有完成的运单
3. 如果所有前驱链都有完成的运单，创建合并运单
4. 避免重复创建

**自动触发机制**：
- 在 `checkAndCompleteShipment` 方法中添加调用
- 当运单加工完成时，自动检查并触发合并运单创建

---

## 五、测试验证

### 5.1 测试类

**测试类**: `YShapeProcessingChainTest`
**测试方法数**: 9

### 5.2 测试用例

| 序号 | 测试方法 | 功能 | 状态 |
|------|---------|------|------|
| 1 | `testCreateChainA` | 创建上游加工链 A（铁矿→钢铁） | ✅ |
| 2 | `testCreateChainB` | 创建上游加工链 B（木材→木板） | ✅ |
| 3 | `testCreateChainC` | 创建下游合并加工链 C（钢铁 + 木板→家具） | ✅ |
| 4 | `testCreateAndExecuteShipmentA` | 创建并执行上游运单 A | ✅ |
| 5 | `testCreateAndExecuteShipmentB` | 创建并执行上游运单 B | ✅ |
| 6 | `testCreateMergeShipment` | 创建合并运单 C（Y 形合并） | ✅ |
| 7 | `testExecuteMergeShipment` | 执行合并运单 C | ✅ |
| 8 | `testAutoCreateMergeShipment` | 测试自动创建合并运单 | ✅ |
| 99 | `cleanupTestData` | 清理测试数据 | ✅ |

### 5.3 测试结果

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

---

## 六、使用示例

### 6.1 创建 Y 形加工链

```java
// 1. 创建上游加工链 A：铁矿→钢铁
ProcessingChain chainA = new ProcessingChain();
chainA.setChainCode("CHAIN_STEEL");
chainA.setChainName("钢铁加工链");
chainA.setYieldRate(0.80);
// ... 添加工序 ...
processingChainService.createChain(chainA);

// 2. 创建上游加工链 B：木材→木板
ProcessingChain chainB = new ProcessingChain();
chainB.setChainCode("CHAIN_WOOD");
chainB.setChainName("木材加工链");
chainB.setYieldRate(0.90);
// ... 添加工序 ...
processingChainService.createChain(chainB);

// 3. 创建下游合并加工链 C：钢铁 + 木板→家具
ProcessingChain chainC = new ProcessingChain();
chainC.setChainCode("CHAIN_FURNITURE");
chainC.setChainName("家具加工链（合并链）");
chainC.setYieldRate(0.95);

// 设置前驱加工链（Y 形合并的关键）
chainC.addPredecessorChainId(chainA.getId());
chainC.addPredecessorChainId(chainB.getId());
// ... 添加工序 ...
processingChainService.createChain(chainC);
```

### 6.2 创建并执行运单

```java
// 1. 创建并执行上游运单 A
Shipment shipmentA = processingChainService.createProcessingShipment(
    chainA.getId(), 100.0, "user"
);
processingChainService.startProcessing(shipmentA.getId());
// ... 等待加工完成 ...

// 2. 创建并执行上游运单 B
Shipment shipmentB = processingChainService.createProcessingShipment(
    chainB.getId(), 100.0, "user"
);
processingChainService.startProcessing(shipmentB.getId());
// ... 等待加工完成 ...

// 3. 自动创建合并运单 C
// 当 shipmentA 和 shipmentB 都完成后，系统会自动创建合并运单
// 或者手动创建：
Shipment shipmentC = processingChainService.createMergeShipment(
    List.of(shipmentA.getId(), shipmentB.getId()),
    chainC.getId(),
    "user"
);

// 4. 执行合并运单
processingChainService.startProcessing(shipmentC.getId());
```

---

## 七、文件清单

### 7.1 修改的文件

| 文件 | 变更内容 |
|------|----------|
| `ProcessingChain.java` | 添加 predecessorChainIds、mergeStageId、inputMaterials 字段 |
| `Shipment.java` | 添加 upstreamShipmentIds 字段 |
| `ProcessingChainRepository.java` | 添加查询前驱关系的方法 |
| `ShipmentRepository.java` | 添加查询合并运单的方法 |
| `ProcessingChainServiceV2.java` | 添加合并相关方法接口 |
| `ProcessingChainServiceV3Impl.java` | 实现合并逻辑 |

### 7.2 新增的文件

| 文件 | 说明 |
|------|------|
| `create_y_shape_processing_chain_tables.sql` | Y 形加工链数据库迁移脚本 |
| `YShapeProcessingChainTest.java` | Y 形加工链测试类 |

---

## 八、注意事项

### 8.1 数据一致性

- 合并运单创建时，会验证所有上游运单必须已完成
- 自动创建合并运单时，会检查避免重复创建

### 8.2 重量计算

- 合并运单的输入重量 = 所有上游运单的实际输出重量之和
- 合并运单的预期输出重量 = 合并输入重量 × 下游加工链产出率

### 8.3 事务管理

- 所有创建和更新操作都在事务中执行
- 测试类使用 `@Rollback(true)` 保证测试数据不污染数据库

---

## 九、下一步工作

- [ ] 前端界面支持 Y 形加工链配置
- [ ] 可视化展示加工链合并关系
- [ ] 支持更复杂的 DAG 结构（多合并点）
- [ ] 优化合并运单的自动触发机制

---

**实现完成日期**: 2026-03-28
**测试通过率**: 100% (9/9)
**编译状态**: ✅ BUILD SUCCESS
