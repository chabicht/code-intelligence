package com.chabicht.code_intelligence.apiclient;

import java.util.regex.Pattern;

/**
 * Detects per-model API capabilities for Anthropic models.
 *
 * <ul>
 * <li>Claude 3.x and Claude 4.0 through 4.5 — manual thinking with {@code budget_tokens}</li>
 * <li>All other model IDs — adaptive thinking with an effort setting</li>
 * </ul>
 */
public class AnthropicModelCapabilities {

	// Keep this deliberately narrow. New and unrecognised Anthropic models use adaptive
	// thinking by default; only these established families retain manual token budgets.
	private static final Pattern LEGACY_MANUAL_THINKING_PATTERN = Pattern.compile(
			"claude-3(?:[.-]|\\b)|claude(?:-(?:opus|sonnet|haiku))?-4-[0-5](?!\\d)",
			Pattern.CASE_INSENSITIVE);

	// Opus 4.7+ only: manual thinking (budget_tokens) and sampling params are rejected (HTTP 400).
	private static final Pattern ADAPTIVE_ONLY_PATTERN = Pattern.compile(
			"claude-opus-4-[7-9]\\b",
			Pattern.CASE_INSENSITIVE);

	private final boolean useAdaptiveThinkingAndEffort;
	private final boolean allowManualThinking;
	private final boolean allowSamplingParams;

	private AnthropicModelCapabilities(boolean useAdaptiveThinkingAndEffort, boolean allowManualThinking,
			boolean allowSamplingParams) {
		this.useAdaptiveThinkingAndEffort = useAdaptiveThinkingAndEffort;
		this.allowManualThinking = allowManualThinking;
		this.allowSamplingParams = allowSamplingParams;
	}

	public static AnthropicModelCapabilities forModelId(String modelId) {
		boolean legacyManualThinking = modelId != null
				&& LEGACY_MANUAL_THINKING_PATTERN.matcher(modelId).find();
		boolean adaptiveOnly = modelId != null && ADAPTIVE_ONLY_PATTERN.matcher(modelId).find();
		return new AnthropicModelCapabilities(!legacyManualThinking, legacyManualThinking, !adaptiveOnly);
	}

	/** True unless the model is in the explicit legacy manual-thinking allowlist. */
	public boolean isUseAdaptiveThinkingAndEffort() {
		return useAdaptiveThinkingAndEffort;
	}

	/** True only for models in the explicit legacy manual-thinking allowlist. */
	public boolean isAllowManualThinking() {
		return allowManualThinking;
	}

	/** False for Opus 4.7+ — {@code temperature}/{@code top_p}/{@code top_k} return HTTP 400. */
	public boolean isAllowSamplingParams() {
		return allowSamplingParams;
	}
}
