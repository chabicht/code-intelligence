package com.chabicht.code_intelligence.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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

	/**
	 * Context information added to a message.
	 */
	public static class MessageContext {
		private final String fileName;
		private final int startLine;
		private final int endLine;
		private final String content;

		public MessageContext(String fileName, int startLine, int endLine, String content) {
			this.fileName = fileName;
			this.startLine = startLine;
			this.endLine = endLine;
			this.content = content;
		}

		public String getFileName() {
			return fileName;
		}

		public int getStartLine() {
			return startLine;
		}

		public int getEndLine() {
			return endLine;
		}

		public String getContent() {
			return content;
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
	}

	private final List<ChatMessage> messages = new ArrayList<>();

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
		messageAdded(message);
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
			listener.onChatResponseFinished(message);
		}
	}

	private void messageAdded(ChatMessage message) {
		for (ChatListener listener : listeners) {
			listener.onMessageAdded(message);
		}
	}

	private void messageUpdated(ChatMessage message) {
		for (ChatListener listener : listeners) {
			listener.onMessageUpdated(message);
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
}
