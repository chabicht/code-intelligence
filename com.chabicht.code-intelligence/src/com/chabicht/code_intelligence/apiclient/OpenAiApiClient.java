package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOLS_ENABLED;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOL_PROFILE;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ChatOption;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Implementation for OpenAI or compatible API using the chat completion
 * endpoint.
 */
public class OpenAiApiClient extends AbstractApiClient implements IAiApiClient {

	private CompletableFuture<Void> asyncRequest;

	public OpenAiApiClient(AiApiConnection apiConnection) {
		super(apiConnection);
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "models");
		return res.get("data").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			return new AiModel(apiConnection, id, id);
		}).collect(Collectors.toList());
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	@SuppressWarnings("unchecked")
	<T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath)).GET();
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
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

	@SuppressWarnings("unchecked")
	<T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath, U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = "(nothing)";
		try {
			requestBodyString = gson.toJson(requestBody);
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString));
			requestBuilder.header("Content-Type", "application/json");
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
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
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", completionPrompt.getTemperature());
		// Fix for MistralAI: they don't support max_completion_tokens
		if (!modelName.toLowerCase().contains("mistral")) {
			req.addProperty("max_completion_tokens", Activator.getDefault().getMaxCompletionTokens());
		}

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", completionPrompt.compile());
		messages.add(userMessage);

		req.add("messages", messages);

		JsonObject res = performPost(JsonObject.class, "chat/completions", req);
		return new CompletionResult(res.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message")
				.getAsJsonObject().get("content").getAsString());
	}

	/**
	 * Sends a chat request in streaming mode using the current ChatConversation.
	 * <p>
	 * This method does the following:
	 * <ol>
	 * <li>Builds the JSON request from the conversation messages already present.
	 * (It does not include a reply message yet.)</li>
	 * <li>Adds a new (empty) assistant message to the conversation which will be
	 * updated as the API response streams in.</li>
	 * <li>Sends the request with "stream": true and processes the response
	 * line-by-line.</li>
	 * <li>As each new chunk arrives, it appends the new text to the assistant
	 * message, notifies the conversation listeners, and calls the optional onChunk
	 * callback.</li>
	 * </ol>
	 *
	 * @param modelName the model to use (for example, "gpt-4")
	 * @param chat      the ChatConversation object containing the conversation so
	 *                  far
	 * @param onChunk   a Consumer callback invoked with each new text chunk (may be
	 *                  null)
	 */
	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonArray messagesJson = buildMessagesJson(chat);

		// Create the JSON request object.
		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		// Fix for MistralAI: they don't support max_completion_tokens
		if (!modelName.toLowerCase().contains("mistral")) {
			req.addProperty("max_completion_tokens", maxResponseTokens);
		}
		req.addProperty("stream", true);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			ToolProfile profile = (ToolProfile) options.getOrDefault(TOOL_PROFILE, ToolProfile.ALL);
			if (apiConnection.isLegacyFormat()) {
				patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsOpenAiLegacy(profile));
			} else {
				JsonObject toolDefinitionsOpenAi = ToolDefinitions.getInstance().getToolDefinitionsOpenAi(profile);
				// Hack for Fireworks.AI: they don't support the strict flag in function
				// definitions.
				if (apiConnection.getBaseUri().contains("fireworks.ai")) {
					removeStrictFlag(toolDefinitionsOpenAi);
				}
				patchMissingProperties(req, toolDefinitionsOpenAi);
			}
		}
		req.add("messages", messagesJson);

		// Add a new (empty) assistant message to the conversation.
		// This is the message that will be updated as new text is streamed in.
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		// Prepare the HTTP request.
		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve("chat/completions"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		// Map to keep track of tool calls by their index
		Map<Integer, ToolCallInfo> activeToolCalls = new TreeMap<>();

		// Send the request asynchronously and process the streamed response
		// line-by-line.
		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
				AtomicBoolean reasoningStarted = new AtomicBoolean(false);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					response.body().forEach(line -> {
						// Each chunk from the API is prefixed with "data: ".
						if (line != null && line.startsWith("data: ")) {
							String data = line.substring("data: ".length()).trim();
							if ("[DONE]".equals(data)) {
								// End of stream.
								return;
							}

							try {
								JsonObject jsonChunk = JsonParser.parseString(data).getAsJsonObject();
								JsonArray choices = jsonChunk.getAsJsonArray("choices");
								for (JsonElement choiceElement : choices) {
									JsonObject choice = choiceElement.getAsJsonObject();
									if (choice.has("delta")) {
										JsonObject delta = choice.getAsJsonObject("delta");
										String chunk = "";
										// Special case for DeepSeek: reasoning is in another field than regular
										// content.
										if (delta.has("content") && !delta.get("content").isJsonNull()) {
											chunk = delta.get("content").getAsString();
											if (reasoningStarted.get()) {
												chunk = "</think>\n" + chunk;
												reasoningStarted.set(false);
											}
										} else if (delta.has("reasoning_content")
												&& !delta.get("reasoning_content").isJsonNull()) {
											chunk = delta.get("reasoning_content").getAsString();
											if (!reasoningStarted.get()) {
												chunk = "<think>\n" + chunk;
												reasoningStarted.set(true);
											}
										} else {
											chunk = "";
										}

										// Check for tool_calls in the delta
										if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
											// Process tool calls
											handleToolCallDelta(delta.getAsJsonArray("tool_calls"), activeToolCalls,
													assistantMessage, chat);
										}

										// Check for function_call in the delta (deprecated format)
										if (delta.has("function_call") && !delta.get("function_call").isJsonNull()) {
											// Process function call (deprecated format)
											handleFunctionCallDelta(delta.getAsJsonObject("function_call"),
													activeToolCalls, assistantMessage, chat);
										}

										if (StringUtils.isNotEmpty(chunk)) {
											// Append the received chunk to the assistant message.
											assistantMessage.setContent(assistantMessage.getContent() + chunk);
											// Notify the conversation listeners that the assistant message was updated.
											chat.notifyMessageUpdated(assistantMessage);
										}
									}

									// Check for finish_reason to detect completed tool calls or function calls
									if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
										String finishReason = choice.get("finish_reason").getAsString();
										if ("tool_calls".equals(finishReason) || "function_call".equals(finishReason)) {
											// All tool calls or function calls are complete - finalize any pending
											// calls
											finalizeToolCalls(activeToolCalls, assistantMessage, chat);
										}
									}
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + data, e);
							}
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode() + "\n"
							+ response.body().collect(Collectors.joining("\n")), null);
				}
			} finally {
				// Check if any tool calls are still pending finalization
				if (!activeToolCalls.isEmpty()) {
					finalizeToolCalls(activeToolCalls, assistantMessage, chat);
				}

				chat.notifyChatResponseFinished(assistantMessage);
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat", e);

			// Clean up any pending tool/function calls
			if (!activeToolCalls.isEmpty()) {
				finalizeToolCalls(activeToolCalls, assistantMessage, chat);
			}

			chat.notifyChatResponseFinished(assistantMessage);
			asyncRequest = null;
			return null;
		});
	}

	private void removeStrictFlag(JsonObject toolDefinitionsOpenAi) {
		for (JsonElement el : toolDefinitionsOpenAi.get("tools").getAsJsonArray()) {
			el.getAsJsonObject().get("function").getAsJsonObject().remove("strict");
		}
	}

	private JsonArray buildMessagesJson(ChatConversation chat) {
		JsonArray messagesJson = new JsonArray();
		List<ChatMessage> messagesToSend = new ArrayList<>(chat.getMessages());
		for (ChatMessage msg : messagesToSend) {
			if (Role.TOOL_SUMMARY.equals(msg.getRole())) {
				continue;
			}

			JsonObject jsonMsg = new JsonObject();
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());
			jsonMsg.addProperty("content", compileMessageContent(msg));
			appendAssistantToolCalls(jsonMsg, msg);
			messagesJson.add(jsonMsg);
			appendToolResultMessages(messagesJson, msg);
		}

		logDebugMessagesSummary(messagesJson);
		return messagesJson;
	}

	private String compileMessageContent(ChatMessage message) {
		StringBuilder contentBuilder = new StringBuilder(256);
		if (!message.getContext().isEmpty()) {
			contentBuilder.append("Context information:\n\n");
			for (MessageContext ctx : message.getContext()) {
				contentBuilder.append(ctx.compile(true));
				contentBuilder.append("\n");
			}
		}
		if (message.getContent() != null) {
			contentBuilder.append(message.getContent());
		}
		return contentBuilder.toString();
	}

	private void appendAssistantToolCalls(JsonObject jsonMsg, ChatMessage message) {
		boolean appendedBatchCalls = appendBatchAssistantToolCalls(jsonMsg, message);
		if (!appendedBatchCalls && Role.ASSISTANT.equals(message.getRole()) && message.getFunctionCall().isPresent()) {
			JsonArray toolCallsArray = new JsonArray();
			toolCallsArray.add(buildToolCallItem(message.getFunctionCall().get()));
			jsonMsg.add("tool_calls", toolCallsArray);
		}
	}

	private boolean appendBatchAssistantToolCalls(JsonObject jsonMsg, ChatMessage message) {
		if (message == null || !Role.ASSISTANT.equals(message.getRole()) || message.getFunctionCallBatch().isEmpty()) {
			return false;
		}

		JsonArray toolCallsArray = new JsonArray();
		for (FunctionCallItem item : message.getFunctionCallBatch().get().getItems()) {
			if (item == null || item.getCall() == null) {
				continue;
			}
			toolCallsArray.add(buildToolCallItem(item.getCall()));
		}
		if (toolCallsArray.isEmpty()) {
			return false;
		}

		jsonMsg.add("tool_calls", toolCallsArray);
		return true;
	}

	private JsonObject buildToolCallItem(FunctionCall functionCall) {
		JsonObject toolCallItem = new JsonObject();
		toolCallItem.addProperty("id", functionCall.getId());
		toolCallItem.addProperty("type", "function");

		JsonObject functionDetails = new JsonObject();
		functionDetails.addProperty("name", functionCall.getFunctionName());
		functionDetails.addProperty("arguments", functionCall.getArgsJson());
		toolCallItem.add("function", functionDetails);
		return toolCallItem;
	}

	private void appendToolResultMessages(JsonArray messagesJson, ChatMessage message) {
		boolean appendedBatchResults = appendBatchToolResultMessages(messagesJson, message);
		if (!appendedBatchResults && message.getFunctionResult().isPresent()) {
			messagesJson.add(buildToolResultMessage(message.getFunctionResult().get()));
		}
	}

	private boolean appendBatchToolResultMessages(JsonArray messagesJson, ChatMessage message) {
		if (message == null || message.getFunctionCallBatch().isEmpty()) {
			return false;
		}

		boolean appended = false;
		FunctionCallBatch batch = message.getFunctionCallBatch().get();
		for (FunctionCallItem item : batch.getItems()) {
			if (item == null || item.getResult() == null) {
				continue;
			}
			messagesJson.add(buildToolResultMessage(item.getResult()));
			appended = true;
		}
		return appended;
	}

	private JsonObject buildToolResultMessage(FunctionResult functionResult) {
		JsonObject toolMessage = new JsonObject();
		toolMessage.addProperty("role", "tool");
		toolMessage.addProperty("tool_call_id", functionResult.getId());
		toolMessage.addProperty("content", functionResult.getResultJson());
		return toolMessage;
	}

	private void logDebugMessagesSummary(JsonArray messagesJson) {
		if (!isDebugToolBatchLoggingEnabled()) {
			return;
		}

		int assistantToolCallCount = 0;
		int toolResultMessageCount = 0;
		for (JsonElement messageElement : messagesJson) {
			if (!messageElement.isJsonObject()) {
				continue;
			}
			JsonObject messageObject = messageElement.getAsJsonObject();
			if (messageObject.has("tool_calls") && messageObject.get("tool_calls").isJsonArray()) {
				assistantToolCallCount += messageObject.getAsJsonArray("tool_calls").size();
			}
			if (messageObject.has("role") && !messageObject.get("role").isJsonNull()
					&& "tool".equals(messageObject.get("role").getAsString())) {
				toolResultMessageCount++;
			}
		}

		Activator.logInfo(String.format(
				"openai chat request built: messages=%d, assistant_tool_calls=%d, tool_results=%d",
				messagesJson.size(), assistantToolCallCount, toolResultMessageCount));
	}

	private boolean isDebugToolBatchLoggingEnabled() {
		Activator activator = Activator.getDefault();
		return activator != null
				&& activator.getPreferenceStore().getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS);
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", 1);

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", content);
		messages.add(userMessage);

		req.add("messages", messages);

		JsonObject res = performPost(JsonObject.class, "chat/completions", req);
		return res.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject()
				.get("content").getAsString();
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

	/**
	 * Processes tool call deltas from the streaming API response.
	 * 
	 * @param toolCallDeltas   The tool call deltas from the current chunk
	 * @param activeToolCalls  Map of active tool calls being tracked
	 * @param assistantMessage The assistant message to update
	 * @param chat             The chat conversation
	 */
	private void handleToolCallDelta(JsonArray toolCallDeltas, Map<Integer, ToolCallInfo> activeToolCalls,
			ChatConversation.ChatMessage assistantMessage, ChatConversation chat) {
		for (JsonElement toolCallElement : toolCallDeltas) {
			JsonObject toolCallDelta = toolCallElement.getAsJsonObject();

			try {
				if (!toolCallDelta.has("index") || toolCallDelta.get("index").isJsonNull()) {
					continue;
				}

				int index = toolCallDelta.get("index").getAsInt();
				ToolCallInfo toolCallInfo = activeToolCalls.computeIfAbsent(index, ToolCallInfo::new);

				if (toolCallDelta.has("id") && !toolCallDelta.get("id").isJsonNull()) {
					toolCallInfo.setId(toolCallDelta.get("id").getAsString());
				}

				if (toolCallDelta.has("function") && toolCallDelta.get("function").isJsonObject()) {
					JsonObject function = toolCallDelta.getAsJsonObject("function");
					if (function.has("name") && !function.get("name").isJsonNull()) {
						toolCallInfo.setName(function.get("name").getAsString());
					}
					if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
						String argumentChunk = function.get("arguments").getAsString();
						toolCallInfo.appendArguments(argumentChunk);
					}
				}
			} catch (Exception e) {
				Activator.logError("Error processing tool call delta: " + toolCallDelta, e);
				// We catch the exception but don't rethrow to allow processing to continue
			}
		}
	}

	/**
	 * Finalizes any pending tool calls when streaming ends.
	 * 
	 * @param activeToolCalls  Map of active tool calls being tracked
	 * @param assistantMessage The assistant message to update
	 * @param chat             The chat conversation
	 */
	private void finalizeToolCalls(Map<Integer, ToolCallInfo> activeToolCalls,
			ChatConversation.ChatMessage assistantMessage, ChatConversation chat) {
		if (activeToolCalls == null || activeToolCalls.isEmpty()) {
			return;
		}

		FunctionCallBatch batch = new FunctionCallBatch();
		for (ToolCallInfo toolCall : activeToolCalls.values()) {
			toolCall.markComplete();
			FunctionCall functionCall = toolCall.toFunctionCall();
			if (functionCall != null) {
				batch.addCall(functionCall);
			}
		}

		if (!batch.getItems().isEmpty()) {
			assistantMessage.setFunctionCallBatch(batch);
			if (assistantMessage.getFunctionCall().isEmpty()) {
				assistantMessage.setFunctionCall(batch.getItems().get(0).getCall());
			}
			logDebugBatchParsed(assistantMessage, batch);
			chat.notifyFunctionCalled(assistantMessage);
		}

		activeToolCalls.clear();
	}

	/**
	 * Processes function call deltas from the streaming API response (deprecated
	 * format).
	 * 
	 * @param functionCallDelta The function call delta from the current chunk
	 * @param activeToolCalls   Map of active tool calls being tracked
	 * @param assistantMessage  The assistant message to update
	 * @param chat              The chat conversation
	 */
	private void handleFunctionCallDelta(JsonObject functionCallDelta, Map<Integer, ToolCallInfo> activeToolCalls,
			ChatConversation.ChatMessage assistantMessage, ChatConversation chat) {
		try {
			// For the deprecated function_call format, we always use index 0
			// (there is only one function call in this format)
			int index = 0;
			ToolCallInfo toolCallInfo = activeToolCalls.computeIfAbsent(index, ToolCallInfo::new);

			if (functionCallDelta.has("name") && !functionCallDelta.get("name").isJsonNull()) {
				toolCallInfo.setName(functionCallDelta.get("name").getAsString());
				if (StringUtils.isBlank(toolCallInfo.getId())) {
					toolCallInfo.setId("call_func_" + System.currentTimeMillis());
				}
			}

			if (functionCallDelta.has("arguments") && !functionCallDelta.get("arguments").isJsonNull()) {
				String argumentChunk = functionCallDelta.get("arguments").getAsString();
				toolCallInfo.appendArguments(argumentChunk);
			}
		} catch (Exception e) {
			Activator.logError("Error processing function call delta: " + functionCallDelta, e);
		}
	}

	private void logDebugBatchParsed(ChatMessage message, FunctionCallBatch batch) {
		if (!isDebugToolBatchLoggingEnabled()) {
			return;
		}
		String callIds = batch.getItems().stream().filter(item -> item != null && item.getCall() != null)
				.map(item -> StringUtils.defaultIfBlank(item.getCall().getId(), "no-id"))
				.collect(Collectors.joining(", "));
		Activator.logInfo(String.format("openai multi-tool parsed: messageId=%s, batchId=%s, calls=%d, callIds=[%s]",
				message != null ? message.getId() : null, batch.getBatchId(), batch.getItems().size(), callIds));
	}

	/**
	 * Helper class to track and accumulate tool call information from streaming
	 * responses.
	 */
	private static class ToolCallInfo {
		private final int index; // The position of this tool call in the array
		private String id; // The unique ID of the tool call
		private String name; // The function name
		private final StringBuilder argumentsJson = new StringBuilder(); // Accumulating JSON arguments
		private boolean isComplete = false; // Whether the tool call is complete
		private String errorMessage = null; // Any error message if the tool call failed

		public ToolCallInfo(int index) {
			this.index = index;
		}

		public void appendArguments(String argumentChunk) {
			if (argumentChunk != null) {
				argumentsJson.append(argumentChunk);
			}
		}

		public boolean isComplete() {
			return isComplete;
		}

		public void markComplete() {
			isComplete = true;
		}

		public void markFailure(String message) {
			this.errorMessage = message;
			this.isComplete = true; // Mark as complete to avoid further processing
		}

		public FunctionCall toFunctionCall() {
			if (StringUtils.isBlank(name)) {
				return null;
			}
			String resolvedId = StringUtils.defaultIfBlank(id, "call_" + index);
			String arguments = StringUtils.defaultIfBlank(argumentsJson.toString(), "{}");
			return new FunctionCall(resolvedId, name, arguments);
		}

		// Getters
		public int getIndex() {
			return index;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			if (StringUtils.isNotBlank(id)) {
				this.id = id;
			}
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			if (StringUtils.isNotBlank(name)) {
				this.name = name;
			}
		}

		public String getArgumentsJson() {
			return argumentsJson.toString();
		}

		public boolean hasFailed() {
			return errorMessage != null;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}
}
