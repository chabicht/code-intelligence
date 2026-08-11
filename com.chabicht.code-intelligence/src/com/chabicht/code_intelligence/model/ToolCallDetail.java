package com.chabicht.code_intelligence.model;

/**
 * How much detail tool calls are rendered with when a conversation is exported.
 */
public enum ToolCallDetail {
	/** Parameters and results in full. */
	DETAILED,

	/**
	 * Parameters and results, but bulky values (long or multi-line, e.g. patches or
	 * file contents) are replaced by a placeholder.
	 */
	BRIEF,

	/** Tool calls are omitted entirely. */
	OFF;

	public static ToolCallDetail fromString(String value, ToolCallDetail fallback) {
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		try {
			return valueOf(value);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
