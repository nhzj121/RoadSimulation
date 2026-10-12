package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.util.*;

/** Pure display projection. Coordinates never infer a driving position or alter recorded progress. */
public final class SandboxMapProjection {
    public static final String POSITION_POLICY = "POI_LINK_OR_UNKNOWN_NO_INTERPOLATION_V1";
    private final ObjectMapper json;
    public SandboxMapProjection(ObjectMapper json) { this.json=json; }
    public ArrayNode pois(JsonNode database) {
        var result=json.createArrayNode();
        for(var row:rows(database,"poi").values()) {
            var item=pick(row,"id name poi_type longitude latitude");
            coordinate(row,"longitude","latitude"); result.add(item);
        }
        return result;
    }
    public ObjectNode project(JsonNode database) {
        var pois=rows(database,"poi"); var vehicles=rows(database,"vehicle");
        var assignments=rows(database,"assignment"); var legs=rows(database,"assignment_leg");
        var nodes=rows(database,"assignment_nodes"); var shipments=rows(database,"shipment");
        var goods=rows(database,"goods"); var cargo=rows(database,"shipment_item");
        var events=rows(database,"transport_random_event");
        var result=json.createObjectNode(); result.set("pois",pois(database));
        var vehicleData=result.putArray("vehicles"); var moving=new HashSet<Long>();
        for(var leg:legs.values()) {
            reference(leg,"vehicle_id",vehicles); reference(leg,"assignment_id",assignments);
            reference(leg,"from_poi_id",pois); reference(leg,"to_poi_id",pois);
            reference(leg,"from_node_id",nodes); reference(leg,"to_node_id",nodes);
            for(String side:List.of("from","to")) if(present(leg,side+"_node_id")) {
                var node=nodes.get(id(leg.get(side+"_node_id")));
                require(Objects.equals(leg.get("assignment_id"),node.get("assignment_id")));
                if(present(leg,side+"_poi_id")) require(Objects.equals(leg.get(side+"_poi_id"),node.get("poi_id")));
            }
            if("RUNNING".equals(leg.path("progress_status").asText()) && present(leg,"vehicle_id")) moving.add(id(leg.get("vehicle_id")));
        }
        for(var row:vehicles.values()) {
            reference(row,"current_poi_id",pois);
            var item=pick(row,"id license_plate vehicle_type current_status current_load current-volumn max_load_capacity cargo_volume current_poi_id replacement_reservation_event_id");
            String status=row.path("current_status").asText();
            boolean inTransit=Set.of("ORDER_DRIVING","TRANSPORT_DRIVING").contains(status)||moving.contains(id(row.get("id")));
            if(inTransit) { item.put("positionSource","IN_TRANSIT"); item.putNull("position"); }
            else if(Set.of("IDLE","LOADING","UNLOADING","WAITING","RESERVED_REPLACEMENT").contains(status)&&present(row,"current_poi_id")) { item.put("positionSource","POI_LINK"); item.set("position",coordinate(pois.get(id(row.get("current_poi_id"))),"longitude","latitude")); }
            else { item.put("positionSource","UNKNOWN"); item.putNull("position"); }
            vehicleData.add(item);
        }
        var assignmentData=result.putArray("assignments");
        for(var row:assignments.values()) {
            reference(row,"vehicle_id",vehicles); reference(row,"origin_poi_id",pois); reference(row,"dest_poi_id",pois);
            assignmentData.add(pick(row,"id vehicle_id origin_poi_id dest_poi_id status current_leg_index current_action_index is_processing_assignment processing_status processing_chain_id"));
        }
        var legData=result.putArray("legs");
        for(var row:legs.values()) {
            var item=pick(row,"id assignment_id vehicle_id sequence_index from_poi_id to_poi_id from_node_id to_node_id load_state progress_status distance_meters executed_distance_meters driving_seconds executed_driving_seconds current_load_tonnes started_sim_time completed_sim_time");
            item.set("from",endpoint(row,"from",pois)); item.set("to",endpoint(row,"to",pois));
            item.put("geometrySource","ENDPOINT_RELATION_NOT_ROAD_ROUTE"); legData.add(item);
        }
        var nodeData=result.putArray("nodes");
        for(var row:nodes.values()) {
            reference(row,"assignment_id",assignments); reference(row,"poi_id",pois); reference(row,"shipment_item_id",cargo);
            nodeData.add(pick(row,"id assignment_id poi_id shipment_item_id sequence_index action_type is_completed actual_arrival_time weight_delta volume_delta"));
        }
        var shipmentData=result.putArray("shipments");
        for(var row:shipments.values()) {
            reference(row,"origin_poi_id",pois); reference(row,"dest_poi_id",pois);
            shipmentData.add(pick(row,"id origin_poi_id dest_poi_id status processing_status processing_chain_id"));
        }
        var cargoData=result.putArray("cargo");
        for(var row:cargo.values()) {
            reference(row,"shipment_id",shipments); reference(row,"goods_id",goods);
            for(String field:List.of("assignment_id","inbound_assignment_id","outbound_assignment_id")) reference(row,field,assignments);
            reference(row,"processing_poi_id",pois);
            cargoData.add(pick(row,"id shipment_id goods_id assignment_id inbound_assignment_id outbound_assignment_id name qty weight volume status processing_status processing_poi_id stage_id stage_name progress_percent"));
        }
        var eventData=result.putArray("events");
        for(var row:events.values()) {
            reference(row,"vehicle_id",vehicles); reference(row,"replacement_vehicle_id",vehicles); reference(row,"assignment_id",assignments);
            eventData.add(pick(row,"id vehicle_id assignment_id event_type status trigger_source trigger_loop_index start_time planned_end_time resolved_time description breakdown_level breakdown_phase recovery_outcome replacement_vehicle_id replacement_outcome speed_factor delay_seconds"));
        }
        result.set("drivingProgress",projectWithoutId(database,"driving_progress","phase_key vehicle_id assignment_id leg_index driving_status phase_start last_settled_time initial_work_seconds remaining_work_seconds affected_seconds lost_work_seconds model_completed_time observed_completed_time",vehicles,assignments));
        var weather=result.putArray("weather");
        for(var row:textRows(database,"weather_run","id").values()) weather.add(pick(row,"id started_at ended_at frozen_scenario_json frozen_event_configuration_json"));
        return result;
    }
    private ArrayNode projectWithoutId(JsonNode database,String table,String fields,Map<Long,JsonNode> vehicles,Map<Long,JsonNode> assignments) {
        var result=json.createArrayNode();
        for(var row:textRows(database,table,"phase_key").values()) { reference(row,"vehicle_id",vehicles); reference(row,"assignment_id",assignments); result.add(pick(row,fields)); }
        return result;
    }
    private Map<String,JsonNode> textRows(JsonNode database,String table,String key) {
        var data=database.path(table);require(data.isArray());var result=new TreeMap<String,JsonNode>();
        for(var row:data){require(row.isObject()&&row.path(key).isTextual()&&!row.path(key).asText().isBlank());require(result.put(row.path(key).asText(),row)==null);}return result;
    }
    private JsonNode endpoint(JsonNode row,String side,Map<Long,JsonNode> pois) {
        if(present(row,side+"_longitude")||present(row,side+"_latitude")) return coordinate(row,side+"_longitude",side+"_latitude");
        return present(row,side+"_poi_id")?coordinate(pois.get(id(row.get(side+"_poi_id"))),"longitude","latitude"):NullNode.instance;
    }
    private ObjectNode coordinate(JsonNode row,String longitude,String latitude) {
        require(row!=null && row.path(longitude).isNumber() && row.path(latitude).isNumber());
        double x=row.path(longitude).asDouble(),y=row.path(latitude).asDouble();
        require(Double.isFinite(x)&&Double.isFinite(y)&&x>=-180&&x<=180&&y>=-90&&y<=90);
        var point=json.createObjectNode();point.set("longitude",row.get(longitude));point.set("latitude",row.get(latitude));return point;
    }
    private Map<Long,JsonNode> rows(JsonNode database,String table) {
        var data=database.path(table); require(data.isArray()); var result=new TreeMap<Long,JsonNode>();
        for(var row:data) { require(row.isObject());require(result.put(id(row.get("id")),row)==null); } return result;
    }
    private long id(JsonNode value) { require(value!=null&&value.isIntegralNumber()&&value.canConvertToLong()); long n=value.asLong(); require(n>0&&n<=9_007_199_254_740_991L);return n; }
    private void reference(JsonNode row,String field,Map<Long,JsonNode> target) { if(present(row,field)) require(target.containsKey(id(row.get(field)))); }
    private boolean present(JsonNode row,String field) { return row.hasNonNull(field); }
    private ObjectNode pick(JsonNode row,String fields) {
        var result=json.createObjectNode();for(String field:fields.split(" ")) {StringBuilder key=new StringBuilder();boolean upper=false;for(char c:field.toCharArray()){if(c=='_'||c=='-'){upper=true;}else{key.append(upper?Character.toUpperCase(c):c);upper=false;}} result.set(key.toString(),row.has(field)?row.get(field).deepCopy():NullNode.instance);}return result;
    }
    private static void require(boolean condition) { if(!condition) throw new SandboxWorkspaceException("EXECUTION_RECORD_CORRUPT","Invalid map fact or reference"); }
}
