package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the commons-text escaping used by the chat view
 * when it builds tool-call and attachment HTML.
 *
 * <p>
 * Besides pinning the escaping behaviour, these tests also act as a guard
 * against a commons-text version that needs a newer commons-lang3 than the
 * target platform provides: such a mismatch shows up here as a
 * {@code NoSuchMethodError} / {@code NoClassDefFoundError}.
 */
public class HtmlEscapingTest {

	@Test
	public void escapeHtml4EscapesMarkupCharacters() {
		assertEquals("&lt;div&gt;", StringEscapeUtils.escapeHtml4("<div>"));
		assertEquals("a &amp; b", StringEscapeUtils.escapeHtml4("a & b"));
		assertEquals("&quot;quoted&quot;", StringEscapeUtils.escapeHtml4("\"quoted\""));
	}

	@Test
	public void escapeHtml4LeavesApostropheAlone() {
		assertEquals("it's", StringEscapeUtils.escapeHtml4("it's"));
	}

	@Test
	public void escapeHtml4DoubleEscapesExistingEntities() {
		assertEquals("&amp;lt;", StringEscapeUtils.escapeHtml4("&lt;"));
	}

	@Test
	public void escapeHtml4TranslatesNamedEntitiesForNonAscii() {
		// html4 knows named entities for latin-1 supplement characters.
		assertEquals("Gr&uuml;&szlig;e", StringEscapeUtils.escapeHtml4("Grüße"));
	}

	@Test
	public void escapeHtml4LeavesCharactersWithoutNamedEntityUnchanged() {
		// The full html4 entity set includes the symbol entities.
		assertEquals("&rarr;", StringEscapeUtils.escapeHtml4("→"));
		// Emoji (surrogate pair) has no entity and must survive untouched.
		assertEquals("🚀", StringEscapeUtils.escapeHtml4("🚀"));
	}

	@Test
	public void escapeHtml3IsLimitedToTheHtml3EntitySet() {
		assertEquals("&lt;b&gt;", StringEscapeUtils.escapeHtml3("<b>"));
		assertEquals("Gr&uuml;&szlig;e", StringEscapeUtils.escapeHtml3("Grüße"));
		// The euro sign has an entity in html4 but not in html3.
		assertEquals("&euro;", StringEscapeUtils.escapeHtml4("€"));
		assertEquals("€", StringEscapeUtils.escapeHtml3("€"));
	}

	@Test
	public void emptyAndNullInput() {
		assertEquals("", StringEscapeUtils.escapeHtml4(""));
		assertNull(StringEscapeUtils.escapeHtml4(null));
		assertNull(StringEscapeUtils.escapeHtml3(null));
	}

	@Test
	public void newlinesAndTabsAreNotEscaped() {
		assertEquals("a\n\tb", StringEscapeUtils.escapeHtml4("a\n\tb"));
	}
}
