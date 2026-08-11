package com.chabicht.code_intelligence.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the commonmark rendering pipeline used throughout
 * the chat UI. These pin down the HTML the embedded commonmark jars produce so
 * that library upgrades surface as test failures rather than as subtle
 * rendering glitches.
 */
public class MarkdownUtilTest {

	private static String render(String markdown) {
		return MarkdownUtil.createRenderer().render(MarkdownUtil.createParser().parse(markdown));
	}

	@Test
	public void headingsAndParagraphs() {
		assertEquals("<h1>Title</h1>\n", render("# Title"));
		assertEquals("<h3>Sub</h3>\n", render("### Sub"));
		assertEquals("<p>Just text.</p>\n", render("Just text."));
	}

	@Test
	public void emphasisAndInlineCode() {
		assertEquals("<p><strong>bold</strong> and <em>italic</em></p>\n", render("**bold** and *italic*"));
		assertEquals("<p>call <code>foo()</code> now</p>\n", render("call `foo()` now"));
	}

	@Test
	public void hardLineBreak() {
		assertEquals("<p>first<br />\nsecond</p>\n", render("first  \nsecond"));
	}

	@Test
	public void fencedCodeBlockWithLanguage() {
		String html = render("```java\nint i = 1;\n```");
		assertEquals("<pre><code class=\"language-java\">int i = 1;\n</code></pre>\n", html);
	}

	@Test
	public void fencedCodeBlockWithoutLanguage() {
		assertEquals("<pre><code>plain\n</code></pre>\n", render("```\nplain\n```"));
	}

	@Test
	public void codeBlockContentIsHtmlEscaped() {
		String html = render("```\n<div a=\"b\"> & more\n```");
		assertTrue(html.contains("&lt;div a=&quot;b&quot;&gt; &amp; more"), html);
	}

	@Test
	public void linksAndImages() {
		assertEquals("<p><a href=\"https://example.com\">text</a></p>\n", render("[text](https://example.com)"));
		assertEquals("<p><img src=\"img.png\" alt=\"alt text\" /></p>\n", render("![alt text](img.png)"));
	}

	@Test
	public void blockQuote() {
		assertEquals("<blockquote>\n<p>quoted</p>\n</blockquote>\n", render("> quoted"));
	}

	@Test
	public void nestedLists() {
		String html = render("- a\n- b\n  - b1\n\n1. one\n2. two");
		assertTrue(html.contains("<ul>"), html);
		assertTrue(html.contains("<li>a</li>"), html);
		assertTrue(html.contains("<ol>"), html);
		assertTrue(html.contains("<li>one</li>"), html);
		// The nested bullet must produce a list inside a list item.
		assertTrue(html.contains("<li>b\n<ul>\n<li>b1</li>\n</ul>\n</li>"), html);
	}

	@Test
	public void inlineTextIsHtmlEscaped() {
		assertEquals("<p>a &lt; b &amp; c &gt; d</p>\n", render("a < b & c > d"));
	}

	@Test
	public void rawHtmlIsPassedThrough() {
		assertEquals("<div class=\"x\">raw</div>\n", render("<div class=\"x\">raw</div>"));
	}

	@Test
	public void gfmTableExtensionIsActive() {
		String html = render("| A | B |\n| --- | ---: |\n| 1 | 2 |");
		assertTrue(html.startsWith("<table>"), html);
		assertTrue(html.contains("<thead>"), html);
		assertTrue(html.contains("<th>A</th>"), html);
		// Right alignment from the `---:` delimiter row.
		assertTrue(html.contains("align=\"right\""), html);
		assertTrue(html.contains("<td>1</td>"), html);
	}

	@Test
	public void gfmStrikethroughExtensionIsActive() {
		assertEquals("<p>keep <del>drop</del></p>\n", render("keep ~~drop~~"));
	}

	@Test
	public void emptyAndBlankInput() {
		assertEquals("", render(""));
		assertEquals("", render("   \n\n  "));
	}

	@Test
	public void nonAsciiAndEmojiSurvive() {
		assertEquals("<p>Grüße 🚀</p>\n", render("Grüße 🚀"));
	}
}
