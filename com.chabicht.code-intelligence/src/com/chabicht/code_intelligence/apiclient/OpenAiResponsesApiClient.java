package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOLS_ENABLED;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOL_PROFILE;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
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
import com.google.gson.JsonSyntaxException;

public class OpenAiResponsesApiClient extends AbstractApiClient implements IAiApiClient {

	private static final String RESPONSES_REL_PATH = "responses";
	private static final String META_OPENAI_RESPONSE_ID = "openai_response_id";

	private static final Set<String> INCOMPATIBLE_PRESET_KEYS = Set.of("messages", "functions", "function_call",
			"max_completion_tokens", "stream_options");

	private transient final Gson gson = Activator.getDefault().createGson();
	private CompletableFuture<Void> asyncRequest;

	public OpenAiResponsesApiClient(AiApiConnection apiConnection) {
		super(apiConnection);
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "models");
		if (!res.has("data") || !res.get("data").isJsonArray()) {
			return List.of();
		}
		return res.getAsJsonArray("data").asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			return new AiModel(apiConnection, id, id);
		}).collect(Collectors.toList());
	}

	@Override
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = sanitizePresetForResponses(createFromPresets(PromptType.INSTRUCT));
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", completionPrompt.getTemperature());
		setPropertyIfNotPresent(req, "max_output_tokens", Activator.getDefault().getMaxCompletionTokens());

		JsonArray input = new JsonArray();
		input.add(buildUserMessageItem(completionPrompt.compile()));
		req.add("input", input);

		JsonObject res = performPost(JsonObject.class, RESPONSES_REL_PATH, req);
		return new CompletionResult(extractOutputText(res));
	}

	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);
		performChat(modelName, chat, maxResponseTokens, assistantMessage, false);
	}

	private void performChat(String modelName, ChatConversation chat, int maxResponseTokens,
			ChatConversation.ChatMessage assistantMessage, boolean retryWithoutPreviousResponseId) {
		ChatRequestBuildResult buildResult = buildResponsesChatRequest(modelName, chat, maxResponseTokens,
				!retryWithoutPreviousResponseId);
		String requestBody = gson.toJson(buildResult.requestBody());

		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
				.followRedirects(Redirect.ALWAYS).build();
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(RESPONSES_REL_PATH))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		AtomicReference<String> currentEvent = new AtomicReference<>("");
		ToolCallAccumulator toolCallAccumulator = new ToolCallAccumulator();
		AtomicBoolean retryStarted = new AtomicBoolean(false);

		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					response.body().forEach(line -> {
						if (line == null || line.isBlank()) {
							return;
						}
						if (line.startsWith("event: ")) {
							currentEvent.set(line.substring("event: ".length()).trim());
							return;
						}
						if (!line.startsWith("data: ")) {
							return;
						}

						String data = line.substring("data: ".length()).trim();
						if ("[DONE]".equals(data)) {
							return;
						}
						try {
							JsonObject payload = JsonParser.parseString(data).getAsJsonObject();
							handleStreamingEvent(currentEvent.get(), payload, assistantMessage, chat, toolCallAccumulator);
						} catch (JsonSyntaxException e) {
							Activator.logWarn("Could not parse streaming payload: " + data);
						} catch (Exception e) {
							Activator.logError("Error handling responses stream event:\n" + data, e);
						}
					});
				} else {
					String body = response.body().collect(Collectors.joining("\n"));
					if (buildResult.usedPreviousResponseId() && !retryWithoutPreviousResponseId
							&& isPreviousResponseNotFound(body)) {
						Activator.logWarn("Retrying /responses chat once without previous_response_id.");
						retryStarted.set(true);
						performChat(modelName, chat, maxResponseTokens, assistantMessage, true);
						return;
					}

					Activator.logError("Streaming chat failed with status: " + response.statusCode() + "\n" + body
							+ "\n\nRequest JSON:\n" + requestBody, null);
				}
			} finally {
				if (!retryStarted.get()) {
					toolCallAccumulator.finalizeIfComplete(assistantMessage, chat);
					chat.notifyChatResponseFinished(assistantMessage);
					asyncRequest = null;
				}
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat request", e);
			if (!retryStarted.get()) {
				toolCallAccumulator.finalizeIfComplete(assistantMessage, chat);
				chat.notifyChatResponseFinished(assistantMessage);
				asyncRequest = null;
			}
			return null;
		});
	}

	@Override
	public void abortChat() {
		if (asyncRequest != null) {
			asyncRequest.cancel(true);
			asyncRequest = null;
		}
	}

	@Override
	public boolean isChatPending() {
		return asyncRequest != null;
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = sanitizePresetForResponses(createFromPresets(PromptType.INSTRUCT));
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", 1);
		setPropertyIfNotPresent(req, "max_output_tokens", Activator.getDefault().getMaxCompletionTokens());

		JsonArray input = new JsonArray();
		input.add(buildUserMessageItem(content));
		req.add("input", input);

		JsonObject res = performPost(JsonObject.class, RESPONSES_REL_PATH, req);
		return extractOutputText(res);
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
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
			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (Exception e) {
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
	private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath, U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = "(nothing)";
		try {
			requestBodyString = gson.toJson(requestBody);
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString)).header("Content-Type", "application/json");
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
			}
			HttpRequest request = requestBuilder.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			statusCode = response.statusCode();
			responseBody = response.body();
			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (Exception e) {
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

	private ChatRequestBuildResult buildResponsesChatRequest(String modelName, ChatConversation chat, int maxResponseTokens,
			boolean allowPreviousResponseId) {
		JsonObject req = sanitizePresetForResponses(createFromPresets(PromptType.CHAT));
		req.addProperty("model", modelName);
		req.addProperty("stream", true);
		if (maxResponseTokens > 0 && !req.has("max_output_tokens")) {
			req.addProperty("max_output_tokens", maxResponseTokens);
		}
		req.addProperty("parallel_tool_calls", false);

		String instructions = findSystemInstructions(chat);
		if (StringUtils.isNotBlank(instructions)) {
			req.addProperty("instructions", instructions);
		}

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			ToolProfile profile = (ToolProfile) options.getOrDefault(TOOL_PROFILE, ToolProfile.READ_WRITE);
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsOpenAi(profile));
			normalizeToolDefinitionsForResponses(req);
		}
		req.addProperty("parallel_tool_calls", false);

		String previousResponseId = allowPreviousResponseId ? findLatestResponseId(chat) : null;
		JsonArray input;
		boolean usedPreviousResponseId = false;
		if (StringUtils.isNotBlank(previousResponseId)) {
			input = buildIncrementalInputItems(chat, previousResponseId);
			if (!input.isEmpty()) {
				req.addProperty("previous_response_id", previousResponseId);
				usedPreviousResponseId = true;
			} else {
				input = buildInputItemsForConversation(chat);
			}
		} else {
			input = buildInputItemsForConversation(chat);
		}

		req.add("input", input);
		return new ChatRequestBuildResult(req, usedPreviousResponseId);
	}

	private JsonArray buildIncrementalInputItems(ChatConversation chat, String previousResponseId) {
		JsonArray input = new JsonArray();
		List<ChatMessage> messages = chat.getMessages();
		int startIndex = -1;
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage message = messages.get(i);
			if (Role.ASSISTANT.equals(message.getRole())
					&& StringUtils.equals(previousResponseId, Objects.toString(message.getMetadata(META_OPENAI_RESPONSE_ID), null))) {
				startIndex = i;
				break;
			}
		}

		if (startIndex < 0) {
			return input;
		}

		for (int i = startIndex; i < messages.size(); i++) {
			ChatMessage message = messages.get(i);
			if (Role.USER.equals(message.getRole())) {
				input.add(buildUserMessageItem(compileMessageContent(message)));
			}
			if (message.getFunctionResult().isPresent()) {
				input.add(buildFunctionCallOutputItem(message.getFunctionResult().get()));
			}
		}
		return input;
	}

	private JsonArray buildInputItemsForConversation(ChatConversation chat) {
		JsonArray input = new JsonArray();
		for (ChatMessage message : chat.getMessages()) {
			if (Role.SYSTEM.equals(message.getRole()) || Role.TOOL_SUMMARY.equals(message.getRole())) {
				continue;
			}
			if (Role.USER.equals(message.getRole())) {
				input.add(buildUserMessageItem(compileMessageContent(message)));
			} else if (Role.ASSISTANT.equals(message.getRole())) {
				String assistantText = StringUtils.trimToEmpty(message.getContent());
				if (StringUtils.isNotBlank(assistantText)) {
					input.add(buildMessageItem("assistant", assistantText));
				}
			}
			if (message.getFunctionResult().isPresent()) {
				input.add(buildFunctionCallOutputItem(message.getFunctionResult().get()));
			}
		}
		return input;
	}

	private JsonObject buildUserMessageItem(String text) {
		return buildMessageItem("user", text);
	}

	private JsonObject buildMessageItem(String role, String text) {
		JsonObject item = new JsonObject();
		item.addProperty("type", "message");
		item.addProperty("role", role);
		JsonArray content = new JsonArray();
		JsonObject contentItem = new JsonObject();
		contentItem.addProperty("type", "input_text");
		contentItem.addProperty("text", StringUtils.defaultString(text));
		content.add(contentItem);
		item.add("content", content);
		return item;
	}

	private JsonObject buildFunctionCallOutputItem(FunctionResult functionResult) {
		JsonObject item = new JsonObject();
		item.addProperty("type", "function_call_output");
		item.addProperty("call_id", functionResult.getId());
		item.addProperty("output", StringUtils.defaultString(functionResult.getResultJson()));
		return item;
	}

	private JsonObject sanitizePresetForResponses(JsonObject source) {
		JsonObject preset = source == null ? new JsonObject() : source.deepCopy();
		for (String key : INCOMPATIBLE_PRESET_KEYS) {
			preset.remove(key);
		}
		return preset;
	}

	private void normalizeToolDefinitionsForResponses(JsonObject req) {
		if (!req.has("tools") || !req.get("tools").isJsonArray()) {
			return;
		}
		JsonArray normalizedTools = new JsonArray();
		for (JsonElement toolElement : req.getAsJsonArray("tools")) {
			if (!toolElement.isJsonObject()) {
				continue;
			}
			JsonObject tool = toolElement.getAsJsonObject();
			if (!tool.has("type") || tool.get("type").isJsonNull()
					|| !"function".equals(tool.get("type").getAsString())) {
				normalizedTools.add(tool);
				continue;
			}

			JsonObject normalizedTool = new JsonObject();
			normalizedTool.addProperty("type", "function");
			if (tool.has("function") && tool.get("function").isJsonObject()) {
				JsonObject function = tool.getAsJsonObject("function");
				copyIfPresent(function, normalizedTool, "name");
				copyIfPresent(function, normalizedTool, "description");
				copyIfPresent(function, normalizedTool, "parameters");
				copyIfPresent(function, normalizedTool, "strict");
			} else {
				copyIfPresent(tool, normalizedTool, "name");
				copyIfPresent(tool, normalizedTool, "description");
				copyIfPresent(tool, normalizedTool, "parameters");
				copyIfPresent(tool, normalizedTool, "strict");
			}
			normalizedTools.add(normalizedTool);
		}
		req.add("tools", normalizedTools);
	}

	private void copyIfPresent(JsonObject source, JsonObject target, String key) {
		if (source.has(key) && !source.get(key).isJsonNull()) {
			target.add(key, source.get(key).deepCopy());
		}
	}

	private String findSystemInstructions(ChatConversation chat) {
		for (ChatMessage message : chat.getMessages()) {
			if (Role.SYSTEM.equals(message.getRole())) {
				return message.getContent();
			}
		}
		return null;
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

	private void handleStreamingEvent(String currentEventName, JsonObject payload, ChatMessage assistantMessage,
			ChatConversation chat, ToolCallAccumulator toolCallAccumulator) {
		String type = payload.has("type") && !payload.get("type").isJsonNull() ? payload.get("type").getAsString()
				: currentEventName;

		if ("response.output_text.delta".equals(type)) {
			if (payload.has("delta") && !payload.get("delta").isJsonNull()) {
				assistantMessage.setContent(assistantMessage.getContent() + payload.get("delta").getAsString());
				chat.notifyMessageUpdated(assistantMessage);
			}
			return;
		}

		if ("response.function_call_arguments.delta".equals(type)) {
			if (payload.has("delta") && !payload.get("delta").isJsonNull()) {
				toolCallAccumulator.appendArguments(payload.get("delta").getAsString());
			}
			return;
		}

		if ("response.function_call_arguments.done".equals(type)) {
			toolCallAccumulator.applyDoneEvent(payload);
			toolCallAccumulator.finalizeIfComplete(assistantMessage, chat);
			return;
		}

		if ("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) {
			if (payload.has("item") && payload.get("item").isJsonObject()) {
				toolCallAccumulator.applyOutputItem(payload.getAsJsonObject("item"));
				toolCallAccumulator.finalizeIfComplete(assistantMessage, chat);
			}
			return;
		}

		if ("response.completed".equals(type)) {
			String responseId = extractResponseId(payload);
			if (StringUtils.isNotBlank(responseId)) {
				assistantMessage.setMetadata(META_OPENAI_RESPONSE_ID, responseId);
			}
			return;
		}

		if ("response.failed".equals(type) || "error".equals(type)) {
			Activator.logWarn("Responses stream failure event: " + payload);
			return;
		}

		if (StringUtils.isNotBlank(type) && type.startsWith("response.")) {
			return;
		}
		if (StringUtils.isNotBlank(type)) {
			Activator.logInfo("Ignoring unknown responses stream event: " + type);
		}
	}

	private String extractResponseId(JsonObject payload) {
		if (payload.has("response") && payload.get("response").isJsonObject()) {
			JsonObject response = payload.getAsJsonObject("response");
			if (response.has("id") && !response.get("id").isJsonNull()) {
				return response.get("id").getAsString();
			}
		}
		if (payload.has("id") && !payload.get("id").isJsonNull()) {
			return payload.get("id").getAsString();
		}
		return null;
	}

	private String findLatestResponseId(ChatConversation chat) {
		List<ChatMessage> messages = chat.getMessages();
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage message = messages.get(i);
			if (Role.ASSISTANT.equals(message.getRole())) {
				Object value = message.getMetadata(META_OPENAI_RESPONSE_ID);
				if (value != null) {
					String responseId = String.valueOf(value);
					if (StringUtils.isNotBlank(responseId)) {
						return responseId;
					}
				}
			}
		}
		return null;
	}

	private boolean isPreviousResponseNotFound(String body) {
		if (StringUtils.isBlank(body)) {
			return false;
		}
		return body.contains("previous_response_not_found");
	}

	private String extractOutputText(JsonObject response) {
		if (response.has("output_text") && !response.get("output_text").isJsonNull()) {
			JsonElement outputText = response.get("output_text");
			if (outputText.isJsonPrimitive()) {
				return outputText.getAsString();
			}
		}

		List<String> chunks = new ArrayList<>();
		if (response.has("output") && response.get("output").isJsonArray()) {
			JsonArray output = response.getAsJsonArray("output");
			for (JsonElement outputItemElement : output) {
				if (!outputItemElement.isJsonObject()) {
					continue;
				}
				JsonObject outputItem = outputItemElement.getAsJsonObject();
				if (!outputItem.has("content") || !outputItem.get("content").isJsonArray()) {
					continue;
				}
				for (JsonElement contentElement : outputItem.getAsJsonArray("content")) {
					if (!contentElement.isJsonObject()) {
						continue;
					}
					JsonObject contentObject = contentElement.getAsJsonObject();
					if (!contentObject.has("type") || contentObject.get("type").isJsonNull()) {
						continue;
					}
					if (!StringUtils.equals("output_text", contentObject.get("type").getAsString())) {
						continue;
					}
					if (contentObject.has("text") && !contentObject.get("text").isJsonNull()) {
						chunks.add(contentObject.get("text").getAsString());
					}
				}
			}
		}
		return String.join("", chunks);
	}

	private static final class ToolCallAccumulator {
		private String callId;
		private String functionName;
		private final StringBuilder argumentsBuilder = new StringBuilder();
		private boolean finalized;
		private boolean warnedAboutMultipleCalls;

		private void applyOutputItem(JsonObject item) {
			if (!item.has("type") || item.get("type").isJsonNull()) {
				return;
			}
			String itemType = item.get("type").getAsString();
			if (!"function_call".equals(itemType)) {
				return;
			}

			String nextCallId = getString(item, "call_id");
			String nextFunctionName = getString(item, "name");
			if (StringUtils.isNotBlank(callId) && StringUtils.isNotBlank(nextCallId)
					&& !StringUtils.equals(callId, nextCallId) && !warnedAboutMultipleCalls) {
				Activator.logWarn("Received multiple function calls in one assistant turn. Only the first is supported.");
				warnedAboutMultipleCalls = true;
				return;
			}
			if (StringUtils.isBlank(callId) && StringUtils.isNotBlank(nextCallId)) {
				callId = nextCallId;
			}
			if (StringUtils.isBlank(functionName) && StringUtils.isNotBlank(nextFunctionName)) {
				functionName = nextFunctionName;
			}
			String arguments = getString(item, "arguments");
			if (StringUtils.isNotBlank(arguments) && argumentsBuilder.length() == 0) {
				argumentsBuilder.append(arguments);
			}
		}

		private void appendArguments(String delta) {
			if (delta != null) {
				argumentsBuilder.append(delta);
			}
		}

		private void applyDoneEvent(JsonObject payload) {
			String doneCallId = getString(payload, "call_id");
			String doneFunctionName = getString(payload, "name");
			String doneArguments = getString(payload, "arguments");
			if (StringUtils.isBlank(callId) && StringUtils.isNotBlank(doneCallId)) {
				callId = doneCallId;
			}
			if (StringUtils.isBlank(functionName) && StringUtils.isNotBlank(doneFunctionName)) {
				functionName = doneFunctionName;
			}
			if (StringUtils.isNotBlank(doneArguments) && argumentsBuilder.length() == 0) {
				argumentsBuilder.append(doneArguments);
			}
		}

		private void finalizeIfComplete(ChatMessage assistantMessage, ChatConversation chat) {
			if (finalized || StringUtils.isBlank(callId) || StringUtils.isBlank(functionName)) {
				return;
			}
			assistantMessage.setFunctionCall(new FunctionCall(callId, functionName, argumentsBuilder.toString()));
			chat.notifyFunctionCalled(assistantMessage);
			finalized = true;
		}

		private static String getString(JsonObject object, String key) {
			if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
				return null;
			}
			return object.get(key).getAsString();
		}
	}

	private static final class ChatRequestBuildResult {
		private final JsonObject requestBody;
		private final boolean usedPreviousResponseId;

		private ChatRequestBuildResult(JsonObject requestBody, boolean usedPreviousResponseId) {
			this.requestBody = requestBody;
			this.usedPreviousResponseId = usedPreviousResponseId;
		}

		private JsonObject requestBody() {
			return requestBody;
		}

		private boolean usedPreviousResponseId() {
			return usedPreviousResponseId;
		}
	}
}
