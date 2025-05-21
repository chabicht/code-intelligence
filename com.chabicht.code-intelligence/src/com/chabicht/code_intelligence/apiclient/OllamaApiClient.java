package com.chabicht.code_intelligence.apiclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // Added import for UUID
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
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

	private CompletableFuture<Void> asyncRequest;

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

	@SuppressWarnings("unchecked")
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath)).GET().build();

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
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
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
		setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
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
		List<ChatConversation.ChatMessage> messagesToSend = new ArrayList<>(chat.getMessages());
		JsonArray messagesJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : messagesToSend) {
			JsonObject jsonMsg = new JsonObject();
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());

			StringBuilder content = new StringBuilder(256);
			if (!msg.getContext().isEmpty()) {
				content.append("Context information:\n\n");
			}
			for (MessageContext ctx : msg.getContext()) {
				content.append(ctx.compile(true));
				content.append("\n");
			}
			content.append(msg.getContent());
			jsonMsg.addProperty("content", content.toString());
			messagesJson.add(jsonMsg);

			// NEW: Add role: "tool" message if the current message was an ASSISTANT
			// message that had a FunctionCall and now has a FunctionResult.
			if (msg.getRole() == ChatConversation.Role.ASSISTANT && msg.getFunctionCall().isPresent()
					&& msg.getFunctionResult().isPresent()) {

				ChatConversation.FunctionCall originalFc = msg.getFunctionCall().get();
				ChatConversation.FunctionResult fr = msg.getFunctionResult().get();

				if (!originalFc.getId().equals(fr.getId())) {
					Activator.logError("Mismatch between FunctionCall ID (" + originalFc.getId()
							+ ") and FunctionResult ID (" + fr.getId() + ") for function " + fr.getFunctionName(),
							null);
				}

				JsonObject toolResultMsgJson = new JsonObject();
				toolResultMsgJson.addProperty("role", "tool");
				toolResultMsgJson.addProperty("content", fr.getResultJson());
				toolResultMsgJson.addProperty("name", fr.getFunctionName());
				messagesJson.add(toolResultMsgJson);
			}
		}

		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("stream", true);
		req.add("messages", messagesJson);

		patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsOllama());

		JsonObject options = getOrAddJsonObject(req, "options");
		setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
		setPropertyIfNotPresent(options, "num_predict", maxResponseTokens);

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.ALWAYS).build();

		URI endpoint = URI.create(apiConnection.getBaseUri()).resolve("/api/chat");
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(endpoint)
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");

		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		final AtomicBoolean responseFinished = new AtomicBoolean(false);

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
										JsonArray toolCallsArray = messageObj.getAsJsonArray("tool_calls");
										if (toolCallsArray != null && !toolCallsArray.isEmpty()) {
											if (toolCallsArray.size() > 1) {
												Activator.logError(
														"Ollama API returned multiple tool_calls in a single message. Processing only the first.",
														null);
											}
											JsonObject toolCallObj = toolCallsArray.get(0).getAsJsonObject();
											JsonObject functionDetails = toolCallObj.getAsJsonObject("function");
											String functionName = functionDetails.get("name").getAsString();
											JsonObject argumentsObj = functionDetails.getAsJsonObject("arguments");
											String argsJsonString = gson.toJson(argumentsObj);
											String clientGeneratedCallId = UUID.randomUUID().toString();
											ChatConversation.FunctionCall functionCall = new ChatConversation.FunctionCall(
													clientGeneratedCallId, functionName, argsJsonString);
											assistantMessage.setFunctionCall(functionCall);
											chat.notifyFunctionCalled(assistantMessage);
										}
									}

									// Handle content
									if (messageObj.has("content")) {
										String chunk = messageObj.get("content").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + chunk);
										chat.notifyMessageUpdated(assistantMessage);
									}
								}
								if (jsonChunk.has("done") && jsonChunk.get("done").getAsBoolean()) {
									if (!responseFinished.get()) {
										chat.notifyChatResponseFinished(assistantMessage);
										responseFinished.set(true);
									}
									return; // End of stream for this line processor
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + line, e);
								if (!responseFinished.get()) {
									chat.notifyChatResponseFinished(assistantMessage);
									responseFinished.set(true);
								}
								asyncRequest = null;
							}
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode()
							+ "\nResponse body: " + response.body().collect(Collectors.joining("\n")), null);
					if (!responseFinished.get()) {
						chat.notifyChatResponseFinished(assistantMessage);
						responseFinished.set(true);
					}
					asyncRequest = null;
				}
			} finally {
				if (!responseFinished.get()) {
					chat.notifyChatResponseFinished(assistantMessage);
					responseFinished.set(true);
				}
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat", e);
			// Ensure assistant message is finalized in case of error before stream
			// completion
			if (assistantMessage != null && !responseFinished.get()) { // Check the flag
				chat.notifyChatResponseFinished(assistantMessage);
				responseFinished.set(true); // Set the flag
			}
			asyncRequest = null;
			return null;

		});
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.addProperty("prompt", content);
		JsonObject options = getOrAddJsonObject(req, "options");
		setPropertyIfNotPresent(options, "temperature", 1);
		setPropertyIfNotPresent(options, NUM_CTX, DEFAULT_CONTEXT_SIZE);
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
}
