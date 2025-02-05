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

public class GoogleApiClient implements IAiApiClient {

	private final AiApiConnection apiConnection;
	private transient final Gson gson = new Gson();
	private CompletableFuture<Void> asyncRequest;

	public GoogleApiClient(AiApiConnection apiConnection) {
		this.apiConnection = apiConnection;
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "models");
		return res.get("models").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String name = o.get("name").getAsString();
			String displayName = o.has("displayName") ? o.get("displayName").getAsString() : name;
			return new AiModel(apiConnection, name, displayName);
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
					.uri(URI.create(apiConnection.getBaseUri() + "/v1beta/")
							.resolve(relPath + "?key=" + apiConnection.getApiKey()))
					.GET();

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
					Method: GET
					Status code: %d
					Response:
					%s
					""", apiConnection.getBaseUri() + "/v1beta/" + relPath, statusCode, responseBody), e);
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
					.uri(URI.create(apiConnection.getBaseUri() + "/v1beta/")
							.resolve(relPath + "?key=" + apiConnection.getApiKey()))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString));
			requestBuilder.header("Content-Type", "application/json");

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
					""", apiConnection.getBaseUri() + "/v1beta/" + relPath, statusCode, requestBodyString,
					responseBody), e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = new JsonObject();
		JsonObject content = new JsonObject();
		JsonArray parts = new JsonArray();
		JsonObject part = new JsonObject();
		part.addProperty("text", completionPrompt.compile());
		parts.add(part);
		content.add("parts", parts);
		JsonArray contents = new JsonArray();
		contents.add(content);
		req.add("contents", contents);

		JsonObject generationConfig = new JsonObject();
		generationConfig.addProperty("temperature", completionPrompt.getTemperature());
		req.add("generationConfig", generationConfig);

		JsonObject res = performPost(JsonObject.class, "models/" + modelName + ":generateContent", req);
		JsonArray candidates = res.getAsJsonArray("candidates");
		if (candidates != null && !candidates.isEmpty()) {
			JsonObject candidate = candidates.get(0).getAsJsonObject();
			JsonObject contentRes = candidate.getAsJsonObject("content");
			JsonArray partsRes = contentRes.getAsJsonArray("parts");
			if (partsRes != null && !partsRes.isEmpty()) {
				return new CompletionResult(partsRes.get(0).getAsJsonObject().get("text").getAsString());
			}
		}
		return new CompletionResult(""); // Return empty result if extraction fails
	}

	@Override
	public void performChat(String modelName, ChatConversation chat) {
		// Build the JSON array of contents from the conversation.
		List<ChatConversation.ChatMessage> messagesToSend = new ArrayList<>(chat.getMessages());
		JsonArray contentsJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : messagesToSend) {
			JsonObject contentJson = new JsonObject();
			JsonArray parts = new JsonArray();
			JsonObject part = new JsonObject();

			StringBuilder content = new StringBuilder(256);
			if (!msg.getContext().isEmpty()) {
				content.append("Context information:\n\n");
			}
			for (MessageContext ctx : msg.getContext()) {
				content.append(ctx.getDescriptor());
				content.append(ctx.getContent());
				content.append("\n\n");
			}
			content.append(msg.getContent());
			part.addProperty("text", content.toString());
			parts.add(part);
			contentJson.add("parts", parts);
			contentJson.addProperty("role", msg.getRole().toString().toLowerCase()); // roles: user, model
			contentsJson.add(contentJson);
		}

		// Create the JSON request object.
		JsonObject req = new JsonObject();
		req.add("contents", contentsJson);

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
				.uri(URI.create(apiConnection.getBaseUri() + "/v1beta/").resolve(
						"models/" + modelName + ":streamGenerateContent?alt=sse&key=" + apiConnection.getApiKey()))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");

		HttpRequest request = requestBuilder.build();

		// Send the request asynchronously and process the streamed response
		// line-by-line.
		asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
			try {
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
								JsonArray candidates = jsonChunk.getAsJsonArray("candidates");
								if (candidates != null && !candidates.isEmpty()) {
									JsonObject candidate = candidates.get(0).getAsJsonObject();
									JsonObject contentRes = candidate.getAsJsonObject("content");
									JsonArray partsRes = contentRes.getAsJsonArray("parts");
									if (partsRes != null && !partsRes.isEmpty()) {
										String chunk = partsRes.get(0).getAsJsonObject().get("text").getAsString();
										// Append the received chunk to the assistant message.
										assistantMessage.setContent(assistantMessage.getContent() + chunk);
										// Notify the conversation listeners that the assistant message was updated.
										chat.notifyMessageUpdated(assistantMessage);
									}
								}

							} catch (JsonSyntaxException e) {
								Activator.logError("Error parsing stream chunk: " + data, e);
							}
						} else if (line != null && line.startsWith("error")) {
							Activator.logError("Error in SSE stream: " + line, null);
						}
					});
				} else {
					Activator.logError("Streaming chat failed with status: " + response.statusCode() + ", body: "
							+ response.body(), null);
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