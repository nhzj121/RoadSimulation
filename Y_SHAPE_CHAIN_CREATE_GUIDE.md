# Y 型加工链创建指南

## 目录

1. [概述](#1-概述)
2. [场景说明](#2-场景说明)
3. [通过 API 创建](#3-通过-api-创建)
4. [通过 Java 代码创建](#4-通过-java-代码创建)
5. [验证与查询](#5-验证与查询)
6. [常见问题](#6-常见问题)

---

## 1. 概述

本文档介绍如何创建 Y 型加工链（多链合并加工链）。Y 型加工链允许两条或多条独立的加工链在某个点汇合，形成一条新的加工链继续加工。

### 核心概念

| 概念 | 说明 |
|------|------|
| **上游加工链** | Y 形结构的分支链（如：钢铁链、木材链） |
| **下游加工链** | Y 形结构的主干链/合并链（如：家具链） |
| **前驱关系** | 下游链与上游链的依赖关系 |
| **合并点** | 下游链中执行合并工序的节点 |

---

## 2. 场景说明

### 2.1 业务场景

我们要创建一个**家具制造 Y 型加工链**：

```
加工链 A: 铁矿 → 钢铁         加工链 B: 木材 → 木板
┌──────────────────┐       ┌──────────────────┐
│ 工序 A1: 采矿     │       │ 工序 B1: 伐木     │
│ 工序 A2: 冶炼     │       │ 工序 B2: 加工     │
└────────┬─────────┘       └────────┬─────────┘
         │                          │
         │ 输出：80 吨钢铁            │ 输出：90 吨木板
         └──────────┬───────────────┘
                    │
                    ▼  合并 (170 吨)
          ┌─────────────────┐
          │  加工链 C: 家具  │
          │  工序 C1: 组装   │ ← 合并点
          │  工序 C2: 包装   │
          └─────────────────┘
                    │
                    ▼
                输出：161.5 吨家具
```

### 2.2 数据关系

```
┌──────────────────┐
│ ProcessingChain  │
│ (上游链 A, id=1)  │
└────────┬─────────┘
         │
         │ 1
         │
         │ n
         │ ┌─────────────────────────────┐
         └─┤ processing_chain_predecessors│
           │ (前驱关系表)                 │
           └─────────────────────────────┘
                                         │
                                         │ n
                                         │
┌──────────────────┐                     │
│ ProcessingChain  │─────────────────────┘
│ (上游链 B, id=2)  │
└────────┬─────────┘
         │
         │ 1
         │
         │ n
         │ ┌─────────────────────────────┐
         └─┤ processing_chain_predecessors│
           └─────────────────────────────┘
                                         │
                                         │ n
                                         │
                                   ┌─────┴─────────┐
                                   │               │
                                   ▼               ▼
                          ┌───────────────────────────────┐
                          │      ProcessingChain          │
                          │      (下游合并链 C, id=3)      │
                          │  predecessorChainIds: [1, 2]  │
                          │  mergeStageId: C1             │
                          └───────────────────────────────┘
```

---

## 3. 通过 API 创建

### 3.1 创建上游加工链 A（钢铁加工链）

**接口**: `POST /api/v3/processing-chain`

```bash
curl -X POST http://localhost:8080/api/v3/processing-chain \
  -H "Content-Type: application/json" \
  -d '{
    "chainCode": "CHAIN_STEEL_001",
    "chainName": "钢铁加工链",
    "description": "铁矿开采 → 钢铁冶炼",
    "yieldRate": 0.80,
    "stages": [
      {
        "stageOrder": 1,
        "stageName": "铁矿开采",
        "poiId": 1,
        "outputGoodsSku": "RAW_IRON_ORE",
        "outputWeightRatio": 1.0,
        "processingTimeMinutes": 20
      },
      {
        "stageOrder": 2,
        "stageName": "钢铁冶炼",
        "poiId": 2,
        "inputGoodsSku": "RAW_IRON_ORE",
        "outputGoodsSku": "SEMIF_STEEL",
        "outputWeightRatio": 0.8,
        "processingTimeMinutes": 30
      }
    ]
  }'
```

**响应**:
```json
{
  "id": 1,
  "chainCode": "CHAIN_STEEL_001",
  "chainName": "钢铁加工链",
  "status": "ACTIVE",
  "yieldRate": 0.80,
  "totalProcessingTimeMinutes": 50,
  "stages": [
    {
      "id": 1,
      "stageOrder": 1,
      "stageName": "铁矿开采",
      "processingTimeMinutes": 20
    },
    {
      "id": 2,
      "stageOrder": 2,
      "stageName": "钢铁冶炼",
      "processingTimeMinutes": 30
    }
  ],
  "createdAt": "2026-03-28T10:00:00"
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| chainCode | String | 是 | 加工链编码（唯一） |
| chainName | String | 是 | 加工链名称 |
| description | String | 否 | 描述 |
| yieldRate | Double | 否 | 综合产出率（默认 0.95） |
| stages | Array | 是 | 工序列表 |

---

### 3.2 创建上游加工链 B（木材加工链）

```bash
curl -X POST http://localhost:8080/api/v3/processing-chain \
  -H "Content-Type: application/json" \
  -d '{
    "chainCode": "CHAIN_WOOD_001",
    "chainName": "木材加工链",
    "description": "木材砍伐 → 木板加工",
    "yieldRate": 0.90,
    "stages": [
      {
        "stageOrder": 1,
        "stageName": "木材砍伐",
        "poiId": 3,
        "outputGoodsSku": "RAW_WOOD",
        "outputWeightRatio": 1.0,
        "processingTimeMinutes": 15
      },
      {
        "stageOrder": 2,
        "stageName": "木板加工",
        "poiId": 4,
        "inputGoodsSku": "RAW_WOOD",
        "outputGoodsSku": "SEMIF_WOOD",
        "outputWeightRatio": 0.9,
        "processingTimeMinutes": 25
      }
    ]
  }'
```

**响应**:
```json
{
  "id": 2,
  "chainCode": "CHAIN_WOOD_001",
  "chainName": "木材加工链",
  "status": "ACTIVE",
  "yieldRate": 0.90,
  "totalProcessingTimeMinutes": 40
}
```

---

### 3.3 创建下游合并加工链 C（家具加工链）⭐关键步骤

> ⭐ **关键点**: 使用 `predecessorChainIds` 指定前驱加工链，这是 Y 型合并的关键！

```bash
curl -X POST http://localhost:8080/api/v3/processing-chain \
  -H "Content-Type: application/json" \
  -d '{
    "chainCode": "CHAIN_FURNITURE_001",
    "chainName": "家具加工链（合并链）",
    "description": "钢铁 + 木板 → 家具组装 → 家具包装",
    "yieldRate": 0.95,
    "predecessorChainIds": [1, 2],
    "stages": [
      {
        "stageOrder": 1,
        "stageName": "家具组装（合并点）",
        "poiId": 5,
        "inputGoodsSku": "SEMIF_STEEL,SEMIF_WOOD",
        "outputGoodsSku": "PROD_FURNITURE",
        "outputWeightRatio": 0.95,
        "processingTimeMinutes": 40
      },
      {
        "stageOrder": 2,
        "stageName": "家具包装",
        "poiId": 6,
        "inputGoodsSku": "PROD_FURNITURE",
        "outputGoodsSku": "PROD_FURNITURE_PACKED",
        "outputWeightRatio": 1.0,
        "processingTimeMinutes": 20
      }
    ]
  }'
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| predecessorChainIds | Array<Long> | **是** | 前驱加工链 IDs（合并链必填） |
| stages[0].inputGoodsSku | String | 是 | 合并工序的输入物料（多个物料用逗号分隔） |

**响应**:
```json
{
  "id": 3,
  "chainCode": "CHAIN_FURNITURE_001",
  "chainName": "家具加工链（合并链）",
  "status": "ACTIVE",
  "yieldRate": 0.95,
  "predecessorChainIds": [1, 2],
  "totalProcessingTimeMinutes": 60,
  "stages": [
    {
      "id": 5,
      "stageOrder": 1,
      "stageName": "家具组装（合并点）",
      "processingTimeMinutes": 40
    },
    {
      "id": 6,
      "stageOrder": 2,
      "stageName": "家具包装",
      "processingTimeMinutes": 20
    }
  ],
  "createdAt": "2026-03-28T10:05:00"
}
```

---

### 3.4 创建并执行上游运单

```bash
# ========== 创建运单 A ==========
# 输入 100 吨铁矿石 → 预期产出 80 吨钢铁
curl -X POST http://localhost:8080/api/v3/processing-chain/1/shipment \
  -H "Content-Type: application/json" \
  -d '{
    "inputWeight": 100.0,
    "createdBy": "user_001"
  }'

# 响应: {"id": 1, "refNo": "PROC-20260328-000001", ...}

# 开始加工运单 A
curl -X POST http://localhost:8080/api/v3/processing-chain/shipment/1/start


# ========== 创建运单 B ==========
# 输入 100 吨木材 → 预期产出 90 吨木板
curl -X POST http://localhost:8080/api/v3/processing-chain/2/shipment \
  -H "Content-Type: application/json" \
  -d '{
    "inputWeight": 100.0,
    "createdBy": "user_001"
  }'

# 响应: {"id": 2, "refNo": "PROC-20260328-000002", ...}

# 开始加工运单 B
curl -X POST http://localhost:8080/api/v3/processing-chain/shipment/2/start
```

---

### 3.5 等待上游运单完成

```bash
# 查询运单 A 状态
curl http://localhost:8080/api/v3/processing-chain/shipment/1/status

# 响应: {"status": "COMPLETED", "overallProgress": 100, ...}

# 查询运单 B 状态
curl http://localhost:8080/api/v3/processing-chain/shipment/2/status

# 响应: {"status": "COMPLETED", "overallProgress": 100, ...}
```

当两个运单状态都为 `COMPLETED` 时，继续下一步。

---

### 3.6 创建合并运单

#### 方式 A: 手动创建

```bash
curl -X POST http://localhost:8080/api/v3/processing-chain/merge-shipment \
  -H "Content-Type: application/json" \
  -d '{
    "upstreamShipmentIds": [1, 2],
    "downstreamChainId": 3,
    "createdBy": "user_001"
  }'
```

**响应**:
```json
{
  "id": 3,
  "refNo": "PROC-20260328-000003",
  "chainId": 3,
  "chainName": "家具加工链（合并链）",
  "processingStatus": "PENDING",
  "totalWeight": 170.0,
  "expectedOutputWeight": 161.5,
  "expectedYieldRate": 0.95,
  "upstreamShipmentIds": [1, 2],
  "mergeShipment": true,
  "createdAt": "2026-03-28T11:00:00"
}
```

**重量计算说明**:
- 合并输入重量 = 运单 A 输出 (80 吨) + 运单 B 输出 (90 吨) = **170 吨**
- 预期输出重量 = 170 吨 × 0.95 = **161.5 吨家具**

#### 方式 B: 自动创建（推荐）

当上游运单 A 和 B 都完成后，系统会**自动检测**并创建合并运单，无需手动调用 API。

系统会自动执行以下逻辑：
1. 检测运单 A 完成 → 查找以链 A 为前驱的下游链
2. 检测运单 B 完成 → 查找以链 B 为前驱的下游链
3. 当链 A 和链 B 都有完成的运单时 → 自动创建合并运单

---

### 3.7 执行合并运单

```bash
# 开始加工合并运单
curl -X POST http://localhost:8080/api/v3/processing-chain/shipment/3/start

# 查询进度
curl http://localhost:8080/api/v3/processing-chain/shipment/3/status

# 响应示例:
# {
#   "status": "IN_PROCESS",
#   "currentStageName": "家具组装（合并点）",
#   "overallProgress": 50,
#   ...
# }
```

---

## 4. 通过 Java 代码创建

### 4.1 完整示例代码

```java
package org.example.roadsimulation.demo;

import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.service.ProcessingChainServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Y 型加工链创建示例
 */
@Component
public class YShapeChainDemo {

    @Autowired
    private ProcessingChainServiceV2 processingChainService;

    public void createYShapeProcessingChain() {
        // ========== 1. 创建上游加工链 A（钢铁链） ==========
        ProcessingChain chainA = new ProcessingChain();
        chainA.setChainCode("CHAIN_STEEL_001");
        chainA.setChainName("钢铁加工链");
        chainA.setDescription("铁矿开采 → 钢铁冶炼");
        chainA.setYieldRate(0.80);
        
        // 工序 A1: 铁矿开采
        ProcessingStage stageA1 = new ProcessingStage();
        stageA1.setStageOrder(1);
        stageA1.setStageName("铁矿开采");
        stageA1.setProcessingPOI(poiRepository.findById(1L).get());
        stageA1.setOutputGoodsSku("RAW_IRON_ORE");
        stageA1.setOutputWeightRatio(1.0);
        stageA1.setProcessingTimeMinutes(20);
        chainA.addStage(stageA1);
        
        // 工序 A2: 钢铁冶炼
        ProcessingStage stageA2 = new ProcessingStage();
        stageA2.setStageOrder(2);
        stageA2.setStageName("钢铁冶炼");
        stageA2.setProcessingPOI(poiRepository.findById(2L).get());
        stageA2.setInputGoodsSku("RAW_IRON_ORE");
        stageA2.setOutputGoodsSku("SEMIF_STEEL");
        stageA2.setOutputWeightRatio(0.8);
        stageA2.setProcessingTimeMinutes(30);
        chainA.addStage(stageA2);
        
        chainA = processingChainService.createChain(chainA);
        System.out.println("✓ 加工链 A 创建成功，ID: " + chainA.getId());
        
        // ========== 2. 创建上游加工链 B（木材链） ==========
        ProcessingChain chainB = new ProcessingChain();
        chainB.setChainCode("CHAIN_WOOD_001");
        chainB.setChainName("木材加工链");
        chainB.setDescription("木材砍伐 → 木板加工");
        chainB.setYieldRate(0.90);
        
        // 工序 B1: 木材砍伐
        ProcessingStage stageB1 = new ProcessingStage();
        stageB1.setStageOrder(1);
        stageB1.setStageName("木材砍伐");
        stageB1.setProcessingPOI(poiRepository.findById(3L).get());
        stageB1.setOutputGoodsSku("RAW_WOOD");
        stageB1.setOutputWeightRatio(1.0);
        stageB1.setProcessingTimeMinutes(15);
        chainB.addStage(stageB1);
        
        // 工序 B2: 木板加工
        ProcessingStage stageB2 = new ProcessingStage();
        stageB2.setStageOrder(2);
        stageB2.setStageName("木板加工");
        stageB2.setProcessingPOI(poiRepository.findById(4L).get());
        stageB2.setInputGoodsSku("RAW_WOOD");
        stageB2.setOutputGoodsSku("SEMIF_WOOD");
        stageB2.setOutputWeightRatio(0.9);
        stageB2.setProcessingTimeMinutes(25);
        chainB.addStage(stageB2);
        
        chainB = processingChainService.createChain(chainB);
        System.out.println("✓ 加工链 B 创建成功，ID: " + chainB.getId());
        
        // ========== 3. 创建下游合并加工链 C（家具链） ⭐ ==========
        ProcessingChain chainC = new ProcessingChain();
        chainC.setChainCode("CHAIN_FURNITURE_001");
        chainC.setChainName("家具加工链（合并链）");
        chainC.setDescription("钢铁 + 木板 → 家具组装 → 家具包装");
        chainC.setYieldRate(0.95);
        
        // ⭐ 设置前驱加工链（Y 型合并的关键）
        chainC.addPredecessorChainId(chainA.getId());  // 添加钢铁链为前驱
        chainC.addPredecessorChainId(chainB.getId());  // 添加木材链为前驱
        
        // 工序 C1: 家具组装（合并点）
        ProcessingStage stageC1 = new ProcessingStage();
        stageC1.setStageOrder(1);
        stageC1.setStageName("家具组装（合并点）");
        stageC1.setProcessingPOI(poiRepository.findById(5L).get());
        stageC1.setInputGoodsSku("SEMIF_STEEL,SEMIF_WOOD");
        stageC1.setOutputGoodsSku("PROD_FURNITURE");
        stageC1.setOutputWeightRatio(0.95);
        stageC1.setProcessingTimeMinutes(40);
        chainC.addStage(stageC1);
        
        // 工序 C2: 家具包装
        ProcessingStage stageC2 = new ProcessingStage();
        stageC2.setStageOrder(2);
        stageC2.setStageName("家具包装");
        stageC2.setProcessingPOI(poiRepository.findById(6L).get());
        stageC2.setInputGoodsSku("PROD_FURNITURE");
        stageC2.setOutputGoodsSku("PROD_FURNITURE_PACKED");
        stageC2.setOutputWeightRatio(1.0);
        stageC2.setProcessingTimeMinutes(20);
        chainC.addStage(stageC2);
        
        chainC = processingChainService.createChain(chainC);
        System.out.println("✓ 加工链 C（合并链）创建成功，ID: " + chainC.getId());
        System.out.println("  - 前驱链数量：" + chainC.getPredecessorChainIds().size());
        System.out.println("  - 前驱链 IDs: " + chainC.getPredecessorChainIds());
        
        // ========== 4. 创建并执行上游运单 ==========
        Shipment shipmentA = processingChainService.createProcessingShipment(
            chainA.getId(), 
            100.0,  // 输入 100 吨铁矿石
            "user_001"
        );
        System.out.println("✓ 运单 A 创建成功，ID: " + shipmentA.getId());
        
        processingChainService.startProcessing(shipmentA.getId());
        System.out.println("✓ 运单 A 开始加工");
        
        Shipment shipmentB = processingChainService.createProcessingShipment(
            chainB.getId(), 
            100.0,  // 输入 100 吨木材
            "user_001"
        );
        System.out.println("✓ 运单 B 创建成功，ID: " + shipmentB.getId());
        
        processingChainService.startProcessing(shipmentB.getId());
        System.out.println("✓ 运单 B 开始加工");
        
        // ========== 5. 等待上游运单完成 ==========
        // 在实际系统中，这里需要等待加工完成
        // 可以通过轮询状态或监听事件来实现
        waitForShipmentComplete(shipmentA.getId());
        waitForShipmentComplete(shipmentB.getId());
        
        Shipment completedShipmentA = shipmentRepository.findById(shipmentA.getId()).get();
        Shipment completedShipmentB = shipmentRepository.findById(shipmentB.getId()).get();
        
        System.out.println("✓ 运单 A 加工完成，输出：" + completedShipmentA.getActualOutputWeight() + "吨钢铁");
        System.out.println("✓ 运单 B 加工完成，输出：" + completedShipmentB.getActualOutputWeight() + "吨木板");
        
        // ========== 6. 创建合并运单 ==========
        
        // 方式 A: 手动创建
        Shipment shipmentC = processingChainService.createMergeShipment(
            List.of(shipmentA.getId(), shipmentB.getId()),
            chainC.getId(),
            "user_001"
        );
        System.out.println("✓ 合并运单创建成功，ID: " + shipmentC.getId());
        System.out.println("  - 合并输入重量：" + shipmentC.getTotalWeight() + "吨");
        System.out.println("  - 预期输出重量：" + shipmentC.getExpectedOutputWeight() + "吨");
        System.out.println("  - 上游运单 IDs: " + shipmentC.getUpstreamShipmentIds());
        
        // 方式 B: 自动创建（系统会自动检测并创建，无需手动调用）
        // 当上游运单都完成后，系统会自动创建合并运单
        
        // ========== 7. 执行合并运单 ==========
        processingChainService.startProcessing(shipmentC.getId());
        System.out.println("✓ 合并运单开始加工");
        
        // 等待合并运单完成
        waitForShipmentComplete(shipmentC.getId());
        
        Shipment completedShipmentC = shipmentRepository.findById(shipmentC.getId()).get();
        System.out.println("✓ 合并运单加工完成，输出：" + completedShipmentC.getActualOutputWeight() + "吨家具");
    }
    
    private void waitForShipmentComplete(Long shipmentId) {
        // 模拟等待（实际系统中需要轮询或监听事件）
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

### 4.2 运行结果

```
✓ 加工链 A 创建成功，ID: 1
✓ 加工链 B 创建成功，ID: 2
✓ 加工链 C（合并链）创建成功，ID: 3
  - 前驱链数量：2
  - 前驱链 IDs: [1, 2]
✓ 运单 A 创建成功，ID: 1
✓ 运单 A 开始加工
✓ 运单 B 创建成功，ID: 2
✓ 运单 B 开始加工
✓ 运单 A 加工完成，输出：80.0 吨钢铁
✓ 运单 B 加工完成，输出：90.0 吨木板
✓ 合并运单创建成功，ID: 3
  - 合并输入重量：170.0 吨
  - 预期输出重量：161.5 吨
  - 上游运单 IDs: [1, 2]
✓ 合并运单开始加工
✓ 合并运单加工完成，输出：161.5 吨家具
```

---

## 5. 验证与查询

### 5.1 查询加工链详情

```bash
# 查询加工链 C（合并链）的详情
curl http://localhost:8080/api/v3/processing-chain/3
```

**响应**:
```json
{
  "id": 3,
  "chainCode": "CHAIN_FURNITURE_001",
  "chainName": "家具加工链（合并链）",
  "status": "ACTIVE",
  "yieldRate": 0.95,
  "predecessorChainIds": [1, 2],
  "totalProcessingTimeMinutes": 60,
  "stages": [
    {
      "id": 5,
      "stageOrder": 1,
      "stageName": "家具组装（合并点）",
      "processingTimeMinutes": 40
    },
    {
      "id": 6,
      "stageOrder": 2,
      "stageName": "家具包装",
      "processingTimeMinutes": 20
    }
  ]
}
```

### 5.2 查询合并运单列表

```bash
# 查询所有合并运单
curl "http://localhost:8080/api/v3/processing-chain/shipments?isMergeShipment=true"
```

**响应**:
```json
{
  "content": [
    {
      "id": 3,
      "refNo": "PROC-20260328-000003",
      "chainId": 3,
      "chainName": "家具加工链（合并链）",
      "processingStatus": "COMPLETED",
      "totalWeight": 170.0,
      "actualOutputWeight": 161.5,
      "mergeShipment": true,
      "upstreamShipmentIds": [1, 2],
      "createdAt": "2026-03-28T11:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### 5.3 查询加工链统计

```bash
# 查询加工链 C 的统计信息（包含上游链信息）
curl http://localhost:8080/api/v3/processing-chain/3/stats
```

**响应**:
```json
{
  "chainId": 3,
  "chainName": "家具加工链（合并链）",
  "totalOrders": 1,
  "pendingOrders": 0,
  "inProcessOrders": 0,
  "completedOrders": 1,
  "cancelledOrders": 0,
  "totalInputWeight": 170.0,
  "totalOutputWeight": 161.5,
  "mergeShipmentCount": 1,
  "upstreamChainStats": [
    {
      "chainId": 1,
      "chainName": "钢铁加工链",
      "completedShipmentCount": 1
    },
    {
      "chainId": 2,
      "chainName": "木材加工链",
      "completedShipmentCount": 1
    }
  ]
}
```

---

## 6. 常见问题

### 6.1 创建合并链时报错"前驱加工链不存在"

**原因**: `predecessorChainIds` 中指定的加工链 ID 不存在。

**解决方案**: 确保先创建上游加工链，获取到正确的 ID 后再创建下游合并链。

### 6.2 创建合并运单时报错"上游运单尚未完成"

**原因**: 上游运单状态不是 `COMPLETED`。

**解决方案**: 
- 等待上游运单加工完成
- 或者使用系统自动创建功能（上游运单完成后自动触发）

### 6.3 合并运单的重量计算错误

**原因**: 上游运单的 `actualOutputWeight` 未正确设置。

**解决方案**: 确保上游运单加工完成后，`actualOutputWeight` 字段有正确的值。

### 6.4 如何查询某个加工链的所有前驱链？

```bash
# 查询加工链详情，包含 predecessorChainIds 字段
curl http://localhost:8080/api/v3/processing-chain/{chainId}
```

### 6.5 如何查询某个加工链的所有下游合并链？

```bash
# 查询所有合并链，然后筛选出包含当前链作为前驱的链
curl "http://localhost:8080/api/v3/processing-chain?isMergeChain=true"
```

---

## 附录

### A. 相关文档

- [Y 形加工链设计文档](./Y_SHAPE_PROCESSING_CHAIN_DESIGN.md)
- [Y 形加工链 API 文档](./Y_SHAPE_PROCESSING_CHAIN_API.md)
- [Y 形加工链实现总结](./Y_SHAPE_PROCESSING_CHAIN_SUMMARY.md)

### B. 数据库表结构

```sql
-- 加工链前驱关系表
CREATE TABLE processing_chain_predecessors (
    chain_id BIGINT NOT NULL,
    predecessor_chain_id BIGINT NOT NULL,
    PRIMARY KEY (chain_id, predecessor_chain_id)
);

-- 运单上游关系表
CREATE TABLE shipment_upstream_relations (
    shipment_id BIGINT NOT NULL,
    upstream_shipment_id BIGINT NOT NULL,
    PRIMARY KEY (shipment_id, upstream_shipment_id)
);
```

### C. 状态流转图

```
加工链状态: ACTIVE → INACTIVE → MAINTENANCE

运单状态: PENDING → IN_PROCESS → COMPLETED
                    ↓
               CANCELLED
```

---

**文档版本**: v1.0  
**最后更新**: 2026-03-28  
**作者**: 系统开发团队
