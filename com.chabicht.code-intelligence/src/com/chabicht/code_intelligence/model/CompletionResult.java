package com.chabicht.code_intelligence.model;

public class CompletionResult {
	private final String completion;

	public CompletionResult(String completion) {
		completion = completion.replaceAll("^```[^\n]*\n?", "");
		completion = completion.replaceAll("\n?```$", "");
		this.completion = completion;
	}

	public String getCaption() {
		String comp = completion;
		comp = comp.replaceAll("^\\s*", "");

		// Check if there were multiple lines in the original
		boolean hadMultipleLines = comp.contains("\n");

		// Convert all line breaks to spaces to enforce a single line
		String singleLine = comp.replaceAll("\\r?\\n", " ");

		// If singleLine exceeds 30 chars, truncate and append "..."
		if (singleLine.length() > 30) {
			return singleLine.substring(0, 30) + "...";
		}

		// Otherwise, if the original had multiple lines or was longer than 30,
		// append "..." to indicate truncation/condensation.
		if (hadMultipleLines || comp.length() > 30) {
			return singleLine + "...";
		}

		// Otherwise, return as-is
		return singleLine;
	}

	public String getCompletion() {
		return completion;
	}
}
