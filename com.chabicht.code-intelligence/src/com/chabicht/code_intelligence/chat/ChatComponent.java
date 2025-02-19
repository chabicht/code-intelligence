package com.chabicht.code_intelligence.chat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.ColorRegistry;
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
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;

public class ChatComponent extends Composite {
	private Browser bChat;
	private final Set<UUID> knownMessages = new HashSet<>();
	private boolean ready = false;
	private LocalResourceManager rm;

	public ChatComponent(Composite parent, int style) {
		super(parent, style);

		rm = new LocalResourceManager(JFaceResources.getResources());

		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		setLayout(gridLayout);

		bChat = new Browser(this, SWT.WEBKIT | SWT.EDGE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bChat.setText(getHtml());
		bChat.addProgressListener(ProgressListener.completedAdapter(e -> {
			ready = true;
		}));
	}

	private String getHtml() {
		Color bgColor = getTextBackgroundColor();
		String bgColorString = toCss(bgColor);
		Color fgColor = rm.create(ColorDescriptor
				.createFrom(new RGB(255 - bgColor.getRed(), 255 - bgColor.getGreen(), 255 - bgColor.getBlue())));
		String fgColorString = toCss(fgColor);
		Color bubbleColor = rm.create(interpolate(bgColor, fgColor, 95));
		String bubbleColorString = toCss(bubbleColor);
		Color bubbleBorderColor = rm.create(interpolate(bgColor, fgColor, 75));
		String bubbleBorderColorString = toCss(bubbleBorderColor);
		return CHAT_TEMPLATE.replaceAll("\\{\\{\\{background_color\\}\\}\\}", bgColorString)
				.replaceAll("\\{\\{\\{foreground_color\\}\\}\\}", fgColorString)
				.replaceAll("\\{\\{\\{bubble_color\\}\\}\\}", bubbleColorString)
				.replaceAll("\\{\\{\\{bubble_border_color\\}\\}\\}", bubbleBorderColorString);
	}

	private ColorDescriptor interpolate(Color bgColor, Color fgColor, int weight) {
		return ColorDescriptor
				.createFrom(new RGB(bgColor.getRed() * weight / 100 + fgColor.getRed() * (100 - weight) / 100,
						bgColor.getGreen() * weight / 100 + fgColor.getGreen() * (100 - weight) / 100,
						bgColor.getBlue() * weight / 100 + fgColor.getBlue() * (100 - weight) / 100));
	}

	private String toCss(Color color) {
		return String.format("#%x%x%x", color.getRed(), color.getGreen(), color.getBlue());
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

	public void addMessage(UUID messageId, String role, String html) {
		knownMessages.add(messageId);
		bChat.execute(String.format("addMessage('%s', '%s', '%s');", messageId, role, escapeForJavaScript(html)));
	}

	public void updateMessage(UUID messageId, String html) {
		bChat.execute(String.format("updateMessage('%s', '%s');", messageId, escapeForJavaScript(html)));
	}

	public void updateMessage(UUID messageId, String role, String html) {
		bChat.execute(String.format("updateMessage('%s', '%s', '%s');", messageId, role, escapeForJavaScript(html)));
	}

	public boolean isMessageKnown(UUID messageId) {
		return knownMessages.contains(messageId);
	}

	public void reset() {
		bChat.setText(getHtml());
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

	private Color getTextBackgroundColor() {
		IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
		ITheme currentTheme = themeManager.getCurrentTheme();
		ColorRegistry colorRegistry = currentTheme.getColorRegistry();
		String backgroundColorKey = "org.eclipse.ui.workbench.ACTIVE_TAB_BG_END";
		return colorRegistry.get(backgroundColorKey);
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
