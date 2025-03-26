package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatOption;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.PromptType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

public class AnthropicApiClient extends AbstractApiClient implements IAiApiClient {

	private transient final Gson gson = Activator.getDefault().createGson();
	private CompletableFuture<Void> asyncRequest;

	private static final String ANTHROPIC_VERSION = "2023-06-01";

	public AnthropicApiClient(AiApiConnection apiConnection) {
		super(apiConnection);
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "models");
		return res.get("data").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			String displayName = o.has("display_name") ? o.get("display_name").getAsString() : id;
			return new AiModel(apiConnection, id, displayName);
		}).collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	<T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath)).GET()
					.header("x-api-key", apiConnection.getApiKey()).header("anthropic-version", ANTHROPIC_VERSION);

			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("x-api-key", apiConnection.getApiKey());
			}

			HttpRequest request = requestBuilder.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			Activator.logError(String.format("""
					Error during API request:
					URI: %s
					Method: GET
					Status code: %d
					Response:
					%s
					""", apiConnection.getBaseUri() + relPath, statusCode, responseBody), e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.addProperty("max_tokens", Activator.getDefault().getMaxCompletionTokens());

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", completionPrompt.compile());
		messages.add(userMessage);

		req.add("messages", messages);

		JsonObject res = performPost(JsonObject.class, "messages", req);
		return new CompletionResult(
				res.get("content").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString());
	}

	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);

		JsonArray messages = new JsonArray();
		for (ChatConversation.ChatMessage msg : chat.getMessages()) {
			if (Role.SYSTEM.equals(msg.getRole())) {
				req.add("system", new JsonPrimitive(msg.getContent()));
				continue;
			}

			JsonObject jsonMsg = new JsonObject();
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());

			StringBuilder content = new StringBuilder(256);
			if (!msg.getContext().isEmpty()) {
				content.append("Context information:\n\n");
			}
			for (ChatConversation.MessageContext ctx : msg.getContext()) {
				content.append(ctx.compile());
				content.append("\n");
			}
			content.append(msg.getContent());
			jsonMsg.addProperty("content", content.toString());
			messages.add(jsonMsg);
		}
		req.add("messages", messages);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(REASONING_ENABLED) && Boolean.TRUE.equals(options.get(REASONING_ENABLED))) {
			int reasoningBudgetTokens = (int) options.get(REASONING_BUDGET_TOKENS);
			req.addProperty("max_tokens", maxResponseTokens + reasoningBudgetTokens);
			JsonObject thinking = new JsonObject();
			req.add("thinking", thinking);
			thinking.add("type", new JsonPrimitive("enabled"));
			thinking.add("budget_tokens", new JsonPrimitive(reasoningBudgetTokens));
		} else {
			req.addProperty("max_tokens", maxResponseTokens);
		}

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(apiConnection.getBaseUri() + "/messages"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("x-api-key", apiConnection.getApiKey())
				.header("Content-Type", "application/json").header("anthropic-version", ANTHROPIC_VERSION);

		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("x-api-key", apiConnection.getApiKey());
		}

		HttpRequest request = requestBuilder.build();

		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					AtomicReference<String> currentEvent = new AtomicReference<>("");
					AtomicBoolean thinkingStarted = new AtomicBoolean(false);

					response.body().forEach(line -> {
						if (line == null || line.isEmpty()) {
							return;
						}

						if (line.startsWith("event: ")) {
							currentEvent.set(line.substring("event: ".length()).trim());
						} else if (line.startsWith("data: ")) {
							String data = line.substring("data: ".length()).trim();

							try {
								JsonObject jsonResponse = JsonParser.parseString(data).getAsJsonObject();

								switch (currentEvent.get()) {
								case "content_block_delta":
									JsonObject delta = jsonResponse.getAsJsonObject("delta");
									String deltaType = delta.get("type").getAsString();

									if (deltaType.equals("text_delta")) {
										if (thinkingStarted.get()) {
											assistantMessage.setContent(assistantMessage.getContent() + "\n</think>\n");
											thinkingStarted.set(false);
										}
										String text = delta.get("text").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + text);
										chat.notifyMessageUpdated(assistantMessage);
									} else if (deltaType.equals("thinking_delta")) {
										if (!thinkingStarted.get()) {
											assistantMessage.setContent(assistantMessage.getContent() + "\n<think>\n");
											thinkingStarted.set(true);
										}
										// Handle thinking delta
										String thinking = delta.get("thinking").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + thinking);
										chat.notifyMessageUpdated(assistantMessage);
									} else if (deltaType.equals("signature_delta")) {
										// Handle signature delta for thinking content
										// This signals the end of a thinking block
										// No additional action required here
									}
									break;

								case "message_stop":
									// Handle end of message
									break;

								case "content_block_start":
								case "content_block_stop":
								case "message_start":
								case "message_delta":
								case "ping":
									// These events can be handled if needed
									break;

								default:
									Activator.logError("Unknown event type in stream: " + currentEvent, null);
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + data, e);
							}
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode() + "\n"
							+ response.body().collect(Collectors.joining()), null);
				}
			} finally {
				chat.notifyChatResponseFinished(assistantMessage);
				asyncRequest = null;
			}
		}).exceptionally(e -> {
		    Activator.logError("Exception during streaming chat", e);
		    return null;
		});
	}

	@SuppressWarnings("unchecked")
	<T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath, U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = "(nothing)";
		try {
			requestBodyString = gson.toJson(requestBody);
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
					.header("x-api-key", apiConnection.getApiKey()).header("Content-Type", "application/json")
					.header("anthropic-version", ANTHROPIC_VERSION);

			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("x-api-key", apiConnection.getApiKey());
			}

			HttpRequest request = requestBuilder.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();

			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(
						String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			Activator.logError(String.format("""
					Error during API request:
					URI: %s
					Method: POST
					Status code: %d
					Request:
					%s
					Response:
					%s
					""", apiConnection.getBaseUri() + relPath, statusCode, requestBodyString, responseBody), e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "max_tokens", 1024);

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", content);
		messages.add(userMessage);

		req.add("messages", messages);

		JsonObject res = performPost(JsonObject.class, "messages", req);
		return res.get("content").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
	}

	@Override
	public synchronized boolean isChatPending() {
		return asyncRequest != null;
	}

	@Override
	public synchronized void abortChat() {
		if (asyncRequest != null) {
			asyncRequest.cancel(true);
			asyncRequest = null;
		}
	}
}