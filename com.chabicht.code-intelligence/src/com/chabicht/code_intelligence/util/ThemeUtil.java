package com.chabicht.code_intelligence.util;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;

/**
 * Utility functions related to UI themes.
 */
public class ThemeUtil {
	private ThemeUtil() {
		// No instances
	}

	public static Color getTextBackgroundColor() {
		IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
		ITheme currentTheme = themeManager.getCurrentTheme();
		ColorRegistry colorRegistry = currentTheme.getColorRegistry();
		String backgroundColorKey = "org.eclipse.ui.workbench.ACTIVE_TAB_BG_END";
		return colorRegistry.get(backgroundColorKey);
	}

	public static boolean isDarkTheme() {
		Color textBackgroundColor = getTextBackgroundColor();
		return textBackgroundColor != null && isDarkColor(textBackgroundColor);
	}

	public static boolean isLightTheme() {
		return !isDarkTheme();
	}

	private static boolean isDarkColor(Color color) {
		// Calculate perceived brightness (YIQ formula)
		double yiq = ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) / 1000;
		// Consider it a dark color if the brightness is less than 128 (midpoint)
		return yiq < 128;
	}

	public static Font getTextEditorFont() {
		return JFaceResources.getTextFont();
	}
}
