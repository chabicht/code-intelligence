package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningControlMode;
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningEffort;

public class ChatSettingsTest {

	@Test
	void getReasoningControlModeUsesConnectionTypeFamilies() {
		assertEquals(ReasoningControlMode.EFFORT, ChatSettings.getReasoningControlMode(ApiType.OPENAI));
		assertEquals(ReasoningControlMode.EFFORT, ChatSettings.getReasoningControlMode(ApiType.OPENAI_RESPONSES));
		assertEquals(ReasoningControlMode.TOKEN_BUDGET, ChatSettings.getReasoningControlMode(ApiType.ANTHROPIC));
		assertEquals(ReasoningControlMode.TOKEN_BUDGET, ChatSettings.getReasoningControlMode(ApiType.GEMINI));
		assertEquals(ReasoningControlMode.OLLAMA_EFFORT, ChatSettings.getReasoningControlMode(ApiType.OLLAMA));
		assertEquals(ReasoningControlMode.NONE, ChatSettings.getReasoningControlMode(ApiType.XAI));
	}

	@Test
	void getSupportedReasoningEffortsRestrictsOllamaChoices() {
		assertArrayEquals(
				new ReasoningEffort[] { ReasoningEffort.DEFAULT, ReasoningEffort.NONE, ReasoningEffort.LOW,
						ReasoningEffort.MEDIUM, ReasoningEffort.HIGH },
				ChatSettings.getSupportedReasoningEfforts(ReasoningControlMode.OLLAMA_EFFORT));
		assertArrayEquals(ReasoningEffort.values(),
				ChatSettings.getSupportedReasoningEfforts(ReasoningControlMode.EFFORT));
	}

	@Test
	void normalizeReasoningEffortClampsUnsupportedOllamaValues() {
		assertEquals(ReasoningEffort.LOW,
				ChatSettings.normalizeReasoningEffort(ReasoningControlMode.OLLAMA_EFFORT, ReasoningEffort.MINIMAL));
		assertEquals(ReasoningEffort.HIGH,
				ChatSettings.normalizeReasoningEffort(ReasoningControlMode.OLLAMA_EFFORT, ReasoningEffort.XHIGH));
		assertEquals(ReasoningEffort.DEFAULT,
				ChatSettings.normalizeReasoningEffort(ReasoningControlMode.OLLAMA_EFFORT, null));
		assertEquals(ReasoningEffort.HIGH,
				ChatSettings.normalizeReasoningEffort(ReasoningControlMode.EFFORT, ReasoningEffort.HIGH));
	}

	@Test
	void isReasoningSupportedAndEnabledUsesExplicitEffortForOpenAiStyleConnections() {
		ChatSettings settings = new ChatSettings() {
			@Override
			public ReasoningControlMode getReasoningControlMode() {
				return ReasoningControlMode.EFFORT;
			}
		};

		settings.setReasoningEffort(ReasoningEffort.DEFAULT);
		assertFalse(settings.isReasoningSupportedAndEnabled());

		settings.setReasoningEffort(ReasoningEffort.NONE);
		assertTrue(settings.isReasoningSupportedAndEnabled());

		settings.setReasoningEffort(ReasoningEffort.HIGH);
		assertTrue(settings.isReasoningSupportedAndEnabled());
	}

	@Test
	void isReasoningSupportedAndEnabledUsesCheckboxForTokenBudgetConnections() {
		ChatSettings settings = new ChatSettings() {
			@Override
			public ReasoningControlMode getReasoningControlMode() {
				return ReasoningControlMode.TOKEN_BUDGET;
			}
		};

		settings.setReasoningEnabled(false);
		assertFalse(settings.isReasoningSupportedAndEnabled());

		settings.setReasoningEnabled(true);
		assertTrue(settings.isReasoningSupportedAndEnabled());
	}

	@Test
	void isReasoningSupportedAndEnabledUsesExplicitEffortForOllamaConnections() {
		ChatSettings settings = new ChatSettings() {
			@Override
			public ReasoningControlMode getReasoningControlMode() {
				return ReasoningControlMode.OLLAMA_EFFORT;
			}
		};

		settings.setReasoningEffort(ReasoningEffort.DEFAULT);
		assertFalse(settings.isReasoningSupportedAndEnabled());

		settings.setReasoningEffort(ReasoningEffort.NONE);
		assertTrue(settings.isReasoningSupportedAndEnabled());

		settings.setReasoningEffort(ReasoningEffort.XHIGH);
		assertEquals(ReasoningEffort.HIGH, settings.getEffectiveReasoningEffort());
		assertTrue(settings.isReasoningSupportedAndEnabled());
	}
}
