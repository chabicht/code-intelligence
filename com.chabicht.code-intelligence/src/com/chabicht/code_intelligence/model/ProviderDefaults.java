package com.chabicht.code_intelligence.model;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;

/**
 * Defines default configuration for AI providers
 */
public class ProviderDefaults {
	private String providerName;
	private ApiType apiType;
	private String baseUri;
	private String apiKeyUri;
	private String defaultCompletionModelId;
	private String defaultChatModelId;

	public ProviderDefaults(String providerName, ApiType apiType, String baseUri, String apiKeyUri,
			String defaultCompletionModelId, String defaultChatModelId) {
		this.providerName = providerName;
		this.apiType = apiType;
		this.baseUri = baseUri;
		this.apiKeyUri = apiKeyUri;
		this.defaultCompletionModelId = defaultCompletionModelId;
		this.defaultChatModelId = defaultChatModelId;
	}

	// Getters
	public String getProviderName() {
		return providerName;
	}

	public ApiType getApiType() {
		return apiType;
	}

	public String getBaseUri() {
		return baseUri;
	}

	public String getApiKeyUri() {
		return apiKeyUri;
	}

	public String getDefaultCompletionModelId() {
		return defaultCompletionModelId;
	}

	public String getDefaultChatModelId() {
		return defaultChatModelId;
	}

	/**
	 * Creates a connection with defaults from this provider
	 */
	public AiApiConnection createConnection() {
		AiApiConnection connection = new AiApiConnection();
		connection.setName(providerName);
		connection.setType(apiType);
		connection.setBaseUri(baseUri);
		connection.setEnabled(true);
		return connection;
	}
}