package org.example.roadsimulation.sandbox.execution;

import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Column-complete fact contract derived only from the versioned, trusted business DDL. */
public final class SandboxBusinessFactSchema {
    private static final Pattern TABLE = Pattern.compile("CREATE TABLE `([a-z_]+)` \\((.*?)\\) ENGINE", Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("^`?([a-z_][a-z0-9_-]*)`?\\s+.*", Pattern.DOTALL);
    private static final Set<String> CONSTRAINTS = Set.of("PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "FOREIGN", "CHECK", "INDEX");
    private final Map<String, List<String>> tables;

    public SandboxBusinessFactSchema() {
        try (var input = new ClassPathResource("sandbox/schema/sandbox-schema-v1.sql").getInputStream()) {
            tables = parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new SandboxWorkspaceException("FACT_SCHEMA_UNREADABLE", "Cannot load the business fact contract", failure);
        }
    }

    public Map<String, List<String>> tables() { return tables; }

    static Map<String, List<String>> parse(String ddl) {
        Map<String, List<String>> result = new TreeMap<>();
        var matcher = TABLE.matcher(ddl);
        while (matcher.find()) {
            List<String> columns = new ArrayList<>();
            for (String definition : splitColumns(matcher.group(2))) {
                String text = definition.trim();
                String first = text.split("\\s+", 2)[0];
                if (CONSTRAINTS.contains(first.toUpperCase(Locale.ROOT))) continue;
                var column = COLUMN.matcher(text);
                if (!column.matches()) throw new IllegalArgumentException("Unrecognized DDL column: " + text);
                columns.add(column.group(1));
            }
            if (columns.isEmpty() || result.put(matcher.group(1), List.copyOf(columns)) != null) {
                throw new IllegalArgumentException("Empty or duplicate business table");
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Business DDL contains no tables");
        return Collections.unmodifiableMap(result);
    }

    private static List<String> splitColumns(String text) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0; char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quote != 0) {
                if (ch == quote && (index == 0 || text.charAt(index - 1) != '\\')) quote = 0;
            } else if (ch == '\'' || ch == '"' || ch == '`') quote = ch;
            else if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == ',' && depth == 0) { result.add(text.substring(start, index)); start = index + 1; }
        }
        result.add(text.substring(start));
        return result;
    }
}
