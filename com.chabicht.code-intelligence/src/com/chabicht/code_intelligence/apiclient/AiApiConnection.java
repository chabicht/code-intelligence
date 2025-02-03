package com.chabicht.code_intelligence.apiclient;

import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;

public class AiApiConnection extends Bean {
	public static enum ApiType {
		OPENAI("OpenAI"), OLLAMA("Ollama");

		private ApiType(String label) {
			this.label = label;
		}

		private String label;

		public String getName() {
			return label;
		}
	}

	private String name;
	private ApiType type;
	private String baseUri;
	private String apiKey;
	private boolean enabled;

	private transient IAiApiClient apiClient;

	public AiApiConnection() {
		type = ApiType.OPENAI;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		propertyChangeSupport.firePropertyChange("name", this.name, this.name = name);
	}

	public ApiType getType() {
		return type;
	}

	public void setType(ApiType type) {
		propertyChangeSupport.firePropertyChange("type", this.type, this.type = type);
	}

	public String getBaseUri() {
		return baseUri;
	}

	public void setBaseUri(String baseUri) {
		propertyChangeSupport.firePropertyChange("baseUri", this.baseUri, this.baseUri = baseUri);
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		propertyChangeSupport.firePropertyChange("apiKey", this.apiKey, this.apiKey = apiKey);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		propertyChangeSupport.firePropertyChange("enabled", this.enabled, this.enabled = enabled);
	}

	public IAiApiClient getApiClient() {
		if (apiClient == null) {
			switch (type) {
			case OPENAI:
				apiClient = new OpenAiApiClient(this);
				break;
			case OLLAMA:
				apiClient = new OllamaApiClient(this);
				break;
			default:
				throw new RuntimeException("Unsupported API type: " + type);
			}
		}
		return apiClient;
	}

	public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
		if (!enabled) {
			throw new RuntimeException("API connection disabled!");
		}

		return getApiClient().performCompletion(modelName, completionPrompt);
	}

	public void performChat(String modelName, ChatConversation chat) {
		if (!enabled) {
			throw new RuntimeException("API connection disabled!");
		}

		getApiClient().performChat(modelName, chat);
	}

	public void abortChat() {
		if (apiClient != null) {
			apiClient.abortChat();
		}
	}

	public boolean isChatPending() {
		return apiClient == null ? false : apiClient.isChatPending();
	}

}
