package com.chabicht.code_intelligence.apiclient;

public class AiModel {
	private final AiApiConnection apiConnection;
	private final String id;
	private final String name;

	public AiModel(AiApiConnection apiConnection, String id, String name) {
		this.apiConnection = apiConnection;
		this.id = id;
		this.name = name;
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
