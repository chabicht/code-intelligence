package com.chabicht.code_intelligence.model;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

public class CompletionResult {
	
	private static final String THINK_START_TAG = "<think>";
	private static final String THINK_END_TAG = "</think>";
	private static final Pattern codeBlockMarkup = Pattern.compile("^\\s*```[^\n]*\n|\n```\\s*$|^\\s*`|`\\s*$");

	private final String rawResult;
	private final String completion;
	private final String thought;
	
	public CompletionResult(String completion) {
		this.rawResult = completion;
		
		String thought = null;
		int start = completion.indexOf(THINK_START_TAG);
		if (start >= 0) {
			int end = completion.lastIndexOf(THINK_END_TAG);
			if (end >= 0) {
				thought = completion.substring(start + THINK_START_TAG.length(), end).strip();
				completion = completion.substring(0, start) + completion.substring(end + THINK_END_TAG.length());
			}
		}
		
		completion = codeBlockMarkup.matcher(completion).replaceAll("");
		
		this.completion = completion;
		this.thought = thought;
	}

	public String getRawResult() {
		return rawResult;
	}
	
	public String getCaption() {
		String comp = completion.strip();

		// Check if there were multiple lines in the original
		boolean hadMultipleLines = comp.contains("\n");

		// Convert all line breaks to spaces to enforce a single line
		String singleLine = comp.replaceAll("\\r?\\n", " ");

		// If singleLine exceeds 30 chars, truncate and append "..."
		if (singleLine.length() > 60) {
			return singleLine.substring(0, 60) + "...";
		}

		// Otherwise, if the original had multiple lines or was longer than 60,
		// append "..." to indicate truncation/condensation.
		if (hadMultipleLines || comp.length() > 60) {
			return singleLine + "...";
		}

		// Otherwise, return as-is
		return singleLine;
	}

	public String getCompletion() {
		return completion;
	}
	
	public String getThought() {
		return thought;
	}
	
	public String getDescription() {
		String comp = "<pre>" + completion + "</pre>";
		if (thought != null && !thought.isEmpty())
			return comp + "<br><br>" + StringEscapeUtils.escapeHtml3(thought).replace("\n", "<br>");
		else
			return comp;
	}
}
