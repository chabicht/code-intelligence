package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

public class OllamaFunctionBatchIntegrationTest {

	private static final String USER_PROMPT = """
			You are refactoring a Java client. Before answering, call read_file_content twice in the same response:
			once for com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java
			and once for com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java.
			Do not answer yet. Batch both tool calls in one turn.
			""";

	private static final String FIRST_TURN_RESPONSE = """
			{"model":"qwen3.5:35b","created_at":"2026-03-09T20:20:30.22491889Z","message":{"role":"assistant","content":"","thinking":"The user wants me to read two Java files before answering any questions about refactoring a Java client. They want me to call the read_file_content function twice in the same response, reading:\\n\\n1. com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java\\n2. com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java\\n\\nI need to batch both tool calls in one turn and not answer yet. Let me make both function calls now.","tool_calls":[{"id":"call_trmym6vj","function":{"index":0,"name":"read_file_content","arguments":{"file_name":"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java"}}},{"id":"call_24957ol8","function":{"index":1,"name":"read_file_content","arguments":{"file_name":"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java"}}}]},"done":true,"done_reason":"stop","total_duration":17651054502,"load_duration":8821849047,"prompt_eval_count":557,"prompt_eval_duration":434295382,"eval_count":213,"eval_duration":7901656841}
			""";

	private static final String SECOND_TURN_RESPONSE = """
			{"model":"qwen3.5:35b","created_at":"2026-03-09T20:22:17.129514047Z","message":{"role":"assistant","content":"I have successfully read both files: `OllamaApiClient.java` and `OpenAiApiClient.java`. Both returned stub content for the purposes of this interaction.\\n\\nNow that I have the file contents, please let me know what specific refactoring changes you would like to make to the Java client!"},"done":true,"done_reason":"stop","total_duration":2820830127,"load_duration":159807458,"prompt_eval_count":779,"prompt_eval_duration":235915117,"eval_count":63,"eval_duration":2295779841}
			""";

	@Test
	void performChatCapturesAllToolCallsFromOneOllamaTurn() throws Exception {
		try (RecordingOllamaServer server = new RecordingOllamaServer(FIRST_TURN_RESPONSE)) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			AtomicInteger functionCallNotifications = new AtomicInteger();
			chat.addListener(createListener(responseFinished, functionCallNotifications));

			client.performChat("qwen3.5:35b", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			ChatMessage assistantMessage = chat.getMessages().get(chat.getMessages().size() - 1);
			assertTrue(assistantMessage.getFunctionCallBatch().isPresent(),
					"Multiple Ollama tool calls should be captured as one FunctionCallBatch");

			FunctionCallBatch batch = assistantMessage.getFunctionCallBatch().get();
			assertEquals(2, batch.getItems().size());
			assertEquals("call_trmym6vj", batch.getItems().get(0).getCall().getId());
			assertEquals("read_file_content", batch.getItems().get(0).getCall().getFunctionName());
			assertEquals(
					"{\"file_name\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java\"}",
					batch.getItems().get(0).getCall().getArgsJson());
			assertEquals("call_24957ol8", batch.getItems().get(1).getCall().getId());
			assertEquals("read_file_content", batch.getItems().get(1).getCall().getFunctionName());
			assertEquals(
					"{\"file_name\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java\"}",
					batch.getItems().get(1).getCall().getArgsJson());
			assertEquals(1, functionCallNotifications.get(), "Function-call notification should fire once per turn");
			assertTrue(assistantMessage.getFunctionCall().isPresent(), "Legacy first-call shim should remain populated");
			assertEquals("call_trmym6vj", assistantMessage.getFunctionCall().get().getId());
		}
	}

	@Test
	void performChatReplaysBatchToolCallsAndToolResultsForContinuation() throws Exception {
		try (RecordingOllamaServer server = new RecordingOllamaServer(SECOND_TURN_RESPONSE)) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = new ChatConversation();
			chat.addMessage(new ChatMessage(Role.USER, USER_PROMPT), false);

			ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
			assistantMessage.setFunctionCallBatch(createBatchWithResults());
			chat.addMessage(assistantMessage, false);

			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished, new AtomicInteger()));

			client.performChat("qwen3.5:35b", chat, 1024);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getRequests().size());
			JsonArray messages = server.getRequests().get(0).getAsJsonArray("messages");
			assertEquals(4, messages.size(),
					"Continuation should replay one assistant tool-call turn plus one tool result message per call");

			JsonObject assistantJson = messages.get(1).getAsJsonObject();
			assertEquals("assistant", assistantJson.get("role").getAsString());
			JsonArray toolCalls = assistantJson.getAsJsonArray("tool_calls");
			assertNotNull(toolCalls, "Assistant continuation turn should include tool_calls");
			assertEquals(2, toolCalls.size());
			assertEquals("read_file_content",
					toolCalls.get(0).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
			assertEquals("read_file_content",
					toolCalls.get(1).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());

			JsonObject firstToolMessage = messages.get(2).getAsJsonObject();
			assertEquals("tool", firstToolMessage.get("role").getAsString());
			assertEquals("read_file_content", firstToolMessage.get("tool_name").getAsString());
			assertFalse(firstToolMessage.has("name"), "Ollama chat history uses tool_name on tool messages");

			JsonObject secondToolMessage = messages.get(3).getAsJsonObject();
			assertEquals("tool", secondToolMessage.get("role").getAsString());
			assertEquals("read_file_content", secondToolMessage.get("tool_name").getAsString());
			assertFalse(secondToolMessage.has("name"), "Ollama chat history uses tool_name on tool messages");
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

	private void awaitChatFinished(OllamaApiClient client, CountDownLatch responseFinished) throws InterruptedException {
		assertTrue(responseFinished.await(10, TimeUnit.SECONDS), "Timed out waiting for chat response to finish");
		for (int i = 0; i < 40 && client.isChatPending(); i++) {
			Thread.sleep(50);
		}
		assertFalse(client.isChatPending(), "Client request should be complete after chat response finished");
	}

	private FunctionCallBatch createBatchWithResults() {
		FunctionCall callOne = new FunctionCall("call_trmym6vj", "read_file_content",
				"{\"file_name\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java\"}");
		FunctionResult resultOne = new FunctionResult("call_trmym6vj", "read_file_content");
		resultOne.setResultJson(
				"{\"status\":\"Success\",\"message\":\"stub\",\"file_path\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OllamaApiClient.java\",\"content\":\"stub\",\"actual_start_line\":1,\"actual_end_line\":1}");

		FunctionCall callTwo = new FunctionCall("call_24957ol8", "read_file_content",
				"{\"file_name\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java\"}");
		FunctionResult resultTwo = new FunctionResult("call_24957ol8", "read_file_content");
		resultTwo.setResultJson(
				"{\"status\":\"Success\",\"message\":\"stub\",\"file_path\":\"com.chabicht.code-intelligence/src/com/chabicht/code_intelligence/apiclient/OpenAiApiClient.java\",\"content\":\"stub\",\"actual_start_line\":1,\"actual_end_line\":1}");

		FunctionCallBatch batch = new FunctionCallBatch("batch-ollama");
		batch.setItems(
				List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		return batch;
	}

	private AiApiConnection createConnection(RecordingOllamaServer server) {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.OLLAMA);
		connection.setBaseUri(server.getBaseUri());
		connection.setEnabled(true);
		return connection;
	}

	private static final class RecordingOllamaServer implements AutoCloseable {
		private final HttpServer server;
		private final Queue<String> responses = new ArrayDeque<>();
		private final List<JsonObject> requests = new ArrayList<>();

		private RecordingOllamaServer(String... responseBodies) throws IOException {
			server = HttpServer.create(new InetSocketAddress(0), 0);
			for (String responseBody : responseBodies) {
				responses.add(responseBody.strip() + "\n");
			}
			server.createContext("/api/chat", this::handleChat);
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
			exchange.getResponseHeaders().add("Content-Type", "application/json");
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
