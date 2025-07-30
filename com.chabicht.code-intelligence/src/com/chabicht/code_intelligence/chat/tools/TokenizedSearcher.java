package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Search for a needle text in a haystack based on exact token sequence
 * matching. Supports C-like programming languages and falls back to general
 * text matching.
 */
public class TokenizedSearcher {

	public enum Language {
		C_LIKE(new String[] { "org.eclipse.jdt.core.javaSource", "org.eclipse.cdt.core.cSource",
				"org.eclipse.cdt.core.cxxSource", "org.eclipse.cdt.core.cppSource",
				"org.eclipse.wst.jsdt.core.jsSource" }),
		GENERAL_TEXT(new String[] { "org.eclipse.core.runtime.text" });

		private final String[] contentTypeIds;

		Language(String[] contentTypeIds) {
			this.contentTypeIds = contentTypeIds;
		}

		public String[] getContentTypeIds() {
			return contentTypeIds;
		}

		public static Language of(String contentType) {
			for (Language lang : Language.values()) {
				for (String contentTypeId : lang.getContentTypeIds()) {
					if (contentTypeId.equalsIgnoreCase(contentType)) {
						return lang;
					}
				}
			}
			return GENERAL_TEXT;
		}
	}

	/**
	 * Configuration for the tokenized search process.
	 */
	public static class Config {
		private Language language = Language.GENERAL_TEXT;
		private boolean ignoreComments = false;
		private boolean ignoreStringLiterals = false;
		private boolean caseSensitive = true;
		private boolean requireCompleteTokenMatch = true;
		private boolean treatNumbersAsGeneric = false; // If true, all numbers match each other

		private boolean treatCommentsAsGeneric = true; // If true, all comments match each other

		public Config() {
		}

		public Config setLanguage(Language language) {
			this.language = language;
			return this;
		}

		public Config setIgnoreComments(boolean ignore) {
			this.ignoreComments = ignore;
			return this;
		}

		public Config setIgnoreStringLiterals(boolean ignore) {
			this.ignoreStringLiterals = ignore;
			return this;
		}

		public Config setCaseSensitive(boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
			return this;
		}

		public Config setRequireCompleteTokenMatch(boolean require) {
			this.requireCompleteTokenMatch = require;
			return this;

		}

		public Config setTreatCommentsAsGeneric(boolean generic) {
			this.treatCommentsAsGeneric = generic;
			return this;
		}

		public Config setTreatNumbersAsGeneric(boolean generic) {
			this.treatNumbersAsGeneric = generic;
			return this;
		}
	}

	/**
	 * Language-specific tokenization rules.
	 */
	private static class LanguageSpec {
		final Set<String> keywords;
		final Pattern tokenPattern;

		LanguageSpec(Set<String> keywords, String commentPattern, String stringPattern, String numberPattern) {
			this.keywords = keywords;

			// Build complete token pattern
			String operatorPattern = "\\+\\+|--|\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=|>>>|==|!=|<=|>=|&&|\\|\\||<<|>>|->|::|\\?\\?";
			String punctuationPattern = "[{}\\[\\]();,.]";
			String identifierPattern = "[a-zA-Z_$][a-zA-Z0-9_$]*";
			String singleOpPattern = "[+\\-*/%&|^~!<>=?:]";
			String whitespacePattern = "\\s++";

			this.tokenPattern = Pattern.compile("(?<comment>" + commentPattern + ")" + "|" + "(?<string>"
					+ stringPattern + ")" + "|" + "(?<number>" + numberPattern + ")" + "|" + "(?<operator>"
					+ operatorPattern + ")" + "|" + "(?<punctuation>" + punctuationPattern + ")" + "|"
					+ "(?<identifier>" + identifierPattern + ")" + "|" + "(?<singleop>" + singleOpPattern + ")" + "|"
					+ "(?<whitespace>" + whitespacePattern + ")", Pattern.MULTILINE | Pattern.DOTALL);
		}
	}

	/**
	 * Represents a token with its position in the original text.
	 */
	private static class Token {
		final String value;
		final int startPos;
		final int endPos;
		final TokenType type;

		Token(String value, int startPos, int endPos, TokenType type) {
			this.value = value;
			this.startPos = startPos;
			this.endPos = endPos;
			this.type = type;
		}

		@Override
		public String toString() {
			return String.format("%s[%d:%d]='%s'", type, startPos, endPos, value);
		}
	}

	private enum TokenType {
		IDENTIFIER, KEYWORD, OPERATOR, PUNCTUATION, NUMBER, STRING, COMMENT, WHITESPACE, WORD
	}

	private final Config config;
	private static final Map<Language, LanguageSpec> LANGUAGE_SPECS = new HashMap<>();

	static {
		// C-like keywords (combination of Java, C, C++, JavaScript)
		Set<String> cLikeKeywords = Set.of(
				// Common keywords across C-like languages
				"abstract", "auto", "break", "case", "catch", "char", "class", "const", "continue", "default", "delete",
				"do", "double", "else", "enum", "extern", "false", "final", "finally", "float", "for", "function",
				"goto", "if", "import", "in", "inline", "instanceof", "int", "interface", "let", "long", "namespace",
				"new", "null", "package", "private", "protected", "public", "return", "short", "signed", "sizeof",
				"static", "struct", "super", "switch", "template", "this", "throw", "throws", "true", "try", "typedef",
				"typeof", "union", "unsigned", "var", "void", "volatile", "while", "with");

		// Comment and string patterns for C-like languages
		String cLikeComments = "//.*?$|/\\*.*?\\*/";
		// Enhanced string patterns including triple quotes and backticks
		String cLikeStrings = "\"\"\".*?\"\"\"|'''.*?'''|`(?:[^`\\\\]|\\\\.)*`|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'";
		String cLikeNumbers = "0[xX][0-9a-fA-F]+[lLuU]*|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlLuU]*";

		LANGUAGE_SPECS.put(Language.C_LIKE, new LanguageSpec(cLikeKeywords, cLikeComments, cLikeStrings, cLikeNumbers));
	}

	public TokenizedSearcher() {
		this.config = new Config();
	}

	public TokenizedSearcher(Config config) {
		this.config = config;
	}

	/**
	 * Finds the region in `textToSearchIn` that best matches the `patternToSearch`
	 * using exact token sequence matching.
	 */
	public int[] findMatchingRegion(String patternToSearch, String textToSearchIn) {
		if (patternToSearch == null || textToSearchIn == null) {
			return null;
		}

		List<Token> patternTokens = tokenize(patternToSearch, config.language);
		List<Token> textTokens = tokenize(textToSearchIn, config.language);

		// Filter tokens based on configuration
		patternTokens = filterTokens(patternTokens);
		textTokens = filterTokens(textTokens);

		if (patternTokens.isEmpty()) {
			return null;
		}

		// Find the token sequence match
		int matchIndex = findTokenSequence(patternTokens, textTokens);
		if (matchIndex == -1) {
			return null;
		}

		// Map back to original text positions
		int startPos = textTokens.get(matchIndex).startPos;
		int endPos = textTokens.get(matchIndex + patternTokens.size() - 1).endPos;

		return new int[] { startPos, endPos };
	}

	/**
	 * Tokenizes the input text based on the configured language.
	 */
	private List<Token> tokenize(String text, Language language) {
		if (language == Language.GENERAL_TEXT) {
			return tokenizeGeneralText(text);
		}

		LanguageSpec spec = LANGUAGE_SPECS.get(language);
		if (spec == null) {
			return tokenizeGeneralText(text);
		}

		List<Token> tokens = new ArrayList<>();
		Matcher matcher = spec.tokenPattern.matcher(text);

		while (matcher.find()) {
			String value = matcher.group();
			int start = matcher.start();
			int end = matcher.end();

			TokenType type = determineTokenType(matcher, spec, value);
			tokens.add(new Token(value, start, end, type));
		}

		return tokens;
	}

	/**
	 * Tokenizes general text (non-source code) by splitting on whitespace and
	 * punctuation.
	 */
	private List<Token> tokenizeGeneralText(String text) {
		List<Token> tokens = new ArrayList<>();
		Pattern pattern = Pattern.compile("\\S++|\\s++");
		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			String value = matcher.group();
			int start = matcher.start();
			int end = matcher.end();

			TokenType type = Character.isWhitespace(value.charAt(0)) ? TokenType.WHITESPACE : TokenType.WORD;
			tokens.add(new Token(value, start, end, type));
		}

		return tokens;
	}

	/**
	 * Determines the token type based on regex groups and language spec.
	 */
	private TokenType determineTokenType(Matcher matcher, LanguageSpec spec, String value) {
		if (matcher.group("comment") != null) {
			return TokenType.COMMENT;
		} else if (matcher.group("string") != null) {
			return TokenType.STRING;
		} else if (matcher.group("number") != null) {
			return TokenType.NUMBER;
		} else if (matcher.group("operator") != null || matcher.group("singleop") != null) {
			return TokenType.OPERATOR;
		} else if (matcher.group("punctuation") != null) {
			return TokenType.PUNCTUATION;
		} else if (matcher.group("identifier") != null) {
			String normalizedValue = config.caseSensitive ? value : value.toLowerCase();
			return spec.keywords.contains(normalizedValue) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
		} else if (matcher.group("whitespace") != null) {
			return TokenType.WHITESPACE;
		} else {
			return TokenType.IDENTIFIER;
		}
	}

	/**
	 * Filters tokens based on configuration settings.
	 */
	private List<Token> filterTokens(List<Token> tokens) {
		return tokens.stream().filter(token -> {
			switch (token.type) {
			case COMMENT:
				return !config.ignoreComments;
			case STRING:
				return !config.ignoreStringLiterals;
			case WHITESPACE:
				return false; // Always ignore whitespace for matching
			default:
				return true;
			}
		}).collect(ArrayList::new, (list, token) -> {
			// Normalize case if needed
			String value = config.caseSensitive ? token.value : token.value.toLowerCase();
			list.add(new Token(value, token.startPos, token.endPos, token.type));
		}, ArrayList::addAll);
	}

	/**
	 * Finds the first occurrence of patternTokens sequence in textTokens.
	 */
	private int findTokenSequence(List<Token> patternTokens, List<Token> textTokens) {
		if (patternTokens.size() > textTokens.size()) {
			return -1;
		}

		for (int i = 0; i <= textTokens.size() - patternTokens.size(); i++) {
			boolean matches = true;
			for (int j = 0; j < patternTokens.size(); j++) {
				if (!tokensEqual(patternTokens.get(j), textTokens.get(i + j))) {
					matches = false;
					break;
				}
			}
			if (matches) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Checks if two tokens are equal based on configuration.
	 */
	private boolean tokensEqual(Token token1, Token token2) {
		// Special handling for numbers if configured to treat them generically
		if (config.treatNumbersAsGeneric && token1.type == TokenType.NUMBER && token2.type == TokenType.NUMBER) {
			return true;
		}

		// Special handling for comments if configured to treat them generically
		if (config.treatCommentsAsGeneric && token1.type == TokenType.COMMENT && token2.type == TokenType.COMMENT) {
			return true;
		}

		if (token1.type != token2.type) {
			return false;
		}

		if (config.requireCompleteTokenMatch) {
			return token1.value.equals(token2.value);
		} else {
			// Allow partial matches
			return token1.value.contains(token2.value) || token2.value.contains(token1.value);
		}
	}

	/**
	 * Finds all matching regions.
	 */
	public List<int[]> findAllMatchingRegions(String patternToSearch, String textToSearchIn) {
		if (patternToSearch == null || textToSearchIn == null) {
			return Collections.emptyList();
		}

		List<Token> patternTokens = tokenize(patternToSearch, config.language);
		List<Token> textTokens = tokenize(textToSearchIn, config.language);

		patternTokens = filterTokens(patternTokens);
		textTokens = filterTokens(textTokens);

		if (patternTokens.isEmpty()) {
			return Collections.emptyList();
		}

		List<int[]> matches = new ArrayList<>();

		for (int i = 0; i <= textTokens.size() - patternTokens.size(); i++) {
			boolean isMatch = true;
			for (int j = 0; j < patternTokens.size(); j++) {
				if (!tokensEqual(patternTokens.get(j), textTokens.get(i + j))) {
					isMatch = false;
					break;
				}
			}

			if (isMatch) {
				int startPos = textTokens.get(i).startPos;
				int endPos = textTokens.get(i + patternTokens.size() - 1).endPos;
				matches.add(new int[] { startPos, endPos });
			}
		}

		return matches;
	}

	/**
	 * Debug method to show tokenization.
	 */
	public Map<String, Object> getDebugInfo(String text) {
		List<Token> tokens = tokenize(text, config.language);
		List<Token> filtered = filterTokens(tokens);

		Map<String, Object> info = new HashMap<>();
		info.put("language", config.language);
		info.put("contentTypeIds", Arrays.asList(config.language.getContentTypeIds()));
		info.put("totalTokens", tokens.size());
		info.put("filteredTokens", filtered.size());
		info.put("tokens", filtered.stream().map(token -> token.value + " (" + token.type + ")").collect(ArrayList::new,
				ArrayList::add, ArrayList::addAll));

		return info;
	}
}