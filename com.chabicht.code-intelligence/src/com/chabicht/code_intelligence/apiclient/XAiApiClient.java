package com.chabicht.code_intelligence.apiclient;

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
import com.google.gson.JsonSyntaxException;

/**
 * Implementation for X.ai API using the chat completion endpoint. Compatible
 * with the OpenAI REST API structure but tailored for X.ai specifics.
 */
public class XAiApiClient extends AbstractApiClient implements IAiApiClient {

	private transient final Gson gson = Activator.getDefault().createGson();
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
		// Build the JSON array of messages from the conversation
		JsonArray messagesJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : chat.getMessages()) { // Iterate directly
			JsonObject jsonMsg = new JsonObject();
			String roleName = msg.getRole().toString().toLowerCase();
			jsonMsg.addProperty("role", roleName);

			StringBuilder contentBuilder = new StringBuilder(256);
			// Build content for every message type
			if (!msg.getContext().isEmpty()) {
				contentBuilder.append("Context information:\n\n");
			}
			for (MessageContext ctx : msg.getContext()) {
				contentBuilder.append(ctx.compile());
				contentBuilder.append("\n");
			}
			contentBuilder.append(msg.getContent());
			jsonMsg.addProperty("content", contentBuilder.toString());

			// Add function calls for assistant messages
			if (msg.getRole() == Role.ASSISTANT && msg.getFunctionCall().isPresent()) {
				JsonArray toolCallsArray = new JsonArray();
				JsonObject toolCallJson = new JsonObject();
				FunctionCall fc = msg.getFunctionCall().get();
				toolCallJson.addProperty("id", fc.getId());
				toolCallJson.addProperty("type", "function");
				JsonObject functionJson = new JsonObject();
				functionJson.addProperty("name", fc.getFunctionName());
				functionJson.addProperty("arguments", fc.getArgsJson());
				toolCallJson.add("function", functionJson);
				toolCallsArray.add(toolCallJson);
				jsonMsg.add("tool_calls", toolCallsArray);
			}

			messagesJson.add(jsonMsg);

			// If this message has a function result, add it as a separate tool message
			if (msg.getRole() == Role.ASSISTANT && msg.getFunctionCall().isPresent()
					&& msg.getFunctionResult().isPresent()) {
				FunctionResult fr = msg.getFunctionResult().get();
				if (StringUtils.isNotBlank(fr.getId())) {
					// Create a new tool message for the function result
					JsonObject toolMsg = new JsonObject();
					toolMsg.addProperty("role", "tool");
					toolMsg.addProperty("content", fr.getResultJson());
					toolMsg.addProperty("tool_call_id", fr.getId());
					messagesJson.add(toolMsg);
				}
			}
		}

		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);
		req.addProperty("max_completion_tokens", maxResponseTokens); // Corrected parameter name
		req.add("messages", messagesJson);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsXAi());
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
			final AtomicReference<String> tempToolCallId = new AtomicReference<>();
			final AtomicReference<String> tempToolCallName = new AtomicReference<>();
			final StringBuilder tempToolCallArgs = new StringBuilder();

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
											JsonArray toolCallsDelta = delta.getAsJsonArray("tool_calls");
											if (toolCallsDelta != null && !toolCallsDelta.isEmpty()) {
												JsonObject toolCallChunk = toolCallsDelta.get(0).getAsJsonObject(); // Process
																													// first
																													// tool
																													// call
												if (toolCallChunk.has("id")) {
													tempToolCallId.set(toolCallChunk.get("id").getAsString());
												}
												// type is assumed "function" by X.ai and not explicitly stored in our
												// FunctionCall model
												if (toolCallChunk.has("function")) {
													JsonObject functionChunk = toolCallChunk
															.getAsJsonObject("function");
													if (functionChunk.has("name")) {
														tempToolCallName.set(functionChunk.get("name").getAsString());
													}
													if (functionChunk.has("arguments")) {
														tempToolCallArgs
																.append(functionChunk.get("arguments").getAsString());
													}
												}
											}
										}
									}

									if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
										String finishReason = choice.get("finish_reason").getAsString();
										// TODO: Consider storing finishReason on assistantMessage if needed by
										// UI/controller after stream
										// e.g., assistantMessage.setLastFinishReason(finishReason);

										if ("tool_calls".equals(finishReason)) {
											if (tempToolCallId.get() != null && tempToolCallName.get() != null) {
												if (assistantMessage.getFunctionCall().isPresent()) {
													Activator.logWarn(
															"Model attempted to make multiple tool calls. Processing only the first one: "
																	+ tempToolCallId.get());
												} else {
													FunctionCall actualFc = new FunctionCall(tempToolCallId.get(),
															tempToolCallName.get(), tempToolCallArgs.toString());
													assistantMessage.setFunctionCall(actualFc); // Uses the
																								// setFunctionCall(FunctionCall)
																								// overload
													chat.notifyFunctionCalled(assistantMessage);
												}
											} else {
												Activator.logWarn(
														"Finish reason was 'tool_calls' but tool call data was incomplete.");
											}
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