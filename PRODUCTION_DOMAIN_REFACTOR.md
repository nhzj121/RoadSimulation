# Demand-driven production domain

This branch introduces a production domain that is decoupled from the legacy processing shipment model.

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

The legacy `ProcessingChainServiceV2` remains active for existing data. New demand-driven processing should use the production domain above.
