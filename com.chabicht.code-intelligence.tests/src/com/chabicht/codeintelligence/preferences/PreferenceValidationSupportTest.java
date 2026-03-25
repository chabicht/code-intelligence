package com.chabicht.codeintelligence.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.apiclient.IAiApiClient;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;

public class PreferenceValidationSupportTest {

	@Test
	void normalizeConfiguredModelTrimsWhitespaceAroundSeparator() {
		assertEquals("X.ai/grok-4", PreferenceValidationSupport.normalizeConfiguredModel("  X.ai / grok-4  "));
	}

	@Test
	void validateModelReturnsWarningForUnknownModel() {
		AiApiConnection connection = createConnection("X.ai", true, "grok-3");

		PreferenceValidationSupport.ValidationResult result = PreferenceValidationSupport
				.validateModel(" X.ai / grok-4 ", "Chat Model", List.of(connection));

		assertTrue(result.isWarning());
		assertEquals("Model 'grok-4' not found in connection 'X.ai'", result.message());
	}

	@Test
	void validateModelReturnsOkForKnownEnabledConnection() {
		AiApiConnection connection = createConnection("X.ai", true, "grok-4");

		PreferenceValidationSupport.ValidationResult result = PreferenceValidationSupport
				.validateModel("X.ai/grok-4", "Chat Model", List.of(connection));

		assertTrue(result.isOk());
	}

	@Test
	void validateIntReturnsErrorForNonInteger() {
		PreferenceValidationSupport.ValidationResult result = PreferenceValidationSupport.validateInt("abc",
				"Chat Max Tokens");

		assertTrue(result.isError());
		assertEquals("Chat Max Tokens must be a valid integer", result.message());
	}

	private AiApiConnection createConnection(String name, boolean enabled, String... modelIds) {
		return new AiApiConnection() {
			private final IAiApiClient apiClient = new StubApiClient(this,
					Arrays.stream(modelIds).toList());
			{
				setName(name);
				setType(ApiType.OPENAI);
				setEnabled(enabled);
			}

			@Override
			public IAiApiClient getApiClient() {
				return apiClient;
			}

			@Override
			public IAiApiClient getApiClient(boolean force) {
				return apiClient;
			}
		};
	}

	private static class StubApiClient implements IAiApiClient {
		private final List<AiModel> models;

		StubApiClient(AiApiConnection connection, List<String> modelIds) {
			this.models = modelIds.stream().map(modelId -> new AiModel(connection, modelId, modelId)).toList();
		}

		@Override
		public List<AiModel> getModels() {
			return models;
		}

		@Override
		public CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void performChat(String modelName, ChatConversation chat, int maxResponseTokens) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void abortChat() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isChatPending() {
			return false;
		}

		@Override
		public String caption(String modelName, String content) {
			throw new UnsupportedOperationException();
		}
	}
}
