package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Spools only validated evaluation data; no live tables, full fact export, or mutation. */
public final class SandboxResultExporter {
    private final SandboxResultReader reader;
    public SandboxResultExporter(SandboxResultReader reader) { this.reader=reader; }
    public record PreparedExport(Path path,String executionId,String format) implements AutoCloseable {
        public long size() throws IOException { return Files.size(path); }
        public void transferTo(OutputStream output) throws IOException { Files.copy(path,output); }
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
    public PreparedExport prepare(String id,String format) {
        if(!Set.of("json","csv").contains(format)) throw new SandboxWorkspaceException("INVALID_EXPORT_FORMAT","Only json/csv supported");
        Path file;
        try { file=Files.createTempFile("roadsimulation-sandbox-result-","."+format); }
        catch(IOException failure) { throw new SandboxWorkspaceException("RESULT_EXPORT_FAILED","Cannot spool export",failure); }
        try {
            reader.snapshot(c->{
                var ctx=reader.context(c,id); SandboxResultReader.requireClosed(ctx);
                try(var q=c.prepareStatement("SELECT COUNT(*) FROM sandbox_execution_tick WHERE execution_id=?")) {
                    q.setString(1,id); try(var r=q.executeQuery()) { SandboxResultReader.require(r.next() && r.getLong(1)==ctx.recorded(),"EXECUTION_RECORD_CORRUPT"); }
                }
                var summaryRow=new LinkedHashMap<>(ctx.row()); JsonNode last=null;
                if(ctx.recorded()>0) {
                    try(var q=c.prepareStatement("SELECT "+SandboxResultReader.EVALUATION_COLUMNS+" FROM sandbox_execution_tick WHERE execution_id=? AND loop_index=?")) {
                        q.setString(1,id); q.setInt(2,ctx.recorded()-1); try(var r=q.executeQuery()) {
                            SandboxResultReader.require(r.next(),"EXECUTION_RECORD_CORRUPT");
                            last=(JsonNode)reader.evaluationItem(ctx,r,ctx.recorded()-1).get("evaluation");
                            summaryRow.put("tick_sha256",r.getString("tick_sha256"));
                        }
                    }
                }
                try {
                if(format.equals("json")) {
                    try(var g=reader.mapper().getFactory().createGenerator(Files.newOutputStream(file))) {
                        g.setCodec(reader.mapper()); g.writeStartObject(); g.writeStringField("artifactVersion","sandbox-result-export/v1");
                        g.writeStringField("executionId",id); g.writeObjectField("summary",SandboxPageViews.summary(summaryRow,last));
                        g.writeObjectField("configuration",ctx.configuration()); g.writeStringField("integrityScope","MANIFEST_AND_EVALUATION_PAYLOADS");
                        g.writeStringField("ledgerIntegrity","NOT_CHECKED"); g.writeArrayFieldStart("evaluations");
                        walk(c,ctx,item->g.writeObject(item)); g.writeEndArray(); g.writeEndObject();
                    }
                } else {
                    try(var w=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
                        w.write('\uFEFF'); w.write("executionId,contractVersion,loopIndex,loopNumber,simTime,snapshotStatus,metricId,displayName,category,unit,value,status,reason,manifestSha256,integrityScope,ledgerIntegrity\r\n");
                        walk(c,ctx,item->{
                            JsonNode e=(JsonNode)item.get("evaluation");
                            var names=new ArrayList<String>(); e.path("metrics").fieldNames().forEachRemaining(names::add); Collections.sort(names);
                            for(String name:names) {
                                var m=e.path("metrics").path(name);
                                csvRow(w,id,e.path("contractVersion").asText(),item.get("loopIndex"),((Number)item.get("loopIndex")).intValue()+1,
                                        e.path("simTime").asText(),e.path("snapshotStatus").asText(),name,m.path("displayName").asText(),
                                        m.path("category").asText(),m.path("unit").asText(),"AVAILABLE".equals(m.path("status").asText())?m.path("value").numberValue():null,
                                        m.path("status").asText(),m.path("reason").isNull()?null:m.path("reason").asText(),ctx.configuration().manifestSha256(),"MANIFEST_AND_EVALUATION_PAYLOADS","NOT_CHECKED");
                            }
                        });
                    }
                }
                } catch(IOException failure) { throw new SandboxWorkspaceException("RESULT_EXPORT_FAILED","Cannot spool export",failure); }
                return null;
            });
            return new PreparedExport(file,id,format);
        } catch(RuntimeException failure) {
            try { Files.deleteIfExists(file); } catch(IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @FunctionalInterface private interface ItemWriter { void write(Map<String,Object> item) throws Exception; }
    private void walk(Connection c,SandboxResultReader.Context ctx,ItemWriter writer) throws Exception {
        for(int index=0;index<ctx.recorded();) {
            int expected=Math.min(100,ctx.recorded()-index),count=0;
            try(var q=c.prepareStatement("SELECT "+SandboxResultReader.EVALUATION_COLUMNS+" FROM sandbox_execution_tick WHERE execution_id=? AND loop_index>=? ORDER BY loop_index LIMIT 100")) {
                q.setString(1,ctx.id()); q.setInt(2,index); try(var r=q.executeQuery()) {
                    while(r.next()) { SandboxResultReader.require(count<expected,"EXECUTION_RECORD_CORRUPT"); writer.write(reader.evaluationItem(ctx,r,index+count)); count++; }
                }
            }
            SandboxResultReader.require(count==expected,"EXECUTION_RECORD_CORRUPT"); index+=count;
        }
    }
    static void csvRow(Writer writer,Object... values) throws IOException {
        for(int i=0;i<values.length;i++) {
            if(i>0) writer.write(','); Object value=values[i]; String text=value==null?"":value.toString();
            String leading=text.stripLeading();
            if(value instanceof String && !leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0) text="'"+text;
            writer.write('"'); writer.write(text.replace("\"","\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }
}
