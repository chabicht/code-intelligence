package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_EFFORT;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.ChatSettings;
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningControlMode;
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningEffort;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatOption;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.PromptType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class OllamaApiClient extends AbstractApiClient implements IAiApiClient {

	private static final String NUM_CTX = "num_ctx";
	private static final int DEFAULT_CONTEXT_SIZE = 8192;
	private static final String COMPLETION = "completion";
	private static final String CHAT = "chat";
	private static final int[] MIN_THINKING_API_VERSION = new int[] { 0, 9, 0 };

	private CompletableFuture<Void> asyncRequest;
	private volatile Boolean thinkingRequestSupported;
	private final Map<String, Boolean> modelThinkingSupport = new ConcurrentHashMap<>();

	public OllamaApiClient(AiApiConnection apiConnection) {
		super(apiConnection);
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "api/tags");
		return res.get("models").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("name").getAsString();
			return new AiModel(apiConnection, id, id);
		}).collect(Collectors.toList());
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	private HttpRequest.Builder createRequestBuilder(String relPath) {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath));
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		return requestBuilder;
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest request = createRequestBuilder(relPath).GET().build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(String.format("Error during API request: %s\nStatus code: %d\nResponse: %s",
					apiConnection.getBaseUri() + relPath, statusCode, responseBody), e);
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath,
			U requestBodyJson) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = gson.toJson(requestBodyJson);
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest request = createRequestBuilder(relPath).POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
					.header("Content-Type", "application/json").build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();
			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(
						String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(
					String.format("Error during API request:\nURI: %s\nStatus code: %d\nRequest: %s\nResponse: %s",
							apiConnection.getBaseUri() + relPath, statusCode, requestBodyString, responseBody),
					e);
		}
	}

	@Override
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.addProperty("prompt", (String) completionPrompt.getPromptArgs().get("prefix"));
		req.addProperty("suffix", (String) completionPrompt.getPromptArgs().get("suffix"));
		JsonObject options = getOrAddJsonObject(req, "options");
		setPropertyIfNotPresent(options, "temperature", completionPrompt.getTemperature());
//		setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
		setPropertyIfNotPresent(options, "num_predict", Activator.getDefault().getMaxCompletionTokens());
		req.addProperty("stream", false);

		try {
			JsonObject res = performPost(JsonObject.class, "api/generate", req);
			return new CompletionResult(res.get("response").getAsString());
		} catch (RuntimeException e) {
			req.remove("suffix");
			req.remove("prompt");
			req.addProperty("prompt", completionPrompt.compile());
			JsonObject res = performPost(JsonObject.class, "api/generate", req);
			return new CompletionResult(res.get("response").getAsString());
		}
	}

	/**
	 * Sends a chat request in streaming mode using the current ChatConversation via
	 * the Ollama API.
	 * <p>
	 * This method does the following:
	 * <ol>
	 * <li>Builds the JSON request from the conversation messages already present.
	 * (It does not include a reply message yet.)</li>
	 * <li>Adds a new (empty) assistant message to the conversation which will be
	 * updated as the API response streams in.</li>
	 * <li>Sends the request with "stream": true to the /api/chat endpoint and
	 * processes the response line-by-line.</li>
	 * <li>As each new chunk arrives, it appends the new text to the assistant
	 * message, notifies the conversation listeners, and (optionally) calls any
	 * onChunk callback.</li>
	 * </ol>
	 *
	 * @param modelName         the model to use (for example, "llama3.2")
	 * @param chat              the ChatConversation object containing the
	 *                          conversation so far
	 * @param maxResponseTokens the maximum number of tokens for the response
	 */
	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonArray messagesJson = buildMessagesJson(chat);

		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);

		Map<ChatOption, Object> chatOptions = chat.getOptions();
		applyReasoningEffort(req, modelName, chatOptions);
		if (chatOptions.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(chatOptions.get(TOOLS_ENABLED))) {
			ToolProfile profile = (ToolProfile) chatOptions.getOrDefault(TOOL_PROFILE, ToolProfile.ALL);
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsOllama(profile));
		}
		req.add("messages", messagesJson);

		JsonObject options = getOrAddJsonObject(req, "options");
		// setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
		setPropertyIfNotPresent(options, "num_predict", maxResponseTokens);

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.ALWAYS).build();
		HttpRequest request = createRequestBuilder("api/chat").POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.header("Content-Type", "application/json").build();

		final AtomicBoolean responseFinished = new AtomicBoolean(false);
		final AtomicBoolean thinkingStarted = new AtomicBoolean(false);
		final Map<Integer, FunctionCall> pendingToolCalls = new TreeMap<>();

		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					response.body().forEach(line -> {
						if (line != null && !line.trim().isEmpty()) {
							try {
								JsonObject jsonChunk = JsonParser.parseString(line).getAsJsonObject();
								if (jsonChunk.has("message")) {
									JsonObject messageObj = jsonChunk.getAsJsonObject("message");

									// Handle tool_calls
									if (messageObj.has("tool_calls")) {
										mergeToolCalls(messageObj.getAsJsonArray("tool_calls"), pendingToolCalls,
												assistantMessage);
									}

									// Handle thinking content
									if (messageObj.has("thinking")
											&& !StringUtils.isEmpty(messageObj.get("thinking").getAsString())) {
										if (!thinkingStarted.get()) {
											assistantMessage.setContent(assistantMessage.getContent() + "\n<think>\n");
											thinkingStarted.set(true);
										}
										String thinking = messageObj.get("thinking").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + thinking);
										assistantMessage
												.setThinkingContent((assistantMessage.getThinkingContent() == null ? ""
														: assistantMessage.getThinkingContent()) + thinking);
										chat.notifyMessageUpdated(assistantMessage);
									}

									// Handle content
									if (messageObj.has("content")
											&& !StringUtils.isEmpty(messageObj.get("content").getAsString())) {
										if (thinkingStarted.get()) {
											assistantMessage.setContent(assistantMessage.getContent() + "\n</think>\n");
											thinkingStarted.set(false);
										}
										String chunk = messageObj.get("content").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + chunk);

										chat.notifyMessageUpdated(assistantMessage);
									}
								}
								if (jsonChunk.has("done") && jsonChunk.get("done").getAsBoolean()) {
									finalizeAssistantMessage(assistantMessage, chat, responseFinished);
									return; // End of stream for this line processor
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + line, e);
								finalizeAssistantMessage(assistantMessage, chat, responseFinished);
								asyncRequest = null;
							}
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode()
							+ "\nResponse body: " + response.body().collect(Collectors.joining("\n")), null);
					finalizeAssistantMessage(assistantMessage, chat, responseFinished);
					asyncRequest = null;
				}
			} finally {
				finalizeAssistantMessage(assistantMessage, chat, responseFinished);
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat", e);
			// Ensure assistant message is finalized in case of error before stream
			// completion
			finalizeAssistantMessage(assistantMessage, chat, responseFinished);
			asyncRequest = null;
			return null;

		});
	}

	private void applyReasoningEffort(JsonObject req, String modelName, Map<ChatOption, Object> options) {
		if (hasNonNullProperty(req, "think") || options == null) {
			return;
		}

		Object effortOption = options.get(REASONING_EFFORT);
		if (!(effortOption instanceof ReasoningEffort reasoningEffort)) {
			return;
		}

		reasoningEffort = ChatSettings.normalizeReasoningEffort(ReasoningControlMode.OLLAMA_EFFORT, reasoningEffort);
		if (reasoningEffort == ReasoningEffort.DEFAULT || !supportsThinkingRequests(modelName)) {
			return;
		}

		switch (reasoningEffort) {
		case NONE:
			req.addProperty("think", false);
			break;
		case LOW:
		case MEDIUM:
		case HIGH:
			req.addProperty("think", reasoningEffort.getApiValue());
			break;
		default:
			break;
		}
	}

	private boolean supportsThinkingRequests(String modelName) {
		if (StringUtils.isBlank(modelName) || !isThinkingRequestSupported()) {
			return false;
		}

		return modelThinkingSupport.computeIfAbsent(modelName, this::probeModelThinkingSupport);
	}

	private boolean isThinkingRequestSupported() {
		Boolean cached = thinkingRequestSupported;
		if (cached != null) {
			return cached.booleanValue();
		}

		synchronized (this) {
			if (thinkingRequestSupported == null) {
				thinkingRequestSupported = Boolean.valueOf(probeThinkingRequestSupport());
			}
			return thinkingRequestSupported.booleanValue();
		}
	}

	private boolean probeThinkingRequestSupport() {
		try {
			JsonObject versionResponse = performGet(JsonObject.class, "api/version");
			if (!hasNonNullProperty(versionResponse, "version")) {
				return false;
			}
			return isVersionAtLeast(versionResponse.get("version").getAsString(), MIN_THINKING_API_VERSION);
		} catch (RuntimeException e) {
			Activator.logError("Unable to determine Ollama reasoning API support from /api/version", e);
			return false;
		}
	}

	private boolean probeModelThinkingSupport(String modelName) {
		try {
			JsonObject request = new JsonObject();
			request.addProperty("model", modelName);
			JsonObject showResponse = performPost(JsonObject.class, "api/show", request);
			if (showResponse.has("capabilities") && showResponse.get("capabilities").isJsonArray()) {
				for (JsonElement capability : showResponse.getAsJsonArray("capabilities")) {
					if (StringUtils.equalsIgnoreCase(capability.getAsString(), "thinking")) {
						return true;
					}
				}
				return false;
			}

			// Some servers expose think without model capabilities metadata. In that case,
			// fall back to the version gate above instead of disabling the feature entirely.
			return true;
		} catch (RuntimeException e) {
			Activator.logError("Unable to determine Ollama model reasoning support from /api/show for " + modelName,
					e);
			return false;
		}
	}

	private boolean isVersionAtLeast(String version, int[] minimumVersion) {
		int[] currentVersion = parseVersion(version);
		for (int i = 0; i < minimumVersion.length; i++) {
			if (currentVersion[i] > minimumVersion[i]) {
				return true;
			}
			if (currentVersion[i] < minimumVersion[i]) {
				return false;
			}
		}
		return true;
	}

	private int[] parseVersion(String version) {
		int[] parsedVersion = new int[] { 0, 0, 0 };
		String normalizedVersion = StringUtils.removeStartIgnoreCase(StringUtils.defaultString(version).trim(), "v");
		String[] parts = normalizedVersion.split("[^0-9]+");
		int targetIndex = 0;
		for (String part : parts) {
			if (StringUtils.isBlank(part)) {
				continue;
			}
			parsedVersion[targetIndex++] = Integer.parseInt(part);
			if (targetIndex == parsedVersion.length) {
				break;
			}
		}
		return parsedVersion;
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.addProperty("prompt", content);
		JsonObject options = getOrAddJsonObject(req, "options");
		setPropertyIfNotPresent(options, "temperature", 1);
//		setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
		setPropertyIfNotPresent(options, "num_predict", Activator.getDefault().getMaxCompletionTokens());
		req.addProperty("stream", false);

		JsonObject res = performPost(JsonObject.class, "api/generate", req);

		return res.get("response").getAsString();
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

	private JsonArray buildMessagesJson(ChatConversation chat) {
		JsonArray messagesJson = new JsonArray();
		for (ChatMessage message : new ArrayList<>(chat.getMessages())) {
			if (Role.TOOL_SUMMARY.equals(message.getRole())) {
				continue;
			}

			JsonObject jsonMsg = new JsonObject();
			jsonMsg.addProperty("role", message.getRole().toString().toLowerCase());
			jsonMsg.addProperty("content", compileMessageContent(message));
			appendAssistantToolCalls(jsonMsg, message);
			messagesJson.add(jsonMsg);
			appendToolResultMessages(messagesJson, message);
		}
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
		appendBatchAssistantToolCalls(jsonMsg, message);
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
		JsonObject functionDetails = new JsonObject();
		functionDetails.addProperty("name", functionCall.getFunctionName());
		functionDetails.add("arguments", parseJsonElementOrEmptyObject(functionCall.getArgsJson()));
		toolCallItem.add("function", functionDetails);
		return toolCallItem;
	}

	private void appendToolResultMessages(JsonArray messagesJson, ChatMessage message) {
		appendBatchToolResultMessages(messagesJson, message);
	}

	private boolean appendBatchToolResultMessages(JsonArray messagesJson, ChatMessage message) {
		if (message == null || message.getFunctionCallBatch().isEmpty()) {
			return false;
		}

		boolean appended = false;
		for (FunctionCallItem item : message.getFunctionCallBatch().get().getItems()) {
			if (item == null || item.getResult() == null) {
				continue;
			}
			messagesJson.add(buildToolResultMessage(item.getResult()));
			appended = true;
		}
		return appended;
	}

	private JsonObject buildToolResultMessage(FunctionResult functionResult) {
		JsonObject toolResultMsgJson = new JsonObject();
		toolResultMsgJson.addProperty("role", "tool");
		toolResultMsgJson.addProperty("content", StringUtils.defaultString(functionResult.getResultJson()));
		toolResultMsgJson.addProperty("tool_name", functionResult.getFunctionName());
		return toolResultMsgJson;
	}

	private void mergeToolCalls(JsonArray toolCallsArray, Map<Integer, FunctionCall> pendingToolCalls,
			ChatMessage assistantMessage) {
		if (toolCallsArray == null || toolCallsArray.isEmpty()) {
			return;
		}

		for (int i = 0; i < toolCallsArray.size(); i++) {
			JsonElement toolCallElement = toolCallsArray.get(i);
			if (!toolCallElement.isJsonObject()) {
				continue;
			}

			JsonObject toolCallObj = toolCallElement.getAsJsonObject();
			FunctionCall functionCall = parseToolCall(toolCallObj);
			if (functionCall == null) {
				continue;
			}

			pendingToolCalls.put(extractToolCallIndex(toolCallObj, i), functionCall);
		}

		if (pendingToolCalls.isEmpty()) {
			return;
		}

		FunctionCallBatch batch = new FunctionCallBatch();
		for (FunctionCall functionCall : pendingToolCalls.values()) {
			batch.addCall(functionCall);
		}
		if (batch.getItems().isEmpty()) {
			return;
		}

		assistantMessage.setFunctionCallBatch(batch);
	}

	private FunctionCall parseToolCall(JsonObject toolCallObj) {
		if (toolCallObj == null || !toolCallObj.has("function") || !toolCallObj.get("function").isJsonObject()) {
			return null;
		}

		JsonObject functionDetails = toolCallObj.getAsJsonObject("function");
		if (!functionDetails.has("name") || functionDetails.get("name").isJsonNull()) {
			return null;
		}

		String functionName = functionDetails.get("name").getAsString();
		if (StringUtils.isBlank(functionName)) {
			return null;
		}

		String callId = null;
		if (toolCallObj.has("id") && !toolCallObj.get("id").isJsonNull()) {
			callId = toolCallObj.get("id").getAsString();
		}
		if (StringUtils.isBlank(callId)) {
			callId = UUID.randomUUID().toString();
		}

		JsonElement argumentsElement = functionDetails.get("arguments");
		String argsJsonString = buildArgumentsJson(argumentsElement);
		return new FunctionCall(callId, functionName, argsJsonString);
	}

	private int extractToolCallIndex(JsonObject toolCallObj, int defaultIndex) {
		if (toolCallObj != null && toolCallObj.has("function") && toolCallObj.get("function").isJsonObject()) {
			JsonObject functionDetails = toolCallObj.getAsJsonObject("function");
			if (functionDetails.has("index") && !functionDetails.get("index").isJsonNull()) {
				try {
					return functionDetails.get("index").getAsInt();
				} catch (Exception e) {
					Activator.logError("Invalid Ollama tool_call index: " + functionDetails.get("index"), e);
				}
			}
		}
		return defaultIndex;
	}

	private String buildArgumentsJson(JsonElement argumentsElement) {
		if (argumentsElement == null || argumentsElement.isJsonNull()) {
			return "{}";
		}
		if (argumentsElement.isJsonPrimitive() && argumentsElement.getAsJsonPrimitive().isString()) {
			String serializedArguments = argumentsElement.getAsString();
			if (StringUtils.isBlank(serializedArguments)) {
				return "{}";
			}
			try {
				return gson.toJson(JsonParser.parseString(serializedArguments));
			} catch (Exception e) {
				Activator.logError("Failed to parse Ollama tool arguments: " + serializedArguments, e);
				return "{}";
			}
		}
		return gson.toJson(argumentsElement);
	}

	private JsonElement parseJsonElementOrEmptyObject(String json) {
		if (StringUtils.isBlank(json)) {
			return new JsonObject();
		}
		try {
			JsonElement parsed = JsonParser.parseString(json);
			return parsed != null ? parsed : new JsonObject();
		} catch (Exception e) {
			Activator.logError("Failed to parse tool JSON payload (length=" + json.length() + ")", e);
			return new JsonObject();
		}
	}

	private void finalizeAssistantMessage(ChatMessage assistantMessage, ChatConversation chat,
			AtomicBoolean responseFinished) {
		if (assistantMessage != null && !responseFinished.get()) {
			if (assistantMessage.getFunctionCallBatch().isPresent()) {
				chat.notifyFunctionCalled(assistantMessage);
			}

			chat.notifyChatResponseFinished(assistantMessage);
			responseFinished.set(true);
		}
	}
}
