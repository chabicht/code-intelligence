package com.chabicht.code_intelligence.chat;

import static com.chabicht.code_intelligence.util.ThemeUtil.getTextBackgroundColor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.util.ThemeUtil;

public class ChatComponent extends Composite {
	private static final RGB BLACK = new RGB(0, 0, 0);
	private static final RGB WHITE = new RGB(255, 255, 255);
	
	private Browser bChat;
	private final Set<UUID> knownMessages = new HashSet<>();
	private boolean ready = false;
	private LocalResourceManager rm;

	private boolean useJetty = true;
	private File htmlFile;

	public ChatComponent(Composite parent, int style) {
		super(parent, style);

		rm = new LocalResourceManager(JFaceResources.getResources());

		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		setLayout(gridLayout);

		bChat = new Browser(this, SWT.WEBKIT | SWT.EDGE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		setHtml(getHtml());
		bChat.addProgressListener(ProgressListener.completedAdapter(e -> {
			ready = true;
		}));
	}

	private void setHtml(String html) {
		bChat.setText(html);
	}

	private String getHtml() {
		boolean isDark = ThemeUtil.isDarkTheme();
		Color bgColor = getTextBackgroundColor();
		String bgColorString = toCss(bgColor);
		Color fgColor = rm.create(ColorDescriptor
				.createFrom(interpolate(isDark ? WHITE : BLACK, new RGB(255 - bgColor.getRed(), 255 - bgColor.getGreen(), 255 - bgColor.getBlue()), 90)));
		String fgColorString = toCss(fgColor);

		Color bubbleColor = rm.create(interpolate(bgColor, fgColor, 95));
		String bubbleColorString = toCss(bubbleColor);
		Color bubbleBorderColor = rm.create(interpolate(bgColor, fgColor, 75));
		String bubbleBorderColorString = toCss(bubbleBorderColor);

		Color messageBorderColor = rm.create(interpolate(bgColor, fgColor, 88));
		String messageBorderColorString = toCss(messageBorderColor);

		Color tableRowEvenColor = rm.create(interpolate(bgColor, fgColor, 98));
		String tableRowEvenColorString = toCss(tableRowEvenColor);
		Color tableRowHoverColor = rm.create(interpolate(bgColor, fgColor, 94));
		String tableRowHoverColorString = toCss(tableRowHoverColor);

		String template = CHAT_TEMPLATE;
		template = replaceColor(template, "#FFFFFF", bgColorString);
		template = replaceColor(template, "#333333", fgColorString);
		template = replaceColor(template, "#F5F5F5", bubbleColorString);
		template = replaceColor(template, "#BFBFBF", bubbleBorderColorString);
		template = replaceColor(template, "#E0E0E0", messageBorderColorString);
		template = replaceColor(template, "#F9F9F9", tableRowEvenColorString);
		template = replaceColor(template, "#F0F0F0", tableRowHoverColorString);

		String theme = isDark ? "dark" : "light";
		String cssFileName = "prism-" + theme + ".css";
		String jsFileName = "prism-" + theme + ".js";
		template = replacePlaceholder(template, "<!-- ADD PRISM CSS HERE -->", cssFileName, "style");
		template = replacePlaceholder(template, "<!-- ADD PRISM JS HERE -->", jsFileName, "script");

		return template;
	}

	private String replacePlaceholder(String template, String placeholder, String resourceFileName,
			String tagName) {
		try {
			String resourceContent = loadResource(resourceFileName);
			return template.replaceAll(placeholder,
					Matcher.quoteReplacement("<" + tagName + ">\n" + resourceContent + "\n</" + tagName + ">"));
		} catch (IOException e) {
			Activator.logError("Error loading PrismJS " + tagName + ": " + e, e);
			return template;
		}
	}

	private String loadResource(String resourceFileName) throws IOException {
		try (InputStream is = this.getClass().getResourceAsStream(resourceFileName)) {
			if (is == null) {
				throw new IOException("Resource not found: " + resourceFileName);
			}
			return IOUtils.toString(is, StandardCharsets.UTF_8);
		}
	}

	private String replaceColor(String template, String color, String replacementColor) {
		return Pattern.compile(color, Pattern.CASE_INSENSITIVE).matcher(template).replaceAll(replacementColor);
	}

	private ColorDescriptor interpolate(Color bgColor, Color fgColor, int weight) {
		return ColorDescriptor
				.createFrom(new RGB(bgColor.getRed() * weight / 100 + fgColor.getRed() * (100 - weight) / 100,
						bgColor.getGreen() * weight / 100 + fgColor.getGreen() * (100 - weight) / 100,
						bgColor.getBlue() * weight / 100 + fgColor.getBlue() * (100 - weight) / 100));
	}

	private RGB interpolate(RGB bgColor, RGB fgColor, int weight) {
		return new RGB(bgColor.red * weight / 100 + fgColor.red * (100 - weight) / 100,
				bgColor.green * weight / 100 + fgColor.green * (100 - weight) / 100,
				bgColor.blue * weight / 100 + fgColor.blue * (100 - weight) / 100);
	}

	private String toCss(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	public boolean isReady() {
		return ready;
	}

	public void addLocationListener(LocationListener listener) {
		bChat.addLocationListener(listener);
	}

	public void addProgressListener(ProgressListener listener) {
		bChat.addProgressListener(listener);
	}

	public void removeLocationListener(LocationListener listener) {
		bChat.removeLocationListener(listener);
	}

	public void removeProgressListener(ProgressListener listener) {
		bChat.removeProgressListener(listener);
	}

	public void addMessage(UUID messageId, String role, String html, boolean updating) {
		knownMessages.add(messageId);
		bChat.execute(String.format("addMessage('%s', '%s', '%s', %s);", messageId, role, escapeForJavaScript(html),
				updating ? "true" : "false"));
	}

	public void updateMessage(UUID messageId, String html) {
		bChat.execute(String.format("updateMessage('%s', '%s');", messageId, escapeForJavaScript(html)));
	}

	public void markMessageFinished(UUID messageId) {
		bChat.execute(String.format("markMessageFinished('%s');", messageId));
	}

	public void markAllMessagesFinished() {
		bChat.execute("markAllMessagesFinished();");
	}

	public boolean isMessageKnown(UUID messageId) {
		return knownMessages.contains(messageId);
	}

	public void reset() {
		setHtml(getHtml());
		knownMessages.clear();
	}

	public boolean setFocus() {
		return bChat.setFocus();
	}

	public Browser getBrowser() {
		return bChat;
	}

	/**
	 * Escapes a string for safe use in a JavaScript literal.
	 *
	 * @param input the original string to be escaped
	 * @return a string where characters that might break a JS literal are escaped
	 */
	private static String escapeForJavaScript(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			switch (c) {
			case '\\':
				escaped.append("\\\\");
				break;
			case '"':
				escaped.append("\\\"");
				break;
			case '\'':
				escaped.append("\\'");
				break;
			case '`':
				escaped.append("\\`");
				break;
			case '\n':
				escaped.append("\\n");
				break;
			case '\r':
				escaped.append("\\r");
				break;
			case '\t':
				escaped.append("\\t");
				break;
			default:
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private String CHAT_TEMPLATE = readTemplateFile();

	private String readTemplateFile() {
		try {
			try (InputStream stream = this.getClass().getClassLoader()
					.getResourceAsStream("com/chabicht/code_intelligence/chat/chat-template.html")) {
				return IOUtils.toString(stream, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
