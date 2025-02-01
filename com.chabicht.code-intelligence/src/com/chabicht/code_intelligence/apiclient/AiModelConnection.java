package com.chabicht.code_intelligence.apiclient;

import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.CompletionPrompt;

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
}
