package com.chabicht.code_intelligence.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		SYSTEM, USER, ASSISTANT;
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
		REASONING_ENABLED, REASONING_BUDGET_TOKENS;
	}

	/**
	 * Context information added to a message.
	 */
	public static class MessageContext {
		private final String fileName;
		private final RangeType rangeType;
		private final int start;
		private final int end;
		private final String content;

		public MessageContext(String fileName, int startLine, int endLine, String content) {
			this(fileName, RangeType.LINE, startLine, endLine, content);
		}

		public MessageContext(String fileName, RangeType rangeType, int start, int end, String content) {
			this.fileName = fileName;
			this.rangeType = rangeType;
			this.start = start;
			this.end = end;
			this.content = content;
		}

		public boolean isDuplicate(MessageContext other) {
			if (other != null && StringUtils.equals(this.getFileName(), other.getFileName())
					&& this.getRangeType().equals(other.getRangeType()) && this.getStart() == other.getStart()
					&& this.getEnd() == other.getEnd()) {
				return true;
			}
			return false;
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

		public String getDescriptor() {
			return new StringBuilder().append("// ").append(this.getFileName()).append(" ")
					.append(this.getRangeDescription()).append("\n").toString();
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

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("ChatMessage {").append("\n");
			sb.append("  id=").append(id);
			sb.append(", role=").append(role).append("\n");
			sb.append("  content:\n  ===\n").append(content).append("\n  ===\n");
			if (context != null && !context.isEmpty()) {
				sb.append("  context=").append(context).append("\n");
			} else {
				sb.append(", context=[]");
			}
			sb.append(" }").append("\n");
			return sb.toString();
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
		 * @param message the updated message.
		 */
		void onMessageAdded(ChatMessage message);

		/**
		 * Called when a chat message is updated.
		 *
		 * @param message the updated message.
		 */
		void onMessageUpdated(ChatMessage message);

		/**
		 * Called when an async chat response finished updating the message.
		 * 
		 * @param message
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
		for (ChatMessage msg : this.messages) {
			listener.onMessageAdded(msg);
		}
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
	public void addMessage(ChatMessage message) {
		messages.add(message);
		notifyMessageAdded(message);
	}

	/**
	 * Notifies listeners that an existing message has been updated.
	 *
	 * @param message the message that was updated.
	 */
	public void notifyMessageUpdated(ChatMessage message) {
		messageUpdated(message);
	}

	public void notifyChatResponseFinished(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onChatResponseFinished(message);
			}
		}
	}

	public void notifyMessageAdded(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onMessageAdded(message);
			}
		}
	}

	private void messageUpdated(ChatMessage message) {
		for (ChatListener listener : listeners) {
			if (listener != null) {
				listener.onMessageUpdated(message);
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
