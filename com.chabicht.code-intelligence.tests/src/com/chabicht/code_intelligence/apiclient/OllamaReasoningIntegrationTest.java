package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_EFFORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningEffort;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class OllamaReasoningIntegrationTest {

	@Test
	void performChatAddsThinkLevelForSupportedThinkingModel() throws Exception {
		try (ReasoningAwareOllamaServer server = new ReasoningAwareOllamaServer("0.17.7",
				jsonArray("completion", "tools", "thinking"))) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = createChat(ReasoningEffort.MEDIUM);
			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished));

			client.performChat("qwen3.5:35b", chat, 128);
			awaitChatFinished(client, responseFinished);

			assertEquals(1, server.getChatRequests().size());
			assertEquals("medium", server.getChatRequests().get(0).get("think").getAsString());
			assertEquals(1, server.getVersionRequestCount());
			assertEquals(1, server.getShowRequestCount());
		}
	}

	@Test
	void performChatAddsThinkFalseForExplicitOff() throws Exception {
		try (ReasoningAwareOllamaServer server = new ReasoningAwareOllamaServer("0.17.7",
				jsonArray("completion", "thinking"))) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = createChat(ReasoningEffort.NONE);
			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished));

			client.performChat("qwen3.5:35b", chat, 128);
			awaitChatFinished(client, responseFinished);

			assertFalse(server.getChatRequests().get(0).get("think").getAsBoolean());
		}
	}

	@Test
	void performChatOmitsThinkForModelDefaultWithoutProbing() throws Exception {
		try (ReasoningAwareOllamaServer server = new ReasoningAwareOllamaServer("0.17.7",
				jsonArray("completion", "thinking"))) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = createChat(ReasoningEffort.DEFAULT);
			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished));

			client.performChat("qwen3.5:35b", chat, 128);
			awaitChatFinished(client, responseFinished);

			assertFalse(server.getChatRequests().get(0).has("think"));
			assertEquals(0, server.getVersionRequestCount());
			assertEquals(0, server.getShowRequestCount());
		}
	}

	@Test
	void performChatOmitsThinkForOlderOllamaServers() throws Exception {
		try (ReasoningAwareOllamaServer server = new ReasoningAwareOllamaServer("0.8.9",
				jsonArray("completion", "thinking"))) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = createChat(ReasoningEffort.HIGH);
			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished));

			client.performChat("qwen3.5:35b", chat, 128);
			awaitChatFinished(client, responseFinished);

			assertFalse(server.getChatRequests().get(0).has("think"));
			assertEquals(1, server.getVersionRequestCount());
			assertEquals(0, server.getShowRequestCount());
		}
	}

	@Test
	void performChatOmitsThinkForModelsWithoutThinkingCapability() throws Exception {
		try (ReasoningAwareOllamaServer server = new ReasoningAwareOllamaServer("0.17.7", jsonArray("completion"))) {
			OllamaApiClient client = new OllamaApiClient(createConnection(server));
			ChatConversation chat = createChat(ReasoningEffort.HIGH);
			CountDownLatch responseFinished = new CountDownLatch(1);
			chat.addListener(createListener(responseFinished));

			client.performChat("codellama:latest", chat, 128);
			awaitChatFinished(client, responseFinished);

			assertFalse(server.getChatRequests().get(0).has("think"));
			assertEquals(1, server.getVersionRequestCount());
			assertEquals(1, server.getShowRequestCount());
		}
	}

	private ChatConversation createChat(ReasoningEffort reasoningEffort) {
		ChatConversation chat = new ChatConversation();
		chat.getOptions().put(REASONING_EFFORT, reasoningEffort);
		chat.addMessage(new ChatMessage(Role.USER, "Count to one."), false);
		return chat;
	}

	private ChatListener createListener(CountDownLatch responseFinished) {
		return new ChatListener() {
			@Override
			public void onMessageAdded(ChatMessage message, boolean updating) {
			}

			@Override
			public void onMessageUpdated(ChatMessage message) {
			}

			@Override
			public void onFunctionCall(ChatMessage message) {
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

	private JsonArray jsonArray(String... values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		return array;
	}

	private AiApiConnection createConnection(ReasoningAwareOllamaServer server) {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.OLLAMA);
		connection.setBaseUri(server.getBaseUri());
		connection.setEnabled(true);
		return connection;
	}

	private static final class ReasoningAwareOllamaServer implements AutoCloseable {
		private static final String CHAT_RESPONSE = """
				{"model":"test","message":{"role":"assistant","content":"One"},"done":true}
				""";

		private final HttpServer server;
		private final String version;
		private final JsonArray capabilities;
		private final List<JsonObject> chatRequests = new ArrayList<>();
		private int versionRequestCount;
		private int showRequestCount;

		private ReasoningAwareOllamaServer(String version, JsonArray capabilities) throws IOException {
			this.version = version;
			this.capabilities = capabilities;
			server = HttpServer.create(new InetSocketAddress(0), 0);
			server.createContext("/api/version", this::handleVersion);
			server.createContext("/api/show", this::handleShow);
			server.createContext("/api/chat", this::handleChat);
			server.start();
		}

		private void handleVersion(HttpExchange exchange) throws IOException {
			versionRequestCount++;
			writeJsonResponse(exchange, """
					{"version":"%s"}
					""".formatted(version));
		}

		private void handleShow(HttpExchange exchange) throws IOException {
			showRequestCount++;
			exchange.getRequestBody().readAllBytes();
			JsonObject response = new JsonObject();
			response.add("capabilities", capabilities.deepCopy());
			writeJsonResponse(exchange, response.toString());
		}

		private void handleChat(HttpExchange exchange) throws IOException {
			byte[] requestBytes = exchange.getRequestBody().readAllBytes();
			chatRequests.add(JsonParser.parseString(new String(requestBytes, StandardCharsets.UTF_8)).getAsJsonObject());
			writeJsonResponse(exchange, CHAT_RESPONSE.strip() + "\n");
		}

		private void writeJsonResponse(HttpExchange exchange, String responseBody) throws IOException {
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

		private List<JsonObject> getChatRequests() {
			return chatRequests;
		}

		private int getVersionRequestCount() {
			return versionRequestCount;
		}

		private int getShowRequestCount() {
			return showRequestCount;
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
