package com.chabicht.code_intelligence.apiclient;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;

public class AiModelConnection {
	private final AiApiConnection conn;
	private final String modelName;

	public AiModelConnection(AiApiConnection conn, String modelName) {
		this.conn = conn;
		this.modelName = modelName;
	}

	public AiApiConnection getConn() {
		return conn;
	}

	public String getModelName() {
		return modelName;
	}

	public CompletionResult complete(CompletionPrompt completionPrompt) {
		return conn.performCompletion(modelName, completionPrompt);
	}

	public void chat(ChatConversation chat) {
		conn.performChat(modelName, chat);
	}

	public String caption(String content) {
		return conn.caption(modelName, content);
	}

	public void abortChat() {
		conn.abortChat();
	}

	public boolean isChatPending() {
		return conn.isChatPending();
	}
}
