package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_EFFORT;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningEffort;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.PromptType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class AnthropicApiClientReasoningTest {

	private static final String MINIMAL_RESPONSE = """
			event: message_start
			data: {"type":"message_start","message":{"id":"m","type":"message","role":"assistant","content":[],"model":"m","stop_reason":null,"stop_sequence":null}}

			event: message_stop
			data: {"type":"message_stop"}
			""";

	@Test
	void opus47WithHighEffortEmitsAdaptiveThinkingAndOutputConfig() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(MINIMAL_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server));
			ChatConversation chat = createChat();
			chat.getOptions().put(REASONING_EFFORT, ReasoningEffort.HIGH);

			runAndAwait(client, "claude-opus-4-7", chat);

			JsonObject req = server.getRequests().get(0);
			assertTrue(req.has("thinking"), "thinking block should be present");
			JsonObject thinking = req.getAsJsonObject("thinking");
			assertEquals("adaptive", thinking.get("type").getAsString());
			assertEquals("summarized", thinking.get("display").getAsString(),
					"Opus 4.7 should opt in to summarized display");
			assertTrue(req.has("output_config"), "output_config should be present");
			assertEquals("high", req.getAsJsonObject("output_config").get("effort").getAsString());
			assertFalse(req.has("temperature"), "temperature must be stripped for Opus 4.7");
			assertFalse(req.has("top_p"), "top_p must be stripped for Opus 4.7");
			assertFalse(req.has("top_k"), "top_k must be stripped for Opus 4.7");
		}
	}

	@Test
	void opus47WithDefaultEffortOmitsThinkingAndOutputConfig() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(MINIMAL_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server));
			ChatConversation chat = createChat();
			chat.getOptions().put(REASONING_EFFORT, ReasoningEffort.DEFAULT);

			runAndAwait(client, "claude-opus-4-7", chat);

			JsonObject req = server.getRequests().get(0);
			assertFalse(req.has("thinking"), "no thinking when effort is DEFAULT");
			assertFalse(req.has("output_config"), "no output_config when effort is DEFAULT");
			assertFalse(req.has("temperature"), "temperature must be stripped for Opus 4.7");
			assertFalse(req.has("top_p"), "top_p must be stripped for Opus 4.7");
			assertFalse(req.has("top_k"), "top_k must be stripped for Opus 4.7");
		}
	}

	@Test
	void opus47StripsPresetSamplingParams() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(MINIMAL_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server)) {
				@Override
				protected JsonObject createFromPresets(PromptType type) {
					JsonObject base = super.createFromPresets(type);
					base.addProperty("temperature", 1.0);
					base.addProperty("top_p", 1.0);
					base.addProperty("top_k", 50);
					return base;
				}
			};
			ChatConversation chat = createChat();
			chat.getOptions().put(REASONING_EFFORT, ReasoningEffort.DEFAULT);

			runAndAwait(client, "claude-opus-4-7", chat);

			JsonObject req = server.getRequests().get(0);
			assertFalse(req.has("temperature"), "temperature must be stripped even when set in preset");
			assertFalse(req.has("top_p"), "top_p must be stripped even when set in preset");
			assertFalse(req.has("top_k"), "top_k must be stripped even when set in preset");
		}
	}

	@Test
	void opus46WithMediumEffortEmitsAdaptiveThinkingWithoutDisplayField() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(MINIMAL_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server)) {
				@Override
				protected JsonObject createFromPresets(PromptType type) {
					JsonObject base = super.createFromPresets(type);
					base.addProperty("temperature", 1.0);
					return base;
				}
			};
			ChatConversation chat = createChat();
			chat.getOptions().put(REASONING_EFFORT, ReasoningEffort.MEDIUM);

			runAndAwait(client, "claude-opus-4-6", chat);

			JsonObject req = server.getRequests().get(0);
			assertTrue(req.has("thinking"), "thinking block should be present");
			JsonObject thinking = req.getAsJsonObject("thinking");
			assertEquals("adaptive", thinking.get("type").getAsString());
			assertFalse(thinking.has("display"),
					"Opus 4.6 should not set display — summarized is already the API default");
			assertTrue(req.has("output_config"), "output_config should be present");
			assertEquals("medium", req.getAsJsonObject("output_config").get("effort").getAsString());
			assertTrue(req.has("temperature"), "Opus 4.6 should keep sampling params");
		}
	}

	@Test
	void legacyModelWithReasoningEnabledEmitsManualThinking() throws Exception {
		try (RecordingAnthropicServer server = new RecordingAnthropicServer(MINIMAL_RESPONSE)) {
			AnthropicApiClient client = new AnthropicApiClient(createConnection(server));
			ChatConversation chat = createChat();
			chat.getOptions().put(REASONING_ENABLED, Boolean.TRUE);
			chat.getOptions().put(REASONING_BUDGET_TOKENS, 8192);

			runAndAwait(client, "claude-opus-4-5-20250929", chat);

			JsonObject req = server.getRequests().get(0);
			assertTrue(req.has("thinking"), "thinking block should be present");
			JsonObject thinking = req.getAsJsonObject("thinking");
			assertEquals("enabled", thinking.get("type").getAsString());
			assertEquals(8192, thinking.get("budget_tokens").getAsInt());
			assertFalse(req.has("output_config"), "legacy path must not emit output_config");
			assertEquals(1024 + 8192, req.get("max_tokens").getAsInt(),
					"max_tokens should include the reasoning budget");
		}
	}

	// --- helpers ---

	private ChatConversation createChat() {
		ChatConversation chat = new ChatConversation();
		chat.addMessage(new ChatMessage(Role.USER, "Hello"), false);
		return chat;
	}

	private void runAndAwait(AnthropicApiClient client, String modelId, ChatConversation chat) throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		chat.addListener(new ChatListener() {
			@Override public void onMessageAdded(ChatMessage message, boolean updating) {}
			@Override public void onMessageUpdated(ChatMessage message) {}
			@Override public void onFunctionCall(ChatMessage message) {}
			@Override public void onChatResponseFinished(ChatMessage message) { latch.countDown(); }
		});
		client.performChat(modelId, chat, 1024);
		assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for response");
		for (int i = 0; i < 40 && client.isChatPending(); i++) {
			Thread.sleep(50);
		}
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
