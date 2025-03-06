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
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * Implementation for OpenAI or compatible API using the chat completion
 * endpoint.
 */
public class OpenAiApiClient implements IAiApiClient {

	private final AiApiConnection apiConnection;
	private transient final Gson gson = new Gson();
	private CompletableFuture<Void> asyncRequest;

	public OpenAiApiClient(AiApiConnection apiConnection) {
		this.apiConnection = apiConnection;
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
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
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
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
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
		// Build the JSON array of messages from the conversation.
		// (We use the messages already in the conversation. We assume the conversation
		// ends with a user message.)
		List<ChatConversation.ChatMessage> messagesToSend = new ArrayList<>(chat.getMessages());
		JsonArray messagesJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : messagesToSend) {
			JsonObject jsonMsg = new JsonObject();

			// Convert the role enum to lowercase string (system, user, assistant).
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

		// Create the JSON request object.
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("max_completion_tokens", maxResponseTokens);
		req.addProperty("stream", true);
		req.add("messages", messagesJson);

		// Add a new (empty) assistant message to the conversation.
		// This is the message that will be updated as new text is streamed in.
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage);

		// Prepare the HTTP request.
		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve("chat/completions"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

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
												chunk = chunk + "<think>\n";
												reasoningStarted.set(true);
											}
										} else {
											chunk = "";
										}

										if (StringUtils.isNotBlank(chunk)) {
											// Append the received chunk to the assistant message.
											assistantMessage.setContent(assistantMessage.getContent() + chunk);
											// Notify the conversation listeners that the assistant message was updated.
											chat.notifyMessageUpdated(assistantMessage);
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
