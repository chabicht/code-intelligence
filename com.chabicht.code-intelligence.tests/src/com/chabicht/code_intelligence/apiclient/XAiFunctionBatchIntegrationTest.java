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

public class XAiFunctionBatchIntegrationTest {

	private static final String USER_PROMPT = "Read A.java and B.java before answering.";

	private static final String FIRST_TURN_RESPONSE = """
			data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"find_files","arguments":"{\\"query\\":\\"*.java\\"}"}},{"index":1,"id":"call-2","type":"function","function":{"name":"read_file_content","arguments":"{\\"path\\":\\"/project/A.java\\"}"}}]}}]}

			data: {"choices":[{"finish_reason":"tool_calls"}]}

			data: [DONE]
			""";

	private static final String SECOND_TURN_RESPONSE = """
			data: {"choices":[{"delta":{"content":"I checked both files."}}]}

			data: {"choices":[{"finish_reason":"stop"}]}

			data: [DONE]
			""";

	@Test
	void performChatCapturesAllToolCallsFromOneXAiTurn() throws Exception {
		try (RecordingXAiServer server = new RecordingXAiServer(FIRST_TURN_RESPONSE)) {
			XAiApiClient client = new XAiApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			AtomicInteger functionCallNotifications = new AtomicInteger();
			chat.addListener(createListener(responseFinished, functionCallNotifications));

			client.performChat("grok-4", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			ChatMessage assistantMessage = chat.getMessages().get(chat.getMessages().size() - 1);
			assertTrue(assistantMessage.getFunctionCallBatch().isPresent(),
					"Multiple X.ai tool calls should be captured as one FunctionCallBatch");

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
	void performChatReplaysBatchToolCallsAndToolResultsForContinuation() throws Exception {
		try (RecordingXAiServer server = new RecordingXAiServer(SECOND_TURN_RESPONSE)) {
			XAiApiClient client = new XAiApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
			assistantMessage.setFunctionCallBatch(createBatchWithResults());
			chat.addMessage(assistantMessage, false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished, new AtomicInteger()));

			client.performChat("grok-4", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			JsonObject request = server.getRequests().get(0);
			JsonArray messages = request.getAsJsonArray("messages");
			assertEquals(4, messages.size(),
					"Continuation should replay one assistant tool-call turn plus one tool result message per call");

			JsonObject assistantJson = messages.get(1).getAsJsonObject();
			assertEquals("assistant", assistantJson.get("role").getAsString());
			JsonArray toolCalls = assistantJson.getAsJsonArray("tool_calls");
			assertEquals(2, toolCalls.size());
			assertEquals("call-1", toolCalls.get(0).getAsJsonObject().get("id").getAsString());
			assertEquals("call-2", toolCalls.get(1).getAsJsonObject().get("id").getAsString());

			JsonObject firstToolMessage = messages.get(2).getAsJsonObject();
			assertEquals("tool", firstToolMessage.get("role").getAsString());
			assertEquals("call-1", firstToolMessage.get("tool_call_id").getAsString());

			JsonObject secondToolMessage = messages.get(3).getAsJsonObject();
			assertEquals("tool", secondToolMessage.get("role").getAsString());
			assertEquals("call-2", secondToolMessage.get("tool_call_id").getAsString());
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

	private void awaitChatFinished(XAiApiClient client, CountDownLatch responseFinished) throws InterruptedException {
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

		FunctionCallBatch batch = new FunctionCallBatch("batch-xai");
		batch.setItems(List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		return batch;
	}

	private AiApiConnection createConnection(RecordingXAiServer server) {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.XAI);
		connection.setApiKey("test-key");
		connection.setBaseUri(server.getBaseUri());
		connection.setEnabled(true);
		return connection;
	}

	private static final class RecordingXAiServer implements AutoCloseable {
		private final HttpServer server;
		private final Queue<String> responses = new ArrayDeque<>();
		private final List<JsonObject> requests = new ArrayList<>();

		private RecordingXAiServer(String... responseBodies) throws IOException {
			server = HttpServer.create(new InetSocketAddress(0), 0);
			for (String responseBody : responseBodies) {
				responses.add(responseBody.strip() + "\n");
			}
			server.createContext("/chat/completions", this::handleChat);
			server.start();
		}

		private void handleChat(HttpExchange exchange) throws IOException {
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
