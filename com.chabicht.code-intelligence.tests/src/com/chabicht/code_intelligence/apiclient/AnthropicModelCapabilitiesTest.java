package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AnthropicModelCapabilitiesTest {

	@Test
	void opus47UsesAdaptiveOnlyNoSamplingParams() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-7");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertFalse(caps.isAllowSamplingParams());
	}

	@Test
	void opus47WithDateSuffixUsesAdaptiveOnly() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-7-20260101");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertFalse(caps.isAllowSamplingParams());
	}

	@Test
	void opus48UsesAdaptiveOnly() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-8");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertFalse(caps.isAllowManualThinking());
		assertFalse(caps.isAllowSamplingParams());
	}

	@Test
	void opus46UsesAdaptiveButAllowsManualAndSampling() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-6");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void sonnet46UsesAdaptiveButAllowsManualAndSampling() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-sonnet-4-6");
		assertTrue(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void opus45IsLegacy() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-opus-4-5-20250929");
		assertFalse(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void claude3ModelIsLegacy() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId("claude-3-5-sonnet-20241022");
		assertFalse(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}

	@Test
	void nullModelIdIsLegacy() {
		AnthropicModelCapabilities caps = AnthropicModelCapabilities.forModelId(null);
		assertFalse(caps.isUseAdaptiveThinkingAndEffort());
		assertTrue(caps.isAllowManualThinking());
		assertTrue(caps.isAllowSamplingParams());
	}
}
