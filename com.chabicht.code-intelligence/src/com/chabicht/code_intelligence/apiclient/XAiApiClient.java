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

/**
 * Implementation for X.ai API using the chat completion endpoint.
 * Compatible with the OpenAI REST API structure but tailored for X.ai specifics.
 */
public class XAiApiClient extends AbstractApiClient implements IAiApiClient {

	private transient final Gson gson = Activator.getDefault().createGson();
    private CompletableFuture<Void> asyncRequest;

    /**
     * Constructs an XAiApiClient with the provided API connection.
     * The AiApiConnection should be configured with base URI "https://api.x.ai/v1"
     * and the X.ai API key.
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
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(Redirect.ALWAYS)
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
                    .GET();
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
    private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath, U requestBody) {
        int statusCode = -1;
        String responseBody = "(nothing)";
        String requestBodyString = "(nothing)";
        try {
            requestBodyString = gson.toJson(requestBody);
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(Redirect.ALWAYS)
                    .build();
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
		req.addProperty("max_tokens", Activator.getDefault().getMaxCompletionTokens());

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", completionPrompt.compile());
        messages.add(userMessage);

        req.add("messages", messages);

        JsonObject res = performPost(JsonObject.class, "chat/completions", req);
        return new CompletionResult(res.get("choices").getAsJsonArray().get(0).getAsJsonObject()
                .get("message").getAsJsonObject().get("content").getAsString());
    }

    @Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
        // Build the JSON array of messages from the conversation
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
				content.append(ctx.compile());
				content.append("\n");
            }
            content.append(msg.getContent());
            jsonMsg.addProperty("content", content.toString());
            messagesJson.add(jsonMsg);
        }

        // Create the JSON request object with streaming enabled
        JsonObject req = new JsonObject();
        req.addProperty("model", modelName);
        req.addProperty("stream", true);
		req.addProperty("max_tokens", maxResponseTokens);
        req.add("messages", messagesJson);

        // Add an empty assistant message to be updated as the stream progresses
        ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
                ChatConversation.Role.ASSISTANT, "");
        chat.addMessage(assistantMessage);

        // Prepare the HTTP request
        String requestBody = gson.toJson(req);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(Redirect.ALWAYS)
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiConnection.getBaseUri() + "/").resolve("chat/completions"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json");
        if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
            requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
        }
        HttpRequest request = requestBuilder.build();

        // Send the request asynchronously and process the streaming response
        asyncRequest = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
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
                                        for (JsonElement choiceElement : choices) {
                                            JsonObject choice = choiceElement.getAsJsonObject();
                                            if (choice.has("delta")) {
                                                JsonObject delta = choice.getAsJsonObject("delta");
                                                if (delta.has("content")) {
                                                    String chunk = delta.get("content").getAsString();
                                                    assistantMessage.setContent(
                                                            assistantMessage.getContent() + chunk);
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