package com.chabicht.code_intelligence.apiclient;

import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.DefaultPrompts;

public class AiApiConnection extends Bean {
	public static enum ApiType {
		OPENAI("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/settings/organization/api-keys"),
		OLLAMA("Ollama", "http://localhost:11434", ""),
		ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "https://console.anthropic.com/settings/keys"),
		GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "https://aistudio.google.com/app/apikey"),
		XAI("X.ai", "https://api.x.ai/v1", "https://console.x.ai/");

		private ApiType(String label, String defaultBaseUri, String apiKeyUri) {
			this.label = label;
			this.defaultBaseUri = defaultBaseUri;
			this.apiKeyUri = apiKeyUri;
		}

		private final String label;
		private final String defaultBaseUri;
		private final String apiKeyUri;

		public String getName() {
			return label;
		}

		public String getDefaultBaseUri() {
			return defaultBaseUri;
		}

		public String getApiKeyUri() {
			return apiKeyUri;
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
		return getApiClient(false);
	}

	public IAiApiClient getApiClient(boolean force) {
		if (apiClient == null || force) {
			switch (type) {
			case OPENAI:
				apiClient = new OpenAiApiClient(this);
				break;
			case OLLAMA:
				apiClient = new OllamaApiClient(this);
				break;
			case ANTHROPIC:
				apiClient = new AnthropicApiClient(this);
				break;
			case GEMINI:
				apiClient = new GeminiApiClient(this);
				break;
			case XAI:
				apiClient = new XAiApiClient(this);
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

	public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
		if (!enabled) {
			throw new RuntimeException("API connection disabled!");
		}

		getApiClient().performChat(modelName, chat, maxResponseTokens);
	}

	public void abortChat() {
		if (apiClient != null) {
			apiClient.abortChat();
		}
	}

	public boolean isChatPending() {
		return apiClient == null ? false : apiClient.isChatPending();
	}

	public String caption(String modelName, String content) {
		if (!enabled) {
			throw new RuntimeException("API connection disabled!");
		}

		return getApiClient().caption(modelName, DefaultPrompts.CAPTION_PROMPT + content);
	}

}
