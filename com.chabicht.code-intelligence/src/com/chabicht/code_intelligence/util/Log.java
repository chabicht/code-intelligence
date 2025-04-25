package com.chabicht.code_intelligence.util;

import java.util.logging.Level;
import java.util.logging.Logger;


public class Log {
	private static final Logger LOG = Logger.getLogger("CODE-INTELLIGENCE");

	/**
	 * Log an error.
	 */
	public static void logError(String message, Throwable exception) {
		if (com.chabicht.code_intelligence.Activator.getDefault() != null) {
			com.chabicht.code_intelligence.Activator.logError(message, exception);
		} else {
			LOG.log(Level.SEVERE, message, exception);
		}
	}

	/**
	 * Log an error.
	 */
	public static void logError(String message) {
		if (com.chabicht.code_intelligence.Activator.getDefault() != null) {
			com.chabicht.code_intelligence.Activator.logError(message);
		} else {
			LOG.log(Level.SEVERE, message);
		}
	}

	/**
	 * Log an info message.
	 */
	public static void logInfo(String message) {
		if (com.chabicht.code_intelligence.Activator.getDefault() != null) {
			com.chabicht.code_intelligence.Activator.logInfo(message);
		} else {
			LOG.log(Level.INFO, message);
		}
	}

	/**
	 * Log a warning message.
	 */	public static void logWarn(String message) {
		if (com.chabicht.code_intelligence.Activator.getDefault() != null) {
			com.chabicht.code_intelligence.Activator.logWarn(message);
		} else {
			LOG.log(Level.WARNING, message);
		}
	}
}
