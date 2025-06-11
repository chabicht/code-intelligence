package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOLS_ENABLED;

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
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatOption;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
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
		if (res.has("usage")) {
			logApiUsage(modelName, res.getAsJsonObject("usage"), "completion");
		}
		return new CompletionResult(
				res.get("content").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString());
	}

	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsAnthropic());
		}

		// Add system prompt if present
		for (ChatConversation.ChatMessage msg : chat.getMessages()) {
			if (Role.SYSTEM.equals(msg.getRole())) {
				req.add("system", new JsonPrimitive(msg.getContent()));
				break;
			}
		}

		// Add messages array
		req.add("messages", createMessagesArray(chat));

		// Set max tokens
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

		// Create assistant message that will be populated with the response
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		// Build request and initiate streaming
		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(apiConnection.getBaseUri() + "/messages"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("x-api-key", apiConnection.getApiKey())
				.header("Content-Type", "application/json").header("anthropic-version", ANTHROPIC_VERSION);

		HttpRequest request = requestBuilder.build();
		AtomicBoolean responseFinished = new AtomicBoolean(false);
		AtomicReference<ToolUseInfo> currentToolUse = new AtomicReference<>(null);

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

								if (jsonResponse.has("usage")) {
									logApiUsage(modelName, jsonResponse.getAsJsonObject("usage"), "chat");
								} else if (jsonResponse.has("message")) {
									JsonObject message = jsonResponse.get("message").getAsJsonObject();
									if (message.has("usage")) {
										logApiUsage(modelName, message.getAsJsonObject("usage"), "chat");
									}
								}

								// Extract the event type from the JSON response
								if (jsonResponse.has("type")) {
									String eventType = jsonResponse.get("type").getAsString();
									currentEvent.set(eventType);

									switch (eventType) {
									case "content_block_delta":
										if (jsonResponse.has("delta")) {
											JsonObject delta = jsonResponse.getAsJsonObject("delta");
											String deltaType = delta.get("type").getAsString();

											if (deltaType.equals("text_delta")) {
												if (thinkingStarted.get()) {
													assistantMessage
															.setContent(assistantMessage.getContent() + "\n</think>\n");
													thinkingStarted.set(false);
												}
												String text = delta.get("text").getAsString();
												assistantMessage.setContent(assistantMessage.getContent() + text);
												chat.notifyMessageUpdated(assistantMessage);
											} else if (deltaType.equals("thinking_delta")) {
												if (!thinkingStarted.get()) {
													assistantMessage
															.setContent(assistantMessage.getContent() + "\n<think>\n");
													thinkingStarted.set(true);
												}
												// Handle thinking delta
												String thinking = delta.get("thinking").getAsString();
												assistantMessage.setContent(assistantMessage.getContent() + thinking);
												assistantMessage.setThinkingContent(
														(assistantMessage.getThinkingContent() == null ? ""
																: assistantMessage.getThinkingContent()) + thinking);
												chat.notifyMessageUpdated(assistantMessage);
											} else if (deltaType.equals("input_json_delta")) {
												// Accumulate tool input JSON
												if (jsonResponse.has("index")) {
													int index = jsonResponse.get("index").getAsInt();
													ToolUseInfo toolUse = currentToolUse.get();
													if (toolUse != null) {
														String partialJson = delta.get("partial_json").getAsString();
														toolUse.addInputJson(partialJson);
													}
												}
											} else if (deltaType.equals("signature_delta")) {
												assistantMessage.getThinkingMetadata().put("signature",
														delta.get("signature").getAsString());
											}
										}
										break;

									case "content_block_start":
										if (jsonResponse.has("content_block")) {
											JsonObject contentBlock = jsonResponse.getAsJsonObject("content_block");
											String blockType = contentBlock.get("type").getAsString();

											if (blockType.equals("tool_use")) {
												// Store the tool use information for later when we receive
												// input_json_delta events
												String id = contentBlock.get("id").getAsString();
												String name = contentBlock.get("name").getAsString();
												currentToolUse.set(new ToolUseInfo(id, name));
											} else if (blockType.equals("thinking")) {
												if (!thinkingStarted.get()) {
													assistantMessage
															.setContent(assistantMessage.getContent() + "\n<think>\n");
													thinkingStarted.set(true);
												}
											}
										}
										break;

									case "content_block_stop":
										if (jsonResponse.has("index")) {
											int index = jsonResponse.get("index").getAsInt();

											// If we have a completed tool use
											ToolUseInfo toolUse = currentToolUse.get();
											if (toolUse != null && toolUse.isComplete()) {
												JsonObject input = JsonParser.parseString(toolUse.getCompleteJson())
														.getAsJsonObject();
												String argsJson = gson.toJson(input);

												assistantMessage.setFunctionCall(
														new FunctionCall(toolUse.getId(), toolUse.getName(), argsJson));
												chat.notifyFunctionCalled(assistantMessage);
												currentToolUse.set(null);
											}
										}
										break;

									case "message_delta":
										if (jsonResponse.has("delta")) {
											JsonObject delta = jsonResponse.getAsJsonObject("delta");
											if (delta.has("stop_reason")
													&& "tool_use".equals(delta.get("stop_reason").getAsString())) {
												if (!responseFinished.get()) {
													chat.notifyChatResponseFinished(assistantMessage);
													responseFinished.set(true);
												}
											}
										}
										break;

									case "message_stop":
										// Handle end of message
										if (!responseFinished.get()) {
											chat.notifyChatResponseFinished(assistantMessage);
											responseFinished.set(true);
										}
										break;

									case "message_start":
									case "ping":
										// These events can be handled if needed
										break;

									default:
										Activator.logError("Unknown event type in stream: " + eventType, null);
									}
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + data, e);
							}

						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode() + "\n"
							+ response.body().collect(Collectors.joining()) + "\n\nRequest JSON:\n" + requestBody,
							null);
				}
			} finally {
				if (!responseFinished.get()) {
					chat.notifyChatResponseFinished(assistantMessage);
				}
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat", e);
			if (!responseFinished.get()) {
				chat.notifyChatResponseFinished(assistantMessage);
			}
			asyncRequest = null;
			return null;
		});
	}

	/**
	 * @param chat
	 * @return
	 */
	private JsonArray createMessagesArray(ChatConversation chat) {
		JsonArray messagesJson = new JsonArray();

		for (ChatConversation.ChatMessage msg : chat.getMessages()) {
			// Skip system messages, they're handled separately
			if (Role.SYSTEM.equals(msg.getRole())) {
				continue;
			}
            // Skip TOOL_SUMMARY messages, they are for internal use only
            if (Role.TOOL_SUMMARY.equals(msg.getRole())) {
                continue;
            }

			// Build message text with context
			StringBuilder contentBuilder = new StringBuilder();
			if (!msg.getContext().isEmpty()) {
				contentBuilder.append("Context information:\n\n");
				for (MessageContext ctx : msg.getContext()) {
					contentBuilder.append(ctx.compile(true));
					contentBuilder.append("\n");
				}
			}
			contentBuilder.append(msg.getContent());

			String messageContent = contentBuilder.toString();

			// Skip if both message content and thinking content are blank
			if (StringUtils.isBlank(messageContent) && StringUtils.isBlank(msg.getThinkingContent())) {
				continue;
			}

			// Create the message JSON object
			JsonObject jsonMsg = new JsonObject();
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());

			JsonArray contentArray = new JsonArray();

			// For assistant messages, add thinking content first if available
			if (Role.ASSISTANT.equals(msg.getRole()) && StringUtils.isNotBlank(msg.getThinkingContent())) {
				JsonObject thinkingContent = new JsonObject();
				thinkingContent.addProperty("type", "thinking");
				thinkingContent.addProperty("thinking", msg.getThinkingContent());

				// Add signature field required by Anthropic API
				// If the field is missing we assume the user switched models. In that case we
				// can't send the thoughts "back" to the API.
				Object signature = msg.getThinkingMetadata().get("signature");
				if (signature != null) {
					thinkingContent.addProperty("signature", (String) signature);

					contentArray.add(thinkingContent);
				}
			}

			// Add text content block if message content is not blank
			if (StringUtils.isNotBlank(messageContent)) {
				JsonObject textContent = new JsonObject();
				textContent.addProperty("type", "text");
				textContent.addProperty("text", messageContent);
				contentArray.add(textContent);
			}

			// If this is an assistant message with a function call, add a tool_use content
			// block
			if (Role.ASSISTANT.equals(msg.getRole()) && msg.getFunctionCall().isPresent()) {
				FunctionCall fc = msg.getFunctionCall().get();

				JsonObject toolUseBlock = new JsonObject();
				toolUseBlock.addProperty("type", "tool_use");
				toolUseBlock.addProperty("id", fc.getId());
				toolUseBlock.addProperty("name", fc.getFunctionName());

				// Parse the argsJson back to a JsonObject
				JsonObject inputObject = JsonParser.parseString(fc.getArgsJson()).getAsJsonObject();
				toolUseBlock.add("input", inputObject);

				contentArray.add(toolUseBlock);
			}

			jsonMsg.add("content", contentArray);
			messagesJson.add(jsonMsg);

			// Then, if there's a function result, add it as a separate user message
			if (msg.getFunctionResult().isPresent()) {
				FunctionResult fr = msg.getFunctionResult().get();

				JsonObject resultMsg = new JsonObject();
				resultMsg.addProperty("role", "user");

				JsonArray resultContentArray = new JsonArray();

				// Add the tool result block
				JsonObject toolResult = new JsonObject();
				toolResult.addProperty("type", "tool_result");
				toolResult.addProperty("tool_use_id", fr.getId());
				toolResult.addProperty("content", fr.getResultJson());
				resultContentArray.add(toolResult);

				resultMsg.add("content", resultContentArray);
				messagesJson.add(resultMsg);
			}
		}

		// Cache conversation with default TTL (5 minutes).
		if (messagesJson.size() > 0) {
			JsonObject lastMessage = messagesJson.get(messagesJson.size() - 1).getAsJsonObject();
			JsonArray contentArray = lastMessage.get("content").getAsJsonArray();
			JsonObject content = contentArray.get(0).getAsJsonObject();
			JsonObject cacheControlObj = new JsonObject();
			cacheControlObj.addProperty("type", "ephemeral");
			content.add("cache_control", cacheControlObj);
		}

		return messagesJson;
	}

	/**
	 * Helper method to add a property to a JsonObject based on its type.
	 * 
	 * @param jsonObject The JsonObject to add the property to
	 * @param key        The property key
	 * @param value      The property value
	 */
	private void addJsonProperty(JsonObject jsonObject, String key, Object value) {
		if (value == null) {
			jsonObject.add(key, null);
		} else if (value instanceof String) {
			jsonObject.addProperty(key, (String) value);
		} else if (value instanceof Number) {
			jsonObject.addProperty(key, (Number) value);
		} else if (value instanceof Boolean) {
			jsonObject.addProperty(key, (Boolean) value);
		} else if (value instanceof Character) {
			jsonObject.addProperty(key, (Character) value);
		} else if (value instanceof JsonElement) {
			jsonObject.add(key, (JsonElement) value);
		} else {
			// For other types, convert to string
			jsonObject.addProperty(key, value.toString());
		}
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
		if (res.has("usage")) {
			logApiUsage(modelName, res.getAsJsonObject("usage"), "caption");
		}
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

	private void logApiUsage(String modelName, JsonObject usage, String apiCallType) {
//		int inputTokens = 0;
//		if (usage.has("input_tokens") && !usage.get("input_tokens").isJsonNull()) {
//			inputTokens = usage.get("input_tokens").getAsInt();
//		}
//		int outputTokens = 0;
//		if (usage.has("output_tokens") && !usage.get("output_tokens").isJsonNull()) {
//			outputTokens = usage.get("output_tokens").getAsInt();
//		}
//		int cacheReadTokens = 0;
//		if (usage.has("cache_read_input_tokens") && !usage.get("cache_read_input_tokens").isJsonNull()) {
//			cacheReadTokens = usage.get("cache_read_input_tokens").getAsInt();
//		}
//		int cacheCreationTokens = 0;
//		if (usage.has("cache_creation_input_tokens") && !usage.get("cache_creation_input_tokens").isJsonNull()) {
//			cacheCreationTokens = usage.get("cache_creation_input_tokens").getAsInt();
//		}
//		String logMessage = String.format(
//				"Anthropic API usage for %s (model: %s): input_tokens=%d, output_tokens=%d, cache_read_tokens=%d, cache_creation_tokens=%d",
//				apiCallType, modelName, inputTokens, outputTokens, cacheReadTokens, cacheCreationTokens);
//		Log.logInfo(logMessage);
	}

	private static class ToolUseInfo {
		private final String id;
		private final String name;
		private final StringBuilder inputJson = new StringBuilder();

		public ToolUseInfo(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public void addInputJson(String partialJson) {
			inputJson.append(partialJson);
		}

		public boolean isComplete() {
			String json = inputJson.toString();
			return json.startsWith("{") && json.endsWith("}");
		}

		public String getCompleteJson() {
			return inputJson.toString();
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}
	}
}