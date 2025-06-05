package com.chabicht.code_intelligence.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.StringUtils;

/**
 * A conversation with a chat model, displayed in the chat view.
 */
public class ChatConversation {
	/**
	 * Role a chat message can have.
	 */
	public static enum Role {
		SYSTEM, USER, ASSISTANT, TOOL;
	}

	public static enum RangeType {
		LINE("line", "l"), OFFSET("offset", "o");

		private String name;
		private String shorthand;

		private RangeType(String name, String shorthand) {
			this.name = name;
			this.shorthand = shorthand;
		}

		public String getName() {
			return name;
		}

		public String getShorthand() {
			return shorthand;
		}
	}

	public static enum ChatOption {
		REASONING_ENABLED, REASONING_BUDGET_TOKENS, TOOLS_ENABLED;
	}

	/**
	 * Context information added to a message.
	 */
	public static class MessageContext {
		private final UUID uuid;
		private final String fileName;
		private final RangeType rangeType;
		private final int start;
		private final int end;
		private final String instructionsBefore;
		private final String content;
		private final String instructionsAfter;

		public MessageContext(String fileName, int startLine, int endLine, String content) {
			this(fileName, RangeType.LINE, startLine, endLine, content);
		}

		public MessageContext(String fileName, RangeType rangeType, int start, int end, String content) {
			this(UUID.randomUUID(), fileName, rangeType, start, end, "", content, "");
		}

		public MessageContext(UUID uuid, String fileName, RangeType rangeType, int start, int end,
				String instructionsBefore, String content, String instructionsAfter) {
			this.uuid = uuid;
			this.fileName = fileName;
			this.rangeType = rangeType;
			this.start = start;
			this.end = end;
			this.instructionsBefore = instructionsBefore;
			this.content = content;
			this.instructionsAfter = instructionsAfter;
		}

		public boolean isDuplicate(MessageContext other) {
			if (other != null && StringUtils.equals(this.getFileName(), other.getFileName())
					&& this.getRangeType().equals(other.getRangeType()) && this.getStart() == other.getStart()
					&& this.getEnd() == other.getEnd()) {
				return true;
			}
			return false;
		}

		public UUID getUuid() {
			return uuid;
		}

		public String getFileName() {
			return fileName;
		}

		public RangeType getRangeType() {
			return rangeType;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

		public String getContent() {
			return content;
		}

		public String getLabel() {
			return this.fileName + ":" + getShortRangeDescription();
		}

		public String getDescriptor(boolean prefixLineNumbers) {
			StringBuilder sb = new StringBuilder().append(this.getFileName()).append(" ")
					.append(this.getRangeDescription());
			if (prefixLineNumbers) {
				sb.append(" (CAUTION! Each line is prefixed with the line number"
						+ " followed by a colon and a space. These are added for your convenience."
						+ " Make sure to remove them before you cite code for the user or call tools.)");
			}
			return sb.append("\n").toString();
		}

		public String compile() {
			return compile(true);
		}

		public String compile(boolean prefixLineNumbers) {
			StringBuilder sb = new StringBuilder();
			if (StringUtils.isNotBlank(instructionsBefore)) {
				sb.append(instructionsBefore);
			}
			sb.append("```").append(getDescriptor(prefixLineNumbers))
					.append(prefixLineNumbers ? getPrefixedContent() : getContent()).append("\n```\n");
			if (StringUtils.isNotBlank(instructionsAfter)) {
				sb.append(instructionsAfter);
			}
			return sb.toString();
		}

		private String getPrefixedContent() {
			if (this.content == null) {
				return "";
			}

			String[] lines = this.content.split("\\r?\\n", -1);

			// Determine the width for line number padding based on the 'end' line number.
			// 'this.end' is the last line number of the context.
			// Line numbers are expected to be 1-based and positive.
			int maxLineNumberForFormatting = this.end;
			int maxLineNumberLength = String.valueOf(maxLineNumberForFormatting).length();
			if (maxLineNumberLength <= 0) { // Should not happen with positive line numbers
				maxLineNumberLength = 1;
			}

			java.util.List<String> prefixedLines = new java.util.ArrayList<>();
			for (int i = 0; i < lines.length; i++) {
				// Calculate the current line number
				int currentLineNumber = this.start + i;

				// Format the line number prefix (e.g., " 9: ", " 10: ", "100: ")
				String prefix = String.format("%" + maxLineNumberLength + "d: ", currentLineNumber);

				prefixedLines.add(prefix + lines[i]);
			}

			return String.join("\n", prefixedLines);
		}

		public String getRangeDescription() {
			return rangeType.getName() + " " + start + " to " + end;
		}

		public String getShortRangeDescription() {
			return rangeType.getShorthand() + start + "-" + end;
		}

		@Override
		public String toString() {
			return "MessageContext [fileName=" + fileName + ", startLine=" + start + ", endLine=" + end
					+ ", content:\n  ===\n" + content + "\n  ===\n";
		}
	}

	/**
	 * Message in a Chat.
	 */
	public static class ChatMessage {
		private final UUID id;
		private final Role role;
		private String content;
		private final List<MessageContext> context = new ArrayList<>();

		private Optional<FunctionCall> functionCall = Optional.empty();
		private Optional<FunctionResult> functionResult = Optional.empty();

		// Fields for thinking/reasoning content
		private String thinkingContent;
		private boolean isThinkingComplete;
		private Map<String, Object> thinkingMetadata = new HashMap<>();

		private ChatMessage() {
			id = UUID.randomUUID();
			role = Role.USER;
		}

		public ChatMessage(Role role, String content) {
			id = UUID.randomUUID();
			this.role = role;
			this.content = content;
		}

		public UUID getId() {
			return id;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public Role getRole() {
			return role;
		}

		public List<MessageContext> getContext() {
			return context;
		}

		public String getToolCallDetailsAsMarkdown() {
			if (!getFunctionCall().isPresent()) {
				return "";
			}
			StringBuilder sb = new StringBuilder();
			FunctionCall call = getFunctionCall().get();
			// The initial "\n\n" for the section header is included here.
			sb.append("\n\n## Tool Call ").append(call.getFunctionName()).append("\n");

			sb.append(call.getParamsAsMarkdown()); // Appends "" or "Parameters:\n..."

			if (getFunctionResult().isPresent()) {
				FunctionResult result = getFunctionResult().get();
				sb.append(result.getResultsAsMarkdown()); // Appends "" or "Results:\n..."
			}
			return sb.toString();
		}


		public Optional<FunctionCall> getFunctionCall() {
			return functionCall;
		}

		public void setFunctionCall(Optional<FunctionCall> functionCall) {
			this.functionCall = functionCall;
		}

		public void setFunctionCall(FunctionCall functionCall) {
			this.functionCall = Optional.ofNullable(functionCall);
		}

		public Optional<FunctionResult> getFunctionResult() {
			return functionResult;
		}

		public void setFunctionResult(Optional<FunctionResult> functionResult) {
			this.functionResult = functionResult;
		}

		public void setFunctionResult(FunctionResult functionResult) {
			this.functionResult = Optional.ofNullable(functionResult);
		}

		/**
		 * Gets the thinking/reasoning content of this message.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @return the thinking content
		 */
		public String getThinkingContent() {
			return thinkingContent;
		}

		/**
		 * Sets the thinking/reasoning content of this message.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @param thinkingContent the thinking content to set
		 */
		public void setThinkingContent(String thinkingContent) {
			this.thinkingContent = thinkingContent;
		}

		/**
		 * Checks if the thinking process is complete.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @return true if thinking is complete, false otherwise
		 */
		public boolean isThinkingComplete() {
			return isThinkingComplete;
		}

		/**
		 * Sets whether the thinking process is complete.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @param isThinkingComplete true if thinking is complete, false otherwise
		 */
		public void setThinkingComplete(boolean isThinkingComplete) {
			this.isThinkingComplete = isThinkingComplete;
		}

		/**
		 * Gets the metadata map for thinking content. This can be used to store
		 * API-specific properties.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @return the thinking metadata map
		 */
		public Map<String, Object> getThinkingMetadata() {
			return thinkingMetadata;
		}

		/**
		 * Sets a metadata property for thinking content.
		 * 
		 * <p>
		 * This field is used only for reasoning content that is sent "out of band" via
		 * the API. This is the case for e.g. the Anthropic API, where the original
		 * thinking content must be reconstructed for subsequent calls.
		 * 
		 * @param key   the metadata key
		 * @param value the metadata value
		 */
		public void setThinkingMetadata(String key, Object value) {
			this.thinkingMetadata.put(key, value);
		}

		/**
		 * Gets a specific metadata property for thinking content.
		 * 
		 * @param key the metadata key
		 * @return the metadata value, or null if not present
		 */
		public Object getThinkingMetadata(String key) {
			return this.thinkingMetadata.get(key);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("ChatMessage {").append("\n");
			sb.append("  id=").append(id);
			sb.append(", role=").append(role).append("\n");
			sb.append("  content:\n  ===\n").append(content).append("\n  ===\n");
			if (StringUtils.isNotBlank(thinkingContent)) {
				sb.append("  thinkingContent:\n  ===\n").append(thinkingContent).append("\n  ===\n");
				sb.append("  isThinkingComplete=").append(isThinkingComplete).append("\n");
				if (!thinkingMetadata.isEmpty()) {
					sb.append("  thinkingMetadata=").append(thinkingMetadata).append("\n");
				}
			}
			if (context != null && !context.isEmpty()) {
				sb.append("  context=").append(context).append("\n");
			} else {
				sb.append(", context=[]");
			}
			sb.append(" }");
			if (functionCall.isPresent()) {
				sb.append(",\n  functionCall=" + functionCall);
			} else {
				sb.append("\n");
			}
			return sb.toString();
		}
	}

	public static class FunctionCall {
		private final String id; // Generated by API
		private final String functionName;
		private final String argsJson;
		private Map<String, FunctionParamValue> prettyParams;

		public FunctionCall(String id, String functionName, String functionArgsJson) {
			this.id = id;
			this.functionName = functionName;
			this.argsJson = functionArgsJson;
			this.prettyParams = new LinkedHashMap<>(); // LinkedHashMap to preserve order
		}

		public String getId() {
			return id;
		}

		public String getFunctionName() {
			return functionName;
		}

		public String getArgsJson() {
			return argsJson;
		}

		public Map<String, FunctionParamValue> getPrettyParams() {
			return prettyParams;
		}

		public void setPrettyParams(Map<String, FunctionParamValue> prettyParams) {
			this.prettyParams = prettyParams;
		}

		public void addPrettyParam(String name, String value, boolean isMarkdown) {
			prettyParams.put(name, new FunctionParamValue(value, isMarkdown));
		}

		public String getParamsAsMarkdown() {
			if (getPrettyParams().isEmpty()) {
				return "";
			}
			StringBuilder sb = new StringBuilder("### Parameters\n");
			for (Map.Entry<String, FunctionParamValue> entry : getPrettyParams().entrySet()) {
				entry.getValue().appendMarkdown(sb, entry.getKey());
			}
			return sb.toString();
		}

		@Override
		public String toString() {
			return "FunctionCall [id=" + id + ", functionName=" + functionName + ", argsJson=" + argsJson + "]";
		}
	}

	public static class FunctionResult {
		private final String id;
		private final String functionName;
		private String resultJson;
		private Map<String, FunctionParamValue> prettyResults;

		public FunctionResult(String id, String functionName) {
			this.id = id;
			this.functionName = functionName;
			this.prettyResults = new LinkedHashMap<>();
		}

		public String getId() {
			return id;
		}

		public String getFunctionName() {
			return functionName;
		}

		public String getResultJson() {
			return resultJson;
		}

		public void setResultJson(String resultJson) {
			this.resultJson = resultJson;
		}

		public Map<String, FunctionParamValue> getPrettyResults() {
			return prettyResults;
		}

		public void setPrettyResults(Map<String, FunctionParamValue> prettyResults) {
			this.prettyResults = prettyResults;
		}

		public void addPrettyResult(String name, String value, boolean isMarkdown) {
			prettyResults.put(name, new FunctionParamValue(value, isMarkdown));
		}

		public String getResultsAsMarkdown() {
			if (getPrettyResults().isEmpty()) {
				return "";
			}
			StringBuilder sb = new StringBuilder("### Results\n");
			for (Map.Entry<String, FunctionParamValue> entry : getPrettyResults().entrySet()) {
				entry.getValue().appendMarkdown(sb, entry.getKey());
			}
			return sb.toString();
		}
	}

	public static class FunctionParamValue {
		private final String value;
		private final boolean isMarkdown;

		public FunctionParamValue(String value, boolean isMarkdown) {
			this.value = value;
			this.isMarkdown = isMarkdown;
		}

		public String getValue() {
			return value;
		}

		public boolean isMarkdown() {
			return isMarkdown;
		}

		public void appendMarkdown(StringBuilder sb, String key) {
			String value = this.getValue();
			String contentIndent = "  "; // Indentation for content lines under the key, relative to the key's base indent.

			sb.append("  **").append(key).append(":** ");

			if (this.isMarkdown()) {
				boolean isAlreadyCodeBlock = value.trim().startsWith("```") && value.trim().endsWith("```");
				sb.append("\n"); // Markdown content always starts on a new line after "key: "
				if (isAlreadyCodeBlock) {
					// Value is already a complete ```code block```
					// Indent each line of the existing code block
					for (String line : value.split("\\r?\\n")) {
						sb.append(contentIndent).append(line).append("\n");
					}
				} else {
					// Value is markdown, but not a pre-formatted code block. Wrap it.
					for (String line : value.split("\\r?\\n")) {
						sb.append(contentIndent).append("> ").append(line).append("\n");
					}
				}
			} else {
				// Not originally markdown
				boolean isShort = value.length() < 120 && !value.contains("\n");
				boolean isNumeric = false;
				try {
					Double.parseDouble(value);
					isNumeric = true;
				} catch (NumberFormatException e) {
					// Not a number
				}

				if (isNumeric || isShort) {
					// Simple, single-line value, append directly after "key: "
					sb.append(value).append("  \n");
				} else {
					// Longer, non-markdown text, format as blockquote on new lines
					sb.append("\n"); // Start blockquote on a new line after "key: "
					for (String line : value.split("\\r?\\n")) {
						sb.append(contentIndent).append("> ").append(line).append("\n");
					}
				}
			}
		}
	}

	private UUID conversationId;
	private String caption;
	private final List<ChatMessage> messages = new ArrayList<>();
	private final Map<ChatOption, Object> options = new HashMap<>();

	// --- Notification mechanism added below ---

	/**
	 * Listener interface for conversation updates.
	 */
	public interface ChatListener {
		/**
		 * Called when a chat message is added.
		 *
		 * @param message  the updated message.
		 * @param updating true if the message is still being updated (streamed).
		 */
		void onMessageAdded(ChatMessage message, boolean updating);

		/**
		 * Called when a chat message is updated.
		 *
		 * @param message the updated message.
		 */
		void onMessageUpdated(ChatMessage message);

		/**
		 * Called when the model requests a function call. This happens when the model's
		 * response includes a function call instruction instead of or in addition to
		 * regular text content.
		 * 
		 * @param message The function details are in message.functionCall.
		 */
		void onFunctionCall(ChatMessage message);

		/**
		 * Called when an async chat response finished updating the message.
		 *
		 * @param message the message that finished updating.
		 */
		void onChatResponseFinished(ChatMessage message);
	}

	private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Adds a listener to receive conversation updates.
	 *
	 * @param listener the listener to add.
	 */
	public void addListener(ChatListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a previously added listener.
	 *
	 * @param listener the listener to remove.
	 */
	public void removeListener(ChatListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Adds a new message to the conversation and notifies listeners.
	 *
	 * @param message the message to add.
	 */
	public void addMessage(ChatMessage message, boolean updating) {
		messages.add(message);
		notifyMessageAdded(message, updating);
	}

	/**
	 * Adds or updates the system prompt message if the prompt is different from the
	 * message content already present.
	 * 
	 * @param prompt The new system prompt.
	 * @return TRUE if the prompt was updated.
	 */
	public boolean addOrReplaceSystemMessage(String prompt) {
		boolean addMessage = false;
		boolean changed = false;
		if (!messages.isEmpty()) {
			ChatMessage first = messages.get(0);
			if (Role.SYSTEM.equals(first.getRole())) {
				if (!StringUtils.equals(prompt, first.getContent())) {
					first.setContent(prompt);
					changed = true;
				}
			} else {
				addMessage = true;
			}
		} else {
			addMessage = true;
		}

		if (addMessage) {
			ChatMessage message = new ChatMessage(Role.SYSTEM, prompt);
			messages.add(0, message);
			changed = true;
		}

		return changed;
	}

	public boolean removeSystemMessage() {
		if (messages.isEmpty()) {
			return false;
		}

		ChatMessage first = messages.get(0);
		if (Role.SYSTEM.equals(first.getRole())) {
			messages.remove(first);
			return true;
		}

		return false;
	}

	/**
	 * Notifies listeners that an existing message has been updated.
	 *
	 * @param message the message that was updated.
	 */
	public void notifyMessageUpdated(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onMessageUpdated(message);
			}
		}
	}

	public void notifyChatResponseFinished(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onChatResponseFinished(message);
			}
		}
	}

	public void notifyMessageAdded(ChatMessage message, boolean updating) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onMessageAdded(message, updating);
			}
		}
	}

	public void notifyFunctionCalled(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onFunctionCall(message);
			}
		}
	}

	/**
	 * Returns the list of messages in the conversation.
	 *
	 * @return the messages.
	 */
	public List<ChatMessage> getMessages() {
		return messages;
	}

	public Map<ChatOption, Object> getOptions() {
		return options;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public void setConversationId(UUID conversationId) {
		this.conversationId = conversationId;
	}

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	@Override
	public String toString() {
		return "ChatConversation [\n" + messages + "\n]";
	}
}
