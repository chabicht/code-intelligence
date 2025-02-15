package com.chabicht.code_intelligence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.chabicht.code_intelligence.model.PromptTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

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

	public List<PromptTemplate> loadPromptTemplates() {
		try {
			Location configLocation = getConfigLocation();
			File parentDirectory = new File(new File(configLocation.getURL().toURI()), getBundle().getSymbolicName());
			try {
				if (!parentDirectory.exists()) {
					parentDirectory.mkdirs();
				}
				File promptTemplatesFile1 = new File(parentDirectory, "prompt-templates.json");
				File promptTemplatesFile = promptTemplatesFile1;

				if (promptTemplatesFile.canRead()) {
					TypeToken<List<PromptTemplate>> typeListPromptTemplate = new TypeToken<List<PromptTemplate>>() {
					};
					try (BufferedReader reader = IOUtils.buffer(new FileReader(promptTemplatesFile))) {
						return new Gson().fromJson(reader, typeListPromptTemplate);
					}
				}
			} finally {
				configLocation.release();
			}
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return new ArrayList<>();
	}

	public void savePromptTemplates(List<PromptTemplate> promptTemplates) {
		try {
			Location configLocation = getConfigLocation();
			File parentDirectory = new File(new File(configLocation.getURL().toURI()), getBundle().getSymbolicName());
			try {
				if (!parentDirectory.exists()) {
					parentDirectory.mkdirs();
				}
				File promptTemplatesFile1 = new File(parentDirectory, "prompt-templates.json");
				File promptTemplatesFile = promptTemplatesFile1;

				try (FileWriter writer = new FileWriter(promptTemplatesFile)) {
					new Gson().toJson(promptTemplates, writer);
				}
			} finally {
				configLocation.release();
			}
		} catch (IOException | JsonIOException | URISyntaxException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Location getConfigLocation() throws InterruptedException, IOException {
		Location configLocation = Platform.getConfigurationLocation();
		if (configLocation == null) {
			configLocation = Platform.getUserLocation();
			while (!configLocation.lock()) {
				Thread.sleep(1000);
			}
		}
		return configLocation;
	}
}
