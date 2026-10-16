package com.jobsearch.joblist.mcp;

import com.jobsearch.joblist.api.ClientAuthFilter;
import com.jobsearch.joblist.common.BadRequestException;
import com.jobsearch.joblist.common.NotFoundException;
import com.jobsearch.joblist.common.TooManyRequestsException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A small MCP server over Streamable HTTP: each POST is one JSON-RPC message answered with one JSON body, no
 * sessions and no server-initiated stream. Enough for an agent to list and call the tools.
 */
@RestController
class McpController {

	private static final Logger log = LoggerFactory.getLogger(McpController.class);
	private static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26");
	private static final int INVALID_REQUEST = -32600;
	private static final int METHOD_NOT_FOUND = -32601;
	private static final int INVALID_PARAMS = -32602;
	private static final int INTERNAL_ERROR = -32603;

	private final McpTools tools;
	private final ObjectMapper json;

	McpController(McpTools tools, ObjectMapper json) {
		this.tools = tools;
		this.json = json;
	}

	@PostMapping("/mcp")
	ResponseEntity<?> message(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @RequestBody JsonNode message) {
		JsonNode method = message.path("method");
		JsonNode id = message.path("id");
		if (!method.isString() && (message.has("result") || message.has("error"))) {
			return ResponseEntity.accepted().build();
		}
		if (!method.isString()) {
			return error(id, INVALID_REQUEST, "Not a JSON-RPC request");
		}
		if (id.isMissingNode()) {
			return ResponseEntity.accepted().build();
		}
		return switch (method.asString()) {
			case "initialize" -> result(id, initialize(message.path("params")));
			case "ping" -> result(id, Map.of());
			case "tools/list" -> result(id, Map.of("tools", tools.all().stream().map(this::describe).toList()));
			case "tools/call" -> call(client, id, message.path("params"));
			default -> error(id, METHOD_NOT_FOUND, "Unknown method: " + method.asString());
		};
	}

	@GetMapping("/mcp")
	ResponseEntity<Void> noStream() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
	}

	private Map<String, Object> initialize(JsonNode params) {
		String requested = params.path("protocolVersion").asString("");
		return Map.of(
				"protocolVersion", PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.getFirst(),
				"capabilities", Map.of("tools", Map.of()),
				"serverInfo", Map.of("name", "job-list", "version", "1"));
	}

	private Map<String, Object> describe(McpTool tool) {
		return Map.of("name", tool.name(), "description", tool.description(), "inputSchema", tool.inputSchema());
	}

	private ResponseEntity<?> call(String client, JsonNode id, JsonNode params) {
		String name = params.path("name").asString("");
		McpTool tool = tools.find(name).orElse(null);
		if (tool == null) {
			return error(id, INVALID_PARAMS, "Unknown tool: " + name);
		}
		Object value;
		try {
			value = tool.handler().apply(client, params.path("arguments"));
		}
		catch (BadRequestException | NotFoundException | TooManyRequestsException ex) {
			return result(id, content(ex.getMessage(), true));
		}
		catch (JacksonException ex) {
			return result(id, content("The arguments could not be read; check the names and types in the tool's inputSchema", true));
		}
		catch (RuntimeException ex) {
			log.error("Tool failed tool={}", tool.name(), ex);
			return error(id, INTERNAL_ERROR, "Internal error");
		}
		return result(id, content(json.writeValueAsString(value), false));
	}

	private static Map<String, Object> content(String text, boolean isError) {
		return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
	}

	private ResponseEntity<Map<String, Object>> result(JsonNode id, Object result) {
		return ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id, "result", result));
	}

	private ResponseEntity<Map<String, Object>> error(JsonNode id, int code, String message) {
		Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", id.isMissingNode() ? json.nullNode() : id,
				"error", Map.of("code", code, "message", message));
		return ResponseEntity.ok(body);
	}
}
