package com.chabicht.code_intelligence.apiclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
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
import com.google.gson.JsonPrimitive;

public class GeminiApiClient extends AbstractApiClient implements IAiApiClient {
	private transient final Gson gson = Activator.getDefault().createGson();
	private CompletableFuture<Void> asyncRequest;
	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

	public GeminiApiClient(AiApiConnection apiConnection) {
		super(apiConnection);
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "models");
		return res.get("models").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String modelId = o.get("name").getAsString();
			String modelName = o.get("displayName").getAsString();
			return new AiModel(apiConnection, modelId, modelName);
		}).collect(Collectors.toList());
	}

	@Override
	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.add("contents", createContentsArray(completionPrompt.compile()));
		JsonObject genConfig = getOrAddJsonObject(req, "generationConfig");
		setPropertyIfNotPresent(genConfig, "temperature", completionPrompt.getTemperature());
		genConfig.addProperty("maxOutputTokens", Activator.getDefault().getMaxChatTokens());

		JsonObject res = performPost(JsonObject.class, modelName + ":generateContent", req);
		String completion = res.get("candidates").getAsJsonArray().get(0).getAsJsonObject().get("content")
				.getAsJsonObject().get("parts").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
		return new CompletionResult(completion);
	}

	@Override
	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		JsonObject req = createFromPresets(PromptType.CHAT);
		String systemPrompt = getSystemPrompt(chat);
		if (StringUtils.isNoneBlank(systemPrompt)) {
			JsonObject systemInstruction = new JsonObject();
			JsonObject parts = new JsonObject();
			parts.add("text", new JsonPrimitive(systemPrompt));
			systemInstruction.add("parts", parts);
			req.add("system_instruction", systemInstruction);
		}
		req.add("contents", createChatContentsArray(chat));

		JsonObject genConfig = getOrAddJsonObject(req, "generationConfig");
		setPropertyIfNotPresent(genConfig, "temperature", 0.1);
		genConfig.addProperty("maxOutputTokens", maxResponseTokens);

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req);
		HttpRequest request = buildHttpRequest(modelName + ":streamGenerateContent?alt=sse&", requestBody);

		asyncRequest = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
				.thenAccept(response -> {
					response.body().forEach(line -> {
						if (line.startsWith("data: ")) {
							String data = line.substring(6).trim();
							try {
								JsonObject jsonChunk = JsonParser.parseString(data).getAsJsonObject();
								JsonArray candidates = jsonChunk.getAsJsonArray("candidates");
								if (!candidates.isEmpty()) {
									JsonObject candidate = candidates.get(0).getAsJsonObject();

									// Process content (if present) to update the message
									if (candidate.has("content")) {
										JsonObject content = candidate.get("content").getAsJsonObject();
										String chunk = content.get("parts").getAsJsonArray().get(0).getAsJsonObject()
												.get("text").getAsString();
										assistantMessage.setContent(assistantMessage.getContent() + chunk);
										chat.notifyMessageUpdated(assistantMessage);
									}

									// Check for finishReason and if it is "STOP", mark as finished.
									if (candidate.has("finishReason")
											&& "STOP".equals(candidate.get("finishReason").getAsString())) {
										chat.notifyChatResponseFinished(assistantMessage);
										asyncRequest = null;
									}
								}
							} catch (Exception e) {
								Activator.logError("Exception during streaming chat", e);
								asyncRequest = null;
							}
						}
					});
				}).exceptionally(e -> {
					Activator.logError("Exception during streaming chat", e);
					return null;
				});
	}

	private String getSystemPrompt(ChatConversation chat) {
		String res = null;

		for (ChatMessage msg : chat.getMessages()) {
			if (Role.SYSTEM.equals(msg.getRole())) {
				res = msg.getContent();
				break;
			}
		}

		return res;
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		req.add("contents", createContentsArray(content));
		JsonObject genConfig = getOrAddJsonObject(req, "generationConfig");
		setPropertyIfNotPresent(genConfig, "temperature", 1);

		JsonObject res = performPost(JsonObject.class, modelName + ":generateContent", req);
		String completion = res.get("candidates").getAsJsonArray().get(0).getAsJsonObject().get("content")
				.getAsJsonObject().get("parts").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
		return completion;
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

	private JsonArray createContentsArray(String text) {
		JsonObject part = new JsonObject();
		part.addProperty("text", text);
		JsonArray parts = new JsonArray();
		parts.add(part);
		JsonObject content = new JsonObject();
		content.add("parts", parts);
		JsonArray contents = new JsonArray();
		contents.add(content);
		return contents;
	}

	private JsonArray createChatContentsArray(ChatConversation chat) {
		JsonArray messagesJson = new JsonArray();
		for (ChatConversation.ChatMessage msg : chat.getMessages()) {
			if (Role.SYSTEM.equals(msg.getRole())) {
				continue;
			}

			JsonObject jsonMsg = new JsonObject();

			// Convert role to lowercase. If your API expects "model" for assistant
			// messages, you might need to map it.
			String role = msg.getRole().toString().toLowerCase();
			if ("assistant".equals(role)) {
				// Some APIs expect the role to be "model" for responses.
				role = "model";
			}
			jsonMsg.addProperty("role", role);

			// Build the full text content including any context information.
			StringBuilder contentBuilder = new StringBuilder();
			if (!msg.getContext().isEmpty()) {
				contentBuilder.append("Context information:\n\n");
				for (MessageContext ctx : msg.getContext()) {
					contentBuilder.append(ctx.compile());
					contentBuilder.append("\n");
				}
			}
			contentBuilder.append(msg.getContent());

			// Instead of "content", create a "parts" array with an object containing
			// "text".
			JsonArray partsArray = new JsonArray();
			JsonObject partObj = new JsonObject();
			partObj.addProperty("text", contentBuilder.toString());
			partsArray.add(partObj);
			jsonMsg.add("parts", partsArray);

			messagesJson.add(jsonMsg);
		}
		return messagesJson;
	}

	private HttpRequest buildHttpRequest(String relPath, String body) {
		String separator = relPath.endsWith("&") ? "" : "?";
		return HttpRequest.newBuilder()
				.uri(URI.create(BASE_URL + relPath + separator + "key=" + apiConnection.getApiKey()))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(BASE_URL + relPath + "?key=" + apiConnection.getApiKey())).GET().build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request,
					HttpResponse.BodyHandlers.ofString());
			return (T) JsonParser.parseString(response.body());
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath,
			U requestBody) {
		try {
			HttpRequest request = buildHttpRequest(relPath, gson.toJson(requestBody));
			HttpResponse<String> response = HttpClient.newHttpClient().send(request,
					HttpResponse.BodyHandlers.ofString());
			return (T) JsonParser.parseString(response.body());
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
