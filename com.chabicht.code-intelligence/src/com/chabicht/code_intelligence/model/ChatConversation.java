package com.chabicht.code_intelligence.model;

import java.util.ArrayList;
import java.util.List;

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
		private final Role role;
		private String content;
		private final List<MessageContext> context = new ArrayList<>();

		public ChatMessage(Role role, String content) {
			this.role = role;
			this.content = content;
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

	public List<ChatMessage> getMessages() {
		return messages;
	}
}
