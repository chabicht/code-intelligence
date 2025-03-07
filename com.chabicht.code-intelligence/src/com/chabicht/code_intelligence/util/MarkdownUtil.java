package com.chabicht.code_intelligence.util;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownUtil {
	private static final List<Extension> extensions = List.of(TablesExtension.create(),
			StrikethroughExtension.create());

	private MarkdownUtil() {
		// No instances.
	}

	/**
	 * Instantiates a Markdown {@link Parser} with some plugins enabled.
	 */
	public static Parser createParser() {
		return Parser.builder().extensions(extensions).build();
	}

	/**
	 * Instantiates a Markdown {@link Renderer} with some plugins enabled.
	 */
	public static HtmlRenderer createRenderer() {
		return HtmlRenderer.builder().extensions(extensions).build();
	}

}
