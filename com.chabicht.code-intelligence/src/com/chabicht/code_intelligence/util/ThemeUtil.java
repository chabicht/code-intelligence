package com.chabicht.code_intelligence.util;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
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
		return textBackgroundColor != null && isDark(textBackgroundColor);
	}

	public static boolean isLightTheme() {
		return !isDarkTheme();
	}

	/**
	 * Calculates the perceived brightness of an SWT Color object.
	 * <p>
	 * <strong>Note:</strong> The caller is responsible for managing the lifecycle
	 * (creating and disposing) of the Color object. It is often safer to work
	 * with RGB objects.
	 *
	 * @param color The SWT color to analyze.
	 * @return A double value representing the brightness, from 0.0 (black) to 255.0 (white).
	 */
	public static double getBrightness(Color color) {
		// The alpha component is ignored.
		return getBrightness(color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Calculates the perceived brightness of an SWT RGB object.
	 * This is the recommended method for SWT as RGB objects are simple data
	 * containers and do not need to be disposed.
	 *
	 * @param rgb The RGB object to analyze.
	 * @return A double value representing the brightness, from 0.0 (black) to 255.0 (white).
	 */
	public static double getBrightness(RGB rgb) {
		return getBrightness(rgb.red, rgb.green, rgb.blue);
	}

	/**
	 * Calculates the perceived brightness of a color defined by its R, G, B components.
	 *
	 * @param r The red component (0-255).
	 * @param g The green component (0-255).
	 * @param b The blue component (0-255).
	 * @return A double value representing the brightness, from 0.0 (black) to 255.0 (white).
	 */
	public static double getBrightness(int r, int g, int b) {
		// Formula for perceived brightness (Luminance)
		// See https://www.w3.org/TR/AERT/#color-contrast
		return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
	}

	/**
	 * A helper method to determine if a color is "dark" or "light".
	 * This is useful for deciding whether to place light or dark text on a colored background.
	 *
	 * @param rgb The RGB object to check.
	 * @param threshold The brightness threshold (a common value is 128).
	 * @return true if the color's brightness is below the threshold, false otherwise.
	 */
	public static boolean isDark(RGB rgb, double threshold) {
		return getBrightness(rgb) < threshold;
	}

	/**
	 * A helper method to determine if a color is "dark" or "light" using a default threshold.
	 * @see #isDark(RGB, double)
	 */
	public static boolean isDark(RGB rgb) {
		return isDark(rgb, 128.0);
	}

	/**
	 * A helper method to determine if a color is "dark" or "light".
	 * @see #isDark(RGB, double)
	 */
	public static boolean isDark(Color color, double threshold) {
		return getBrightness(color) < threshold;
	}

	/**
	 * A helper method to determine if a color is "dark" or "light" using a default threshold.
	 * @see #isDark(Color, double)
	 */
	public static boolean isDark(Color color) {
		return isDark(color, 128.0);
	}

	public static Font getTextEditorFont() {
		return JFaceResources.getTextFont();
	}
}
