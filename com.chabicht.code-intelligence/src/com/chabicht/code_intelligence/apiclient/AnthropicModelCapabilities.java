package com.chabicht.code_intelligence.apiclient;

import java.util.regex.Pattern;

/**
 * Detects per-model API capabilities for Anthropic models.
 *
 * <ul>
 * <li>Opus 4.6 / Sonnet 4.6 — adaptive thinking + effort (budget_tokens deprecated)</li>
 * <li>Opus 4.7+ — adaptive thinking + effort only; no manual thinking, no sampling params</li>
 * <li>Older models — manual thinking with budget_tokens; sampling params allowed</li>
 * </ul>
 */
public class AnthropicModelCapabilities {

	// Opus 4.6, Opus 4.7+, Sonnet 4.6 support adaptive thinking + effort.
	private static final Pattern ADAPTIVE_THINKING_PATTERN = Pattern.compile(
			"claude-opus-4-[6-9]|claude-sonnet-4-6",
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
		if (modelId == null) {
			return new AnthropicModelCapabilities(false, true, true);
		}
		boolean adaptiveAndEffort = ADAPTIVE_THINKING_PATTERN.matcher(modelId).find();
		boolean adaptiveOnly = ADAPTIVE_ONLY_PATTERN.matcher(modelId).find();
		return new AnthropicModelCapabilities(adaptiveAndEffort, !adaptiveOnly, !adaptiveOnly);
	}

	/** True for Opus 4.6, Opus 4.7+, Sonnet 4.6 — use {@code thinking:{type:"adaptive"}} + {@code output_config.effort}. */
	public boolean isUseAdaptiveThinkingAndEffort() {
		return useAdaptiveThinkingAndEffort;
	}

	/** False for Opus 4.7+ — {@code thinking:{type:"enabled",budget_tokens:N}} returns HTTP 400. */
	public boolean isAllowManualThinking() {
		return allowManualThinking;
	}

	/** False for Opus 4.7+ — {@code temperature}/{@code top_p}/{@code top_k} return HTTP 400. */
	public boolean isAllowSamplingParams() {
		return allowSamplingParams;
	}
}
