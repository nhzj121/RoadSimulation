# Demand-driven production domain

This branch introduces a production domain and removes the legacy shipment-as-processing-order model.

## Domain model

- `ProcessingChain`: static process definition.
- `ProductionPlan`: one final-product demand and its backward-calculated stage quantities.
- `ProductionPlanNode`: planned input/output quantity for one stage in a plan.
- `ProductionBatch`: runtime execution of a plan.
- `ProcessingStageExecution`: actual execution record for one stage in a batch.
- `Shipment`: transport demand only.
- `Assignment`: vehicle execution task created by the existing dispatch system.

## Execution flow

1. Create a random final-product demand at the last stage of a chain.
2. Propagate the demand backward using each stage's `outputWeightRatio`.
3. Release the plan to create a `ProductionBatch` and one execution record per stage.
4. Create the first inbound `Shipment` from the source POI to the first processing POI.
5. The existing dispatch system assigns a vehicle and creates an `Assignment`.
6. When transport delivery completes, `TransportLifecycleService` publishes `ShipmentDeliveredEvent`.
7. The production execution service starts the corresponding `ProcessingStageExecution`.
8. The simulation loop advances processing progress.
9. When a stage completes, an outbound `Shipment` is created to the next processing POI.
10. The final stage completion marks the batch complete.

## API

### Manage chain definitions

Only static definitions are exposed here. Creating and executing plans is handled by the production-plan API below.

```http
POST   /api/v1/processing-chains
GET    /api/v1/processing-chains
GET    /api/v1/processing-chains/{id}
GET    /api/v1/processing-chains/{id}/stages
PATCH  /api/v1/processing-chains/{id}/status?status=ACTIVE
DELETE /api/v1/processing-chains/{id}

POST   /api/v1/processing-chains/{chainId}/stages
PUT    /api/v1/processing-chains/stages/{stageId}
DELETE /api/v1/processing-chains/stages/{stageId}
```

### Create a random plan

### Create a random plan

```http
POST /api/v1/production-plans
```

```json
{
  "chainId": 1,
  "minFinalWeight": 60,
  "maxFinalWeight": 100,
  "lotSize": 5,
  "randomSeed": 12345,
  "sourcePoiId": 10,
  "createdBy": "test"
}
```

`sourcePoiId` is optional. If omitted, the service resolves a source POI from the first stage input SKU through `Enrollment`.

### Query a plan

```http
GET /api/v1/production-plans/{planId}
```

### Release a plan

```http
POST /api/v1/production-plans/{planId}/release?actor=test
```

### Query a batch

```http
GET /api/v1/production-plans/batches/{batchId}
```

## Database

Run `create_production_domain_tables.sql` for environments that do not rely on Hibernate `ddl-auto=update`.

The legacy processing execution model has been removed from the codebase. `Shipment` and `ShipmentItem`
now represent transport only, while production execution is represented by `ProductionBatch` and
`ProcessingStageExecution`. Because `ddl-auto=update` does not drop obsolete columns, existing databases
should remove the old `shipment` / `shipment_item` processing columns in a separate migration after a backup.

## Y-shape DAG processing chains

A processing chain is now modeled as a directed acyclic graph inside one `ProcessingChain`:

- `ProcessingStage`: one operation and its output SKU / output ratio.
- `ProcessingStageInput`: one named input of a stage, such as `steel` or `wood`.
- `ProcessingStageEdge`: one upstream output to one named downstream input.
- `ProductionPlanFlow`: one planned material flow in a plan.
- `ProcessingExecutionFlow`: one runtime material flow, including transport and delivery state.

### Graph rules

1. The graph must be acyclic and have exactly one final stage.
2. `stageKey` is required and unique inside a graph chain.
3. Every stage input must have a non-empty `inputKey`, SKU, and positive `inputShare`.
4. The sum of `inputShare` values in one stage must equal `1`.
5. An upstream output SKU must match the target input SKU.
6. Each named input can have at most one upstream source.
7. External material flows have no upstream node and require a source POI.
8. A merge stage starts only after every inbound runtime flow is delivered.
9. One upstream output is not currently allowed to split to multiple downstream inputs.

### Demand calculation

For a merge stage:

```text
totalInput = requiredOutput / outputWeightRatio
inputWeight = totalInput * inputShare
```

Example:

```text
final demand          = 95
merge output ratio    = 0.95
merge total input     = 100
steel input share     = 0.80 -> 80
wood input share      = 0.20 -> 20
```

### Graph API

```http
POST /api/v1/processing-chains/graph
GET  /api/v1/processing-chains/{chainId}/graph
```

Example request:

```json
{
  "chainCode": "Y-CHAIN",
  "chainName": "Y型加工链",
  "stages": [
    {
      "stageOrder": 1,
      "stageKey": "steel",
      "stageName": "炼钢",
      "processingPoiId": 1,
      "outputGoodsSku": "STEEL",
      "outputWeightRatio": 1.0,
      "processingTimeMinutes": 60,
      "inputs": [{ "inputKey": "input", "sku": "ORE", "inputShare": 1.0 }]
    },
    {
      "stageOrder": 2,
      "stageKey": "wood",
      "stageName": "木材加工",
      "processingPoiId": 2,
      "outputGoodsSku": "WOOD",
      "outputWeightRatio": 1.0,
      "processingTimeMinutes": 60,
      "inputs": [{ "inputKey": "input", "sku": "TIMBER", "inputShare": 1.0 }]
    },
    {
      "stageOrder": 3,
      "stageKey": "merge",
      "stageName": "合并装配",
      "processingPoiId": 3,
      "outputGoodsSku": "FINAL",
      "outputWeightRatio": 0.95,
      "processingTimeMinutes": 90,
      "inputs": [
        { "inputKey": "steel", "sku": "STEEL", "inputShare": 0.8 },
        { "inputKey": "wood", "sku": "WOOD", "inputShare": 0.2 }
      ]
    }
  ],
  "edges": [
    { "fromStageKey": "steel", "toStageKey": "merge", "toInputKey": "steel" },
    { "fromStageKey": "wood", "toStageKey": "merge", "toInputKey": "wood" }
  ]
}
```

### Per-input source POIs

`CreateProductionPlanRequest.sourcePois` supports these keys, in this priority order:

```text
stageKey:inputKey
inputKey
sku
```

If no mapping is supplied, the request-level `sourcePoiId` is used. If that is also absent,
the service resolves a POI from `Enrollment` by SKU.

### Database migration

For an existing database, run:

```text
create_y_shape_dag_tables.sql
```

The consolidated installation script is also updated:

```text
create_production_domain_tables.sql
```

Both scripts create the stage-input, graph-edge, plan-flow, and execution-flow tables.
