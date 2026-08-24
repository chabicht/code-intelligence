package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AnthropicModelCapabilitiesTest {

	@Test
	void fable5UsesAdaptiveThinkingByDefault() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-fable-5");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void unlistedNewerModelUsesAdaptiveThinking() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-sonnet-5-20270101");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void opus47RemainsAdaptiveOnlyForSamplingParameters() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-7");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertFalse(caps.isAllowSamplingParams());
	}

	@Test
	void opus46UsesAdaptiveThinking() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-6");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void claude40Through45AreLegacy() {
		for (int minorVersion = 0; minorVersion <= 5; minorVersion++) {
			AnthropicModelCapabilities caps = AnthropicModelCapabilities
					.forModelId("claude-opus-4-" + minorVersion + "-20250929");
			assertFalse(caps.isUseAdaptiveThinkingAndEffort());
			assertTrue(caps.isAllowManualThinking());
			assertTrue(caps.isAllowSamplingParams());
		}
	}

	@Test
	void claude3ModelIsLegacy() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-3-5-sonnet-20241022");
		assertFalse(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void nullModelIdDefaultsToAdaptiveThinking() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId(null);
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}
}
