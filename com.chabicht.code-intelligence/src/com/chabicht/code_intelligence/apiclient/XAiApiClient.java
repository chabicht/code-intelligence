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
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
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

/**
 * Implementation for X.ai API using the chat completion endpoint. Compatible
 * with the OpenAI REST API structure but tailored for X.ai specifics.
 */
public class XAiApiClient extends AbstractApiClient implements IAiApiClient {

	private CompletableFuture<Void> asyncRequest;

	/**
	 * Constructs an XAiApiClient with the provided API connection. The
	 * AiApiConnection should be configured with base URI "https://api.x.ai/v1" and
	 * the X.ai API key.
	 *
	 * @param apiConnection the connection details for the X.ai API
	 */
	public XAiApiClient(AiApiConnection apiConnection) {
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
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath)).GET();
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
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
	private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath,
			U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = "(nothing)";
		try {
			requestBodyString = gson.toJson(requestBody);
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString));
			requestBuilder.header("Content-Type", "application/json");
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
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
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("temperature", completionPrompt.getTemperature());
		req.addProperty("max_completion_tokens", Activator.getDefault().getMaxCompletionTokens());

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

	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonArray messagesJson = buildMessagesJson(chat);

		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);
		req.addProperty("max_completion_tokens", maxResponseTokens); // Corrected parameter name
		req.add("messages", messagesJson);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			ToolProfile profile = (ToolProfile) options.getOrDefault(TOOL_PROFILE, ToolProfile.ALL);
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsXAi(profile));
			req.addProperty("parallel_tool_calls", false);
		}

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req); // For logging
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve("chat/completions"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			Map<Integer, ToolCallInfo> activeToolCalls = new TreeMap<>();

			try {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					response.body().forEach(line -> {
						if (line != null && line.startsWith("data: ")) {
							String data = line.substring("data: ".length()).trim();
							if ("[DONE]".equals(data)) {
								return;
							}
							try {
								JsonObject jsonChunk = JsonParser.parseString(data).getAsJsonObject();
								JsonArray choices = jsonChunk.getAsJsonArray("choices");
								if (choices != null && !choices.isEmpty()) {
									JsonObject choice = choices.get(0).getAsJsonObject(); // Process first choice
									if (choice.has("delta")) {
										JsonObject delta = choice.getAsJsonObject("delta");
										if (delta.has("content") && !delta.get("content").isJsonNull()) {
											String chunk = delta.get("content").getAsString();
											assistantMessage.setContent(assistantMessage.getContent() + chunk);
										}

										if (delta.has("tool_calls")) {
											handleToolCallDelta(delta.getAsJsonArray("tool_calls"), activeToolCalls);
										}
									}

									if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
										String finishReason = choice.get("finish_reason").getAsString();
										// TODO: Consider storing finishReason on assistantMessage if needed by
										// UI/controller after stream
										// e.g., assistantMessage.setLastFinishReason(finishReason);

										if ("tool_calls".equals(finishReason)) {
											finalizeToolCalls(activeToolCalls, assistantMessage, chat);
										}
									}
									chat.notifyMessageUpdated(assistantMessage);
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + data, e);
							}
						}
					});
				} else {
					String errorBody = response.body().collect(Collectors.joining("\n"));
					Activator.logError(String.format("Streaming chat failed with status: %d. Response: %s. Request: %s",
							response.statusCode(), errorBody, requestBody), null);
					assistantMessage
							.setContent("[Error: API request failed with status " + response.statusCode() + "]");
					// TODO: Consider storing "error" as finishReason on assistantMessage if needed
					// e.g., assistantMessage.setLastFinishReason("error");
				}
			} finally {
				chat.notifyChatResponseFinished(assistantMessage);
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat. Request: " + requestBody, e);
			// TODO: Consider storing "exception" as finishReason on assistantMessage if
			// needed
			// e.g., assistantMessage.setLastFinishReason("exception");
			chat.notifyChatResponseFinished(assistantMessage); // Ensure this is called
			asyncRequest = null;
			return null;
		});
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
		}
		for (MessageContext ctx : message.getContext()) {
			contentBuilder.append(ctx.compile());
			contentBuilder.append("\n");
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
		JsonObject toolCallJson = new JsonObject();
		toolCallJson.addProperty("id", functionCall.getId());
		toolCallJson.addProperty("type", "function");

		JsonObject functionJson = new JsonObject();
		functionJson.addProperty("name", functionCall.getFunctionName());
		functionJson.addProperty("arguments", functionCall.getArgsJson());
		toolCallJson.add("function", functionJson);
		return toolCallJson;
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
		JsonObject toolMsg = new JsonObject();
		toolMsg.addProperty("role", "tool");
		toolMsg.addProperty("content", StringUtils.defaultString(functionResult.getResultJson()));
		toolMsg.addProperty("tool_call_id", functionResult.getId());
		return toolMsg;
	}

	private void handleToolCallDelta(JsonArray toolCallDeltas, Map<Integer, ToolCallInfo> activeToolCalls) {
		if (toolCallDeltas == null) {
			return;
		}
		for (int i = 0; i < toolCallDeltas.size(); i++) {
			JsonElement toolCallElement = toolCallDeltas.get(i);
			if (!toolCallElement.isJsonObject()) {
				continue;
			}

			JsonObject toolCallDelta = toolCallElement.getAsJsonObject();
			int index = toolCallDelta.has("index") && !toolCallDelta.get("index").isJsonNull()
					? toolCallDelta.get("index").getAsInt()
					: i;
			ToolCallInfo toolCallInfo = activeToolCalls.computeIfAbsent(index, ToolCallInfo::new);

			if (toolCallDelta.has("id") && !toolCallDelta.get("id").isJsonNull()) {
				toolCallInfo.setId(toolCallDelta.get("id").getAsString());
			}
			if (toolCallDelta.has("function") && toolCallDelta.get("function").isJsonObject()) {
				JsonObject functionChunk = toolCallDelta.getAsJsonObject("function");
				if (functionChunk.has("name") && !functionChunk.get("name").isJsonNull()) {
					toolCallInfo.setName(functionChunk.get("name").getAsString());
				}
				if (functionChunk.has("arguments") && !functionChunk.get("arguments").isJsonNull()) {
					toolCallInfo.appendArguments(functionChunk.get("arguments").getAsString());
				}
			}
		}
	}

	private void finalizeToolCalls(Map<Integer, ToolCallInfo> activeToolCalls, ChatMessage assistantMessage,
			ChatConversation chat) {
		if (activeToolCalls == null || activeToolCalls.isEmpty()) {
			Activator.logWarn("Finish reason was 'tool_calls' but tool call data was incomplete.");
			return;
		}

		FunctionCallBatch batch = new FunctionCallBatch();
		for (ToolCallInfo toolCall : activeToolCalls.values()) {
			FunctionCall functionCall = toolCall.toFunctionCall();
			if (functionCall != null) {
				batch.addCall(functionCall);
			}
		}
		if (batch.getItems().isEmpty()) {
			Activator.logWarn("Finish reason was 'tool_calls' but tool call data was incomplete.");
			return;
		}

		assistantMessage.setFunctionCallBatch(batch);
		assistantMessage.setFunctionCall(batch.getItems().get(0).getCall());
		chat.notifyFunctionCalled(assistantMessage);
		activeToolCalls.clear();
	}

	private static class ToolCallInfo {
		private final int index;
		private String id;
		private String name;
		private final StringBuilder arguments = new StringBuilder();

		private ToolCallInfo(int index) {
			this.index = index;
		}

		private void setId(String id) {
			this.id = id;
		}

		private void setName(String name) {
			this.name = name;
		}

		private void appendArguments(String chunk) {
			if (chunk != null) {
				arguments.append(chunk);
			}
		}

		private FunctionCall toFunctionCall() {
			if (StringUtils.isBlank(id) || StringUtils.isBlank(name)) {
				return null;
			}
			return new FunctionCall(id, name, arguments.toString());
		}

		@Override
		public String toString() {
			return "ToolCallInfo[index=" + index + ", id=" + id + ", name=" + name + "]";
		}
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("temperature", 1);

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
}
