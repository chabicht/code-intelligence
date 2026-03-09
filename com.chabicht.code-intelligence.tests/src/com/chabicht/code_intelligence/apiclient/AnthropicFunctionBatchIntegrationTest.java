package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class AnthropicFunctionBatchIntegrationTest {

	private static final String USER_PROMPT = "Read A.java and B.java before answering.";

	private static final String FIRST_TURN_RESPONSE = """
			event: message_start
			data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-sonnet-4-20250514","stop_reason":null,"stop_sequence":null}}

			event: content_block_start
			data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"call-1","name":"find_files","input":{}}}

			event: content_block_delta
			data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"query\\":\\"*.java\\"}"}}

			event: content_block_stop
			data: {"type":"content_block_stop","index":0}

			event: content_block_start
			data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call-2","name":"read_file_content","input":{}}}

			event: content_block_delta
			data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"path\\":\\"/project/A.java\\"}"}}

			event: content_block_stop
			data: {"type":"content_block_stop","index":1}

			event: message_delta
			data: {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":89}}

			event: message_stop
			data: {"type":"message_stop"}
			""";

	private static final String SECOND_TURN_RESPONSE = """
			event: message_start
			data: {"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","content":[],"model":"claude-sonnet-4-20250514","stop_reason":null,"stop_sequence":null}}

			event: content_block_start
			data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

			event: content_block_delta
			data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"I checked both files."}}

			event: content_block_stop
			data: {"type":"content_block_stop","index":0}

			event: message_delta
			data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null}}

			event: message_stop
			data: {"type":"message_stop"}
			""";

	@Test
	void performChatCapturesAllToolUsesFromOneAnthropicTurn() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(FIRST_TURN_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			AtomicInteger functionCallNotifications = new AtomicInteger();
			chat.addListener(createListener(responseFinished, functionCallNotifications));

			client.performChat("claude-sonnet-4-20250514", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			ChatMessage assistantMessage = chat.getMessages().get(chat.getMessages().size() - 1);
			assertTrue(assistantMessage.getFunctionCallBatch().isPresent(),
					"Multiple Anthropic tool uses should be captured as one FunctionCallBatch");

			FunctionCallBatch batch = assistantMessage.getFunctionCallBatch().get();
			assertEquals(2, batch.getItems().size());
			assertEquals("call-1", batch.getItems().get(0).getCall().getId());
			assertEquals("find_files", batch.getItems().get(0).getCall().getFunctionName());
			assertEquals("{\"query\":\"*.java\"}", batch.getItems().get(0).getCall().getArgsJson());
			assertEquals("call-2", batch.getItems().get(1).getCall().getId());
			assertEquals("read_file_content", batch.getItems().get(1).getCall().getFunctionName());
			assertEquals("{\"path\":\"/project/A.java\"}", batch.getItems().get(1).getCall().getArgsJson());
			assertEquals(1, functionCallNotifications.get(), "Function-call notification should fire once per turn");
			assertTrue(assistantMessage.getFunctionCall().isPresent(), "Legacy first-call shim should remain populated");
			assertEquals("call-1", assistantMessage.getFunctionCall().get().getId());
		}
	}

	@Test
	void performChatReplaysBatchToolUsesAndGroupedToolResultsForContinuation() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(SECOND_TURN_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
			assistantMessage.setFunctionCallBatch(createBatchWithResults());
			chat.addMessage(assistantMessage, false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished, new AtomicInteger()));

			client.performChat("claude-sonnet-4-20250514", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			JsonArray messages = server.getRequests().get(0).getAsJsonArray("messages");
			assertEquals(3, messages.size(), "Continuation should replay user, assistant tool_use turn, and one grouped tool_result user turn");

			JsonObject assistantJson = messages.get(1).getAsJsonObject();
			assertEquals("assistant", assistantJson.get("role").getAsString());
			JsonArray assistantContent = assistantJson.getAsJsonArray("content");
			assertEquals(2, assistantContent.size());
			assertEquals("tool_use", assistantContent.get(0).getAsJsonObject().get("type").getAsString());
			assertEquals("tool_use", assistantContent.get(1).getAsJsonObject().get("type").getAsString());
			assertEquals("call-1", assistantContent.get(0).getAsJsonObject().get("id").getAsString());
			assertEquals("call-2", assistantContent.get(1).getAsJsonObject().get("id").getAsString());

			JsonObject toolResultTurn = messages.get(2).getAsJsonObject();
			assertEquals("user", toolResultTurn.get("role").getAsString());
			JsonArray toolResults = toolResultTurn.getAsJsonArray("content");
			assertEquals(2, toolResults.size());
			assertEquals("tool_result", toolResults.get(0).getAsJsonObject().get("type").getAsString());
			assertEquals("tool_result", toolResults.get(1).getAsJsonObject().get("type").getAsString());
			assertEquals("call-1", toolResults.get(0).getAsJsonObject().get("tool_use_id").getAsString());
			assertEquals("call-2", toolResults.get(1).getAsJsonObject().get("tool_use_id").getAsString());
		}
	}

	private ChatListener createListener(CountDownLatch responseFinished, AtomicInteger functionCallNotifications) {
		return new ChatListener() {
			@Override
			public void onMessageAdded(ChatMessage message, boolean updating) {
			}

			@Override
			public void onMessageUpdated(ChatMessage message) {
			}

			@Override
			public void onFunctionCall(ChatMessage message) {
				functionCallNotifications.incrementAndGet();
			}

			@Override
			public void onChatResponseFinished(ChatMessage message) {
				responseFinished.countDown();
			}
		};
	}

	private void awaitChatFinished(AnthropicApiClient client, CountDownLatch responseFinished)
			throws InterruptedException {
		assertTrue(responseFinished.await(10, TimeUnit.SECONDS), "Timed out waiting for chat response to finish");
		for (int i = 0; i < 40 && client.isChatPending(); i++) {
			Thread.sleep(50);
		}
		assertTrue(!client.isChatPending(), "Client request should be complete after chat response finished");
	}

	private FunctionCallBatch createBatchWithResults() {
		FunctionCall callOne = new FunctionCall("call-1", "find_files", "{\"query\":\"*.java\"}");
		FunctionResult resultOne = new FunctionResult("call-1", "find_files");
		resultOne.setResultJson("{\"status\":\"ok\",\"items\":[\"A.java\"]}");

		FunctionCall callTwo = new FunctionCall("call-2", "read_file_content", "{\"path\":\"/project/A.java\"}");
		FunctionResult resultTwo = new FunctionResult("call-2", "read_file_content");
		resultTwo.setResultJson("{\"status\":\"ok\",\"content\":\"class A {}\"}");

		FunctionCallBatch batch = new FunctionCallBatch("batch-anthropic");
		batch.setItems(List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		return batch;
	}

	private AiApiConnection createConnection(RecordingAnthropicServer server) {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.ANTHROPIC);
		connection.setApiKey("test-key");
		connection.setBaseUri(server.getBaseUri());
		connection.setEnabled(true);
		return connection;
	}

	private static final class RecordingAnthropicServer implements AutoCloseable {
		private final HttpServer server;
		private final Queue<String> responses = new ArrayDeque<>();
		private final List<JsonObject> requests = new ArrayList<>();

		private RecordingAnthropicServer(String... responseBodies) throws IOException {
			server = HttpServer.create(new InetSocketAddress(0), 0);
			for (String responseBody : responseBodies) {
				responses.add(responseBody.strip() + "\n");
			}
			server.createContext("/messages", this::handleMessages);
			server.start();
		}

		private void handleMessages(HttpExchange exchange) throws IOException {
			byte[] requestBytes = exchange.getRequestBody().readAllBytes();
			requests.add(JsonParser.parseString(new String(requestBytes, StandardCharsets.UTF_8)).getAsJsonObject());

			String responseBody = responses.poll();
			if (responseBody == null) {
				exchange.sendResponseHeaders(500, -1);
				exchange.close();
				return;
			}

			byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
			exchange.sendResponseHeaders(200, responseBytes.length);
			try (OutputStream responseStream = exchange.getResponseBody()) {
				responseStream.write(responseBytes);
			}
		}

		private String getBaseUri() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		private List<JsonObject> getRequests() {
			return requests;
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
