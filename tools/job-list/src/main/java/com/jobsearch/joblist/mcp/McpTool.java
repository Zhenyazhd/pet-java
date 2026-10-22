package com.jobsearch.joblist.mcp;

import java.util.function.BiFunction;
import tools.jackson.databind.JsonNode;

/** One tool offered to the agent: its description, the JSON schema of its arguments and what it does. */
record McpTool(String name, String description, JsonNode inputSchema, BiFunction<String, JsonNode, Object> handler) {
}
