package com.chabicht.code_intelligence.chat.tools.fuzzydiff;

import java.util.Objects;

/**
 * A class representing a line of text that is compared in a fuzzy way with
 * other fuzzy lines.
 */
public class FuzzyLine implements java.io.Serializable, Comparable<FuzzyLine>, CharSequence {
	private final String originalLine;

	public FuzzyLine(String originalLine) {
		this.originalLine = originalLine;
	}

	@Override
	public String toString() {
		return originalLine;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof FuzzyLine fl) {
			return fl.originalLine.trim().equalsIgnoreCase(this.originalLine);
		} else {
			return Objects.equals(this.originalLine, obj);
		}
	}

	@Override
	public int length() {
		return originalLine == null ? -1 : originalLine.length();
	}

	@Override
	public char charAt(int index) {
		return originalLine == null ? 0 : originalLine.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return originalLine == null ? "" : originalLine.subSequence(start, end);
	}

	@Override
	public int compareTo(FuzzyLine o) {
		// Handle null cases for originalLine strings
		if (this.originalLine == null && o.originalLine == null) {
			return 0; // Both are null, consider them equal
		} else if (this.originalLine == null) {
			return -1; // This line's originalLine is null, so it's "less than" a non-null originalLine
		} else if (o.originalLine == null) {
			return 1; // Other line's originalLine is null, so this non-null originalLine is "greater than" it
		} else {
			// Both originalLine strings are non-null, delegate to String's compareTo
			return this.originalLine.compareTo(o.originalLine);
		}
	}
}
