package com.chabicht.code_intelligence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the jmustache template engine as it is used for
 * prompt templates.
 */
public class CompletionPromptTest {

	private static String compile(String template, Map<String, Object> args) {
		return new CompletionPrompt(0.5f, template, args).compile();
	}

	@Test
	public void simpleSubstitution() {
		assertEquals("Hello World!", compile("Hello {{name}}!", Map.of("name", "World")));
	}

	@Test
	public void htmlIsNotEscaped() {
		// The compiler is configured with escapeHTML(false); prompts are plain text.
		assertEquals("<b>bold</b> & <i>x</i>",
				compile("{{value}}", Map.of("value", "<b>bold</b> & <i>x</i>")));
	}

	@Test
	public void tripleMustacheAlsoWorks() {
		assertEquals("<b>bold</b>", compile("{{{value}}}", Map.of("value", "<b>bold</b>")));
	}

	@Test
	public void listSectionIteratesAndDotRefersToTheElement() {
		assertEquals("[a][b][c]", compile("{{#items}}[{{.}}]{{/items}}", Map.of("items", List.of("a", "b", "c"))));
	}

	@Test
	public void sectionOverMapExposesNestedKeys() {
		assertEquals("File.java:12", compile("{{#ctx}}{{file}}:{{line}}{{/ctx}}",
				Map.of("ctx", Map.of("file", "File.java", "line", 12))));
	}

	@Test
	public void falseySectionIsSkippedAndInvertedSectionRenders() {
		Map<String, Object> args = Map.of("flag", Boolean.FALSE, "items", List.of());
		assertEquals("no-flag|no-items",
				compile("{{#flag}}flag{{/flag}}{{^flag}}no-flag{{/flag}}"
						+ "|{{#items}}item{{/items}}{{^items}}no-items{{/items}}", args));
	}

	@Test
	public void surroundingWhitespaceIsStripped() {
		assertEquals("body", compile("\n\n   body   \n\n", Map.of()));
	}

	@Test
	public void nullTemplateCompilesToEmptyString() {
		assertEquals("", compile(null, Map.of()));
	}

	@Test
	public void missingKeyIsAnError() {
		// jmustache is strict about unresolvable variables by default.
		assertThrows(RuntimeException.class, () -> compile("Hello {{missing}}", Map.of("other", "x")));
	}

	@Test
	public void compileResultIsCached() {
		CompletionPrompt prompt = new CompletionPrompt(0.5f, "{{name}}", Map.of("name", "x"));
		String first = prompt.compile();
		assertEquals("x", first);
		assertSame(first, prompt.compile());
	}
}
