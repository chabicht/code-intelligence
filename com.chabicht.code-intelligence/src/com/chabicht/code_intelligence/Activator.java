package com.chabicht.code_intelligence;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.chabicht.code-intelligence"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Log an error to the Eclipse Error Log.
	 */
	public static void logError(String message, Throwable exception) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, exception));
	}

	/**
	 * Log an error to the Eclipse Error Log.
	 */
	public static void logInfo(String message) {
		getDefault().getLog().log(new Status(IStatus.INFO, PLUGIN_ID, message));
	}
}
