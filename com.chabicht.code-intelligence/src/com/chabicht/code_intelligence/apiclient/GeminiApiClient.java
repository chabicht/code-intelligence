package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOLS_ENABLED;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
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

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsGemini());
		}

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

		// Reasoning
		JsonObject thinkingConfig = new JsonObject();
		if (options.containsKey(REASONING_ENABLED) && Boolean.TRUE.equals(options.get(REASONING_ENABLED))) {
			int reasoningBudgetTokens = (int) options.get(REASONING_BUDGET_TOKENS);

			genConfig.addProperty("maxOutputTokens", maxResponseTokens + reasoningBudgetTokens);

			thinkingConfig.addProperty("includeThoughts", true);
			thinkingConfig.addProperty("thinkingBudget", reasoningBudgetTokens);
		} else {
			thinkingConfig.addProperty("thinkingBudget", 0);
		}
		genConfig.add("thinkingConfig", thinkingConfig);

		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		String requestBody = gson.toJson(req);
		HttpRequest request = buildHttpRequest(modelName + ":streamGenerateContent?alt=sse&", requestBody);

		AtomicBoolean responseFinished = new AtomicBoolean(false);
		AtomicBoolean thinkingStarted = new AtomicBoolean(false);

		asyncRequest = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
				.thenAccept(response -> {
					if (response.statusCode() >= 200 && response.statusCode() < 300) {
						response.body().forEach(line -> {
							// Activator.logInfo("Gemini: " + line);
							if (line.startsWith("data: ")) {
								String data = line.substring(6).trim();
								try {
									JsonObject jsonChunk = JsonParser.parseString(data).getAsJsonObject();
									JsonArray candidates = jsonChunk.getAsJsonArray("candidates");
									if (candidates != null && !candidates.isEmpty()) {
										JsonObject candidate = candidates.get(0).getAsJsonObject();
										JsonObject content = candidate.getAsJsonObject("content");

										if (content != null && content.has("parts")) {
											JsonArray parts = content.getAsJsonArray("parts");
											if (parts != null && !parts.isEmpty()) {
												JsonObject firstPart = parts.get(0).getAsJsonObject();

												if (firstPart.has("text")) {
													String chunk = firstPart.get("text").getAsString();
													if (firstPart.has("thought")
															&& firstPart.get("thought").getAsBoolean()) {
														if (!thinkingStarted.get()) {
															assistantMessage.setContent(
																	assistantMessage.getContent() + "\n<think>\n");
															thinkingStarted.set(true);
														}
														assistantMessage.setThinkingContent(
																(assistantMessage.getThinkingContent() == null ? ""
																		: assistantMessage.getThinkingContent())
																		+ chunk);
													} else if (!firstPart.has("thought")
															|| !firstPart.get("thought").getAsBoolean()) {
														if (thinkingStarted.get()) {
															assistantMessage.setContent(
																	assistantMessage.getContent() + "\n</think>\n");
															thinkingStarted.set(false);
														}
													}

													assistantMessage.setContent(assistantMessage.getContent() + chunk);
													chat.notifyMessageUpdated(assistantMessage);
												}

							if (firstPart.has("functionCall")) {
								JsonObject functionCall = firstPart.getAsJsonObject("functionCall");
								String id = Optional.ofNullable(functionCall.get("id"))
										.map(JsonElement::getAsString).orElse(null);
								String functionName = functionCall.get("name").getAsString();
								JsonObject functionArgs = functionCall.getAsJsonObject("args");
								String argsJson = (functionArgs != null) ? gson.toJson(functionArgs)
										: "{}";
								
								// Capture thoughtSignature if present (required by Gemini 3 Pro)
								if (firstPart.has("thoughtSignature")) {
									String thoughtSignature = firstPart.get("thoughtSignature").getAsString();
									assistantMessage.setMetadata("gemini_thought_signature", thoughtSignature);
								}
								
								assistantMessage.setFunctionCall(
										new FunctionCall(id, functionName, argsJson));
								chat.notifyFunctionCalled(assistantMessage);
							}
											}
										}

										if (candidate.has("finishReason")) {
											String reason = candidate.get("finishReason").getAsString();
											if ("MALFORMED_FUNCTION_CALL".equals(reason)) {
												Activator.logError("Error " + reason + " in API response.\n");
											}
											chat.notifyChatResponseFinished(assistantMessage);
											responseFinished.set(true);
											asyncRequest = null;
										}
									}
								} catch (Exception e) {
									Activator.logError("Exception processing streaming chat chunk: " + data, e);
									if (asyncRequest != null) {
										if (!responseFinished.get()) {
											chat.notifyChatResponseFinished(assistantMessage);
											responseFinished.set(true);
										}
										asyncRequest = null;
									}
								}
							}
						});
					} else {
						String body = response.body().collect(Collectors.joining("\n"));
						Activator.logError("Error " + response.statusCode() + " in API call:\n" + body
								+ "\n\nRequest JSON:\n" + requestBody);
						if (asyncRequest != null) {
							if (!responseFinished.get()) {
								chat.notifyChatResponseFinished(assistantMessage);
								responseFinished.set(true);
							}
							asyncRequest = null;
						}
					}
				}).exceptionally(e -> {
					Activator.logError("Exception during streaming chat request", e);
					if (asyncRequest != null) {
						if (!responseFinished.get()) {
							chat.notifyChatResponseFinished(assistantMessage);
							responseFinished.set(true);
						}
						asyncRequest = null;
					}
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
			// Skip TOOL_SUMMARY messages, they are for internal use only
			if (Role.TOOL_SUMMARY.equals(msg.getRole())) {
				continue;
			}

			JsonObject jsonMsg = createMessage(msg.getRole());

			if (msg.getFunctionCall().isPresent()) {
				fillFunctionCall(jsonMsg, msg);
			} else {
				fillTextMessage(jsonMsg, msg);
			}
			messagesJson.add(jsonMsg);

			if (msg.getFunctionResult().isPresent()) {
				JsonObject resultUserMessage = createMessage(Role.USER);
				fillFunctionResult(resultUserMessage, msg);
				messagesJson.add(resultUserMessage);
			}
		}
		return messagesJson;
	}

	private void fillFunctionCall(JsonObject jsonMsg, ChatMessage msg) {
		FunctionCall fc = msg.getFunctionCall().get();
		JsonObject functionCallObj = new JsonObject();
		functionCallObj.addProperty("id", fc.getId());
		functionCallObj.addProperty("name", fc.getFunctionName());
		functionCallObj.add("args", gson.fromJson(fc.getArgsJson(), JsonObject.class));
		
		JsonObject partObj = new JsonObject();
		partObj.add("functionCall", functionCallObj);
		
		// Add thoughtSignature if present (required by Gemini 3 Pro for function calling)
		Object thoughtSignature = msg.getMetadata("gemini_thought_signature");
		if (thoughtSignature != null) {
			partObj.addProperty("thoughtSignature", (String) thoughtSignature);
		}
		
		jsonMsg.getAsJsonArray("parts").add(partObj);
	}

	private void fillFunctionResult(JsonObject jsonMsg, ChatMessage msg) {
		FunctionResult fr = msg.getFunctionResult().get();
		JsonObject functionCallObj = new JsonObject();
		functionCallObj.addProperty("id", fr.getId());
		functionCallObj.addProperty("name", fr.getFunctionName());
		functionCallObj.add("response", gson.fromJson(fr.getResultJson(), JsonObject.class));
		JsonObject partObj = new JsonObject();
		partObj.add("functionResponse", functionCallObj);
		jsonMsg.getAsJsonArray("parts").add(partObj);
	}

	private void fillTextMessage(JsonObject jsonMsg, ChatConversation.ChatMessage msg) {
		// Build the full text content including any context information.
		StringBuilder contentBuilder = new StringBuilder();
		if (!msg.getContext().isEmpty()) {
			contentBuilder.append("Context information:\n\n");
			for (MessageContext ctx : msg.getContext()) {
				contentBuilder.append(ctx.compile(true));
				contentBuilder.append("\n");
			}
		}
		contentBuilder.append(msg.getContent());

		JsonArray partsArray = jsonMsg.getAsJsonArray("parts");
		JsonObject partObj = new JsonObject();
		partObj.addProperty("text", contentBuilder.toString());
		partsArray.add(partObj);
	}

	private JsonObject createMessage(Role role) {
		JsonObject jsonMsg = new JsonObject();

		// Convert role to lowercase. If your API expects "model" for assistant
		// messages, you might need to map it.
		String roleStr = role.toString().toLowerCase();
		if ("assistant".equals(roleStr)) {
			// Some APIs expect the role to be "model" for responses.
			roleStr = "model";
		}
		jsonMsg.addProperty("role", roleStr);

		JsonArray partsArray = new JsonArray();
		jsonMsg.add("parts", partsArray);

		return jsonMsg;
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
