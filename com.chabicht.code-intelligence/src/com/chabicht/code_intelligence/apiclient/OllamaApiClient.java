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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class OllamaApiClient implements IAiApiClient {

	private final AiApiConnection apiConnection;
	private transient final Gson gson = new Gson();
	private CompletableFuture<Void> asyncRequest;

	public OllamaApiClient(AiApiConnection apiConnection) {
		this.apiConnection = apiConnection;
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
			U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = gson.toJson(requestBody);
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
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("prompt", completionPrompt.compile());
		JsonObject options = new JsonObject();
		options.addProperty("temperature", completionPrompt.getTemperature());
		options.addProperty("num_ctx", 8192);
		options.addProperty("num_predict", Activator.getDefault().getMaxCompletionTokens());
		req.add("options", options);
		req.addProperty("stream", false);

		JsonObject res = performPost(JsonObject.class, "api/generate", req);

		return new CompletionResult(res.get("response").getAsString());
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
	 * @param modelName the model to use (for example, "llama3.2")
	 * @param chat      the ChatConversation object containing the conversation so
	 *                  far
	 */
	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		// Build the JSON array of messages from the conversation.
		// We assume the conversation ends with a user message.
		List<ChatConversation.ChatMessage> messagesToSend = new ArrayList<>(chat.getMessages());
		JsonArray messagesJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : messagesToSend) {
			JsonObject jsonMsg = new JsonObject();

			// Convert the role to lowercase (e.g. "system", "user", "assistant").
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());

			StringBuilder content = new StringBuilder(256);
			if (!msg.getContext().isEmpty()) {
				content.append("Context information:\n\n");
			}
			for (MessageContext ctx : msg.getContext()) {
				content.append(ctx.compile());
				content.append("\n");
			}
			content.append(msg.getContent());
			jsonMsg.addProperty("content", content.toString());

			messagesJson.add(jsonMsg);
		}

		// Create the JSON request object for Ollama.
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("stream", true);
		req.add("messages", messagesJson);

		JsonObject options = new JsonObject();
		options.addProperty("num_ctx", 8192);
		options.addProperty("num_predict", maxResponseTokens);
		req.add("options", options);

		// Add a new (empty) assistant message to the conversation.
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage);

		// Prepare the HTTP request.
		String requestBody = gson.toJson(req);
		// Use HTTP/1.1 client with a short connection timeout.
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.ALWAYS).build();

		// The Ollama streaming chat endpoint is at "/api/chat"
		URI endpoint = URI.create(apiConnection.getBaseUri()).resolve("/api/chat");
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(endpoint)
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		// Optionally add an authorization header if your apiConnection includes an API
		// key.
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		// Send the request asynchronously and process the streamed response
		// line-by-line.
		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					response.body().forEach(line -> {
						// Each line is expected to be a JSON object.
						if (line != null && !line.trim().isEmpty()) {
							try {
								JsonObject jsonChunk = JsonParser.parseString(line).getAsJsonObject();
								// The Ollama chat endpoint returns a JSON object with a "message" field.
								// That "message" object should contain a "content" field that holds the new
								// text.
								if (jsonChunk.has("message")) {
									JsonObject messageObj = jsonChunk.getAsJsonObject("message");
									if (messageObj.has("content")) {
										String chunk = messageObj.get("content").getAsString();
										// Append the received chunk to the assistant message.
										assistantMessage.setContent(assistantMessage.getContent() + chunk);
										// Notify the conversation listeners that the assistant message was updated.
										chat.notifyMessageUpdated(assistantMessage);
									}
								}
								// Optionally, if the response includes a "done" flag that is true, you can
								// finish early.
								if (jsonChunk.has("done") && jsonChunk.get("done").getAsBoolean()) {
									// End of stream.
									return;
								}
							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + line, e);
							}
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode(), null);
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

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("prompt", content);
		JsonObject options = new JsonObject();
		options.addProperty("temperature", 1);
		options.addProperty("num_ctx", 8192);
		req.add("options", options);
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
