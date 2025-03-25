package com.chabicht.code_intelligence.util;

public class CodeUtil {
	public CodeUtil() {
		// No instances.
	}

	/**
	 * Removes the common indentation from all lines in the text.
	 * 
	 * @param text The text to process
	 * @return The text with common indentation removed
	 */
	public static String removeCommonIndentation(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		// Split the text into lines
		String[] lines = text.split("\n");

		// Find the minimum indentation
		int minIndent = Integer.MAX_VALUE;
		for (String line : lines) {
			// Skip empty lines when calculating minimum indentation
			if (line.trim().isEmpty()) {
				continue;
			}

			int indent = 0;
			while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
				indent++;
			}

			minIndent = Math.min(minIndent, indent);
		}

		// If minIndent is still MAX_VALUE or 0, no processing needed
		if (minIndent == Integer.MAX_VALUE || minIndent == 0) {
			return text;
		}

		// Remove the common indentation from each line
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.length() >= minIndent) {
				result.append(line.substring(minIndent));
			} else {
				// Keep empty lines as is
				result.append(line);
			}

			// Add newline except for the last line
			if (i < lines.length - 1) {
				result.append("\n");
			}
		}

		return result.toString();
	}

}
