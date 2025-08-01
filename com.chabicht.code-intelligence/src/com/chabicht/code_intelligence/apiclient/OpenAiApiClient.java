package com.chabicht.code_intelligence.apiclient;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.TOOLS_ENABLED;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Implementation for OpenAI or compatible API using the chat completion
 * endpoint.
 */
public class OpenAiApiClient extends AbstractApiClient implements IAiApiClient {

	private transient final Gson gson = Activator.getDefault().createGson();
	private CompletableFuture<Void> asyncRequest;

	public OpenAiApiClient(AiApiConnection apiConnection) {
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
	<T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
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
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
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
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", completionPrompt.getTemperature());
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
			// Skip TOOL_SUMMARY messages, they are for internal use only
			if (Role.TOOL_SUMMARY.equals(msg.getRole())) {
				continue;
			}

			JsonObject jsonMsg = new JsonObject();
			// Convert the role enum to lowercase string (system, user, assistant).
			jsonMsg.addProperty("role", msg.getRole().toString().toLowerCase());

			// For user, system messages, or assistant messages without function calls
			StringBuilder contentBuilder = new StringBuilder(256);
			if (!msg.getContext().isEmpty()) {
				contentBuilder.append("Context information:\n\n");
				for (MessageContext ctx : msg.getContext()) {
					contentBuilder.append(ctx.compile(true));
					contentBuilder.append("\n");
				}
			}
			String textContent = msg.getContent();
			if (textContent != null) { // Ensure content is not null before appending
				contentBuilder.append(textContent);
			}
			jsonMsg.addProperty("content", contentBuilder.toString());

			// Add tool call to assistant message if applicable.
			if (Role.ASSISTANT.equals(msg.getRole()) && msg.getFunctionCall().isPresent()) {
				FunctionCall fc = msg.getFunctionCall().get();
				JsonArray toolCallsArray = new JsonArray();
				JsonObject toolCallItem = new JsonObject();
				toolCallItem.addProperty("id", fc.getId());
				toolCallItem.addProperty("type", "function");

				JsonObject functionDetails = new JsonObject();
				functionDetails.addProperty("name", fc.getFunctionName());
				// Arguments for OpenAI function calls should be a string containing JSON
				functionDetails.addProperty("arguments", fc.getArgsJson());
				toolCallItem.add("function", functionDetails);
				toolCallsArray.add(toolCallItem);

				jsonMsg.add("tool_calls", toolCallsArray);
			}

			messagesJson.add(jsonMsg); // Add the constructed message (user, assistant, or system)

			if (msg.getFunctionResult().isPresent()) {
				FunctionResult fr = msg.getFunctionResult().get();
				JsonObject toolMessage = new JsonObject();
				toolMessage.addProperty("role", "tool");
				toolMessage.addProperty("tool_call_id", fr.getId());
				toolMessage.addProperty("content", fr.getResultJson()); // Content is the JSON string of the result
				messagesJson.add(toolMessage);
			}
		}

		// Create the JSON request object.
		JsonObject req = createFromPresets(PromptType.CHAT);
		req.addProperty("model", modelName);
		req.addProperty("max_completion_tokens", maxResponseTokens);
		req.addProperty("stream", true);

		Map<ChatOption, Object> options = chat.getOptions();
		if (options.containsKey(TOOLS_ENABLED) && Boolean.TRUE.equals(options.get(TOOLS_ENABLED))) {
			if (apiConnection.isLegacyFormat()) {
				patchMissingProperties(req, ToolDefinitions.getInstance().getToolDefinitionsOpenAiLegacy());
			} else {
				JsonObject toolDefinitionsOpenAi = ToolDefinitions.getInstance().getToolDefinitionsOpenAi();
				// Hack for Fireworks.AI: they don't support the strict flag in function
				// definitions.
				if (apiConnection.getBaseUri().contains("fireworks.ai")) {
					removeStrictFlag(toolDefinitionsOpenAi);
				}
				patchMissingProperties(req, toolDefinitionsOpenAi);
			}
		}
		req.add("messages", messagesJson);

		// Add a new (empty) assistant message to the conversation.
		// This is the message that will be updated as new text is streamed in.
		ChatConversation.ChatMessage assistantMessage = new ChatConversation.ChatMessage(
				ChatConversation.Role.ASSISTANT, "");
		chat.addMessage(assistantMessage, true);

		// Prepare the HTTP request.
		String requestBody = gson.toJson(req);
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofMinutes(10))
				.uri(URI.create(apiConnection.getBaseUri() + "/").resolve("chat/completions"))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody)).header("Content-Type", "application/json");
		if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
			requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
		}
		HttpRequest request = requestBuilder.build();

		// Map to keep track of tool calls by their index
		Map<Integer, ToolCallInfo> activeToolCalls = new HashMap<>();

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

										// Check for tool_calls in the delta
										if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
											// Process tool calls
											handleToolCallDelta(delta.getAsJsonArray("tool_calls"), activeToolCalls,
													assistantMessage, chat);
										}
                                        
                                        // Check for function_call in the delta (deprecated format)
                                        if (delta.has("function_call") && !delta.get("function_call").isJsonNull()) {
                                            // Process function call (deprecated format)
                                            handleFunctionCallDelta(delta.getAsJsonObject("function_call"), activeToolCalls,
                                                    assistantMessage, chat);
                                        }

										if (StringUtils.isNotBlank(chunk)) {
											// Append the received chunk to the assistant message.
											assistantMessage.setContent(assistantMessage.getContent() + chunk);
											// Notify the conversation listeners that the assistant message was updated.
											chat.notifyMessageUpdated(assistantMessage);
										}
									}

									// Check for finish_reason to detect completed tool calls or function calls
									if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
										String finishReason = choice.get("finish_reason").getAsString();
										if ("tool_calls".equals(finishReason) || "function_call".equals(finishReason)) {
											// All tool calls or function calls are complete - finalize any pending calls
											finalizeToolCalls(activeToolCalls, assistantMessage, chat);
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
				// Check if any tool calls are still pending finalization
				if (!activeToolCalls.isEmpty()) {
					finalizeToolCalls(activeToolCalls, assistantMessage, chat);
				}

				chat.notifyChatResponseFinished(assistantMessage);
				asyncRequest = null;
			}
		}).exceptionally(e -> {
			Activator.logError("Exception during streaming chat", e);
			
			// Clean up any pending tool/function calls
			if (!activeToolCalls.isEmpty()) {
				finalizeToolCalls(activeToolCalls, assistantMessage, chat);
			}
			
			chat.notifyChatResponseFinished(assistantMessage);
			asyncRequest = null;
			return null;
		});
	}

	private void removeStrictFlag(JsonObject toolDefinitionsOpenAi) {
		for (JsonElement el : toolDefinitionsOpenAi.get("tools").getAsJsonArray()) {
			el.getAsJsonObject().get("function").getAsJsonObject().remove("strict");
		}
	}

	@Override
	public String caption(String modelName, String content) {
		JsonObject req = createFromPresets(PromptType.INSTRUCT);
		req.addProperty("model", modelName);
		setPropertyIfNotPresent(req, "temperature", 1);

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

	/**
	 * Processes tool call deltas from the streaming API response.
	 * 
	 * @param toolCallDeltas   The tool call deltas from the current chunk
	 * @param activeToolCalls  Map of active tool calls being tracked
	 * @param assistantMessage The assistant message to update
	 * @param chat             The chat conversation
	 */
	private void handleToolCallDelta(JsonArray toolCallDeltas, Map<Integer, ToolCallInfo> activeToolCalls,
			ChatConversation.ChatMessage assistantMessage, ChatConversation chat) {
		for (JsonElement toolCallElement : toolCallDeltas) {
			JsonObject toolCallDelta = toolCallElement.getAsJsonObject();

			try {
				// Get the index to identify which tool call this belongs to
				int index = toolCallDelta.get("index").getAsInt();

				// Check if this is a new tool call or an update to an existing one
				if (!activeToolCalls.containsKey(index)) {
					// This is a new tool call, extract ID and function name
					String id = null;
					String functionName = null;

					if (toolCallDelta.has("id") && !toolCallDelta.get("id").isJsonNull()) {
						id = toolCallDelta.get("id").getAsString();
					}

					if (toolCallDelta.has("function")) {
						JsonObject function = toolCallDelta.getAsJsonObject("function");
						if (function.has("name") && !function.get("name").isJsonNull()) {
							functionName = function.get("name").getAsString();
						}
					}

					// Only create a new tool call info if we have both id and name
					if (id != null && functionName != null) {
						activeToolCalls.put(index, new ToolCallInfo(index, id, functionName));
					}
				}

				// Now update the existing tool call with any new argument chunks
				if (activeToolCalls.containsKey(index) && toolCallDelta.has("function")) {
					JsonObject function = toolCallDelta.getAsJsonObject("function");
					if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
						String argumentChunk = function.get("arguments").getAsString();
						activeToolCalls.get(index).appendArguments(argumentChunk);
					}
				}
			} catch (Exception e) {
				Activator.logError("Error processing tool call delta: " + toolCallDelta, e);
				// We catch the exception but don't rethrow to allow processing to continue
			}
		}
	}

	/**
	 * Finalizes any pending tool calls when streaming ends.
	 * 
	 * @param activeToolCalls  Map of active tool calls being tracked
	 * @param assistantMessage The assistant message to update
	 * @param chat             The chat conversation
	 */
	private void finalizeToolCalls(Map<Integer, ToolCallInfo> activeToolCalls,
			ChatConversation.ChatMessage assistantMessage, ChatConversation chat) {
		// Find any remaining tool calls that haven't been finalized yet
		for (ToolCallInfo toolCall : activeToolCalls.values()) {
			if (!toolCall.isComplete()) {
				toolCall.markComplete();

				// Only notify for the first tool call if there are multiple
				// (This is just one approach - you might want to handle multiple tools
				// differently)
				if (!assistantMessage.getFunctionCall().isPresent()) {
					assistantMessage.setFunctionCall(toolCall.toFunctionCall());
					chat.notifyFunctionCalled(assistantMessage);
				}
			}
		}

		// Clear the active tool calls map
		activeToolCalls.clear();
	}

	/**
	 * Processes function call deltas from the streaming API response (deprecated format).
	 * 
	 * @param functionCallDelta  The function call delta from the current chunk
	 * @param activeToolCalls    Map of active tool calls being tracked
	 * @param assistantMessage   The assistant message to update
	 * @param chat               The chat conversation
	 */
	private void handleFunctionCallDelta(JsonObject functionCallDelta, 
	                                   Map<Integer, ToolCallInfo> activeToolCalls,
	                                   ChatConversation.ChatMessage assistantMessage, 
	                                   ChatConversation chat) {
		try {
			// For the deprecated function_call format, we always use index 0 
			// (there is only one function call in this format)
			int index = 0;
			
			// Check if this is a new function call or an update to an existing one
			if (!activeToolCalls.containsKey(index)) {
				// This is a new function call, extract the name
				String name = null;
				
				if (functionCallDelta.has("name") && !functionCallDelta.get("name").isJsonNull()) {
					name = functionCallDelta.get("name").getAsString();
					
					// Generate a unique ID for the function call
					String id = "call_func_" + System.currentTimeMillis();
					activeToolCalls.put(index, new ToolCallInfo(index, id, name));
				}
			}
			
			// Now update the existing function call with any new argument chunks
			if (activeToolCalls.containsKey(index) && 
				functionCallDelta.has("arguments") && 
				!functionCallDelta.get("arguments").isJsonNull()) {
				
				String argumentChunk = functionCallDelta.get("arguments").getAsString();
				activeToolCalls.get(index).appendArguments(argumentChunk);
			}
		} catch (Exception e) {
			Activator.logError("Error processing function call delta: " + functionCallDelta, e);
		}
	}
	
	/**
	 * Helper class to track and accumulate tool call information from streaming
	 * responses.
	 */
	private static class ToolCallInfo {
		private final int index; // The position of this tool call in the array
		private final String id; // The unique ID of the tool call
		private final String name; // The function name
		private final StringBuilder argumentsJson = new StringBuilder(); // Accumulating JSON arguments
		private boolean isComplete = false; // Whether the tool call is complete
		private String errorMessage = null; // Any error message if the tool call failed

		public ToolCallInfo(int index, String id, String name) {
			this.index = index;
			this.id = id;
			this.name = name;
		}

		public void appendArguments(String argumentChunk) {
			argumentsJson.append(argumentChunk);
		}

		public boolean isComplete() {
			return isComplete;
		}

		public void markComplete() {
			isComplete = true;
		}

		public void markFailure(String message) {
			this.errorMessage = message;
			this.isComplete = true; // Mark as complete to avoid further processing
		}

		public FunctionCall toFunctionCall() {
			return new FunctionCall(id, name, argumentsJson.toString());
		}

		// Getters
		public int getIndex() {
			return index;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getArgumentsJson() {
			return argumentsJson.toString();
		}

		public boolean hasFailed() {
			return errorMessage != null;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}
}
