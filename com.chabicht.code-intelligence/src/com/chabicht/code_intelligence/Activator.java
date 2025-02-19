package com.chabicht.code_intelligence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	private static final String PROMPT_TEMPLATES_FILE = "prompt-templates.json";

	private static final String API_CONNECTIONS_FILE = "api-connections.json";

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

	public List<AiApiConnection> loadConnections() {
		String json = getPreferenceStore().getString(PreferenceConstants.API_CONNECTION_DATA);
		if (StringUtils.isEmpty(json)) {
			return new ArrayList<>();
		} else {
			Type listType = new TypeToken<List<AiApiConnection>>() {
			}.getType();
			return new Gson().fromJson(json, listType);
		}
	}

	public List<AiApiConnection> loadApiConnections() {
		TypeToken typeToken = new TypeToken<List<AiApiConnection>>() {
		};
		return readFile(API_CONNECTIONS_FILE, typeToken);
	}

	public void saveApiConnections(List<AiApiConnection> apiConnections) {
		writeFile(API_CONNECTIONS_FILE, apiConnections);
	}

	public List<PromptTemplate> loadPromptTemplates() {
		TypeToken typeToken = new TypeToken<List<PromptTemplate>>() {
		};
		return readFile(PROMPT_TEMPLATES_FILE, typeToken);
	}

	public void savePromptTemplates(List<PromptTemplate> promptTemplates) {
		writeFile(PROMPT_TEMPLATES_FILE, promptTemplates);
	}

	private <T> List<T> readFile(String filename, TypeToken<List<T>> token) {
		try {
			File parentDirectory = getConfigLocationAsFile();
			File file = new File(parentDirectory, filename);

			if (!file.exists()) {
				return Collections.emptyList();
			} else {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					Type listType = token.getType();
					List<T> res = new Gson().fromJson(reader, listType);

					return res;
				}
			}
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
	}

	private <T> void writeFile(String filename, List<T> items) {
		try {
			File parentDirectory = getConfigLocationAsFile();
			File file = new File(parentDirectory, filename);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				new Gson().toJson(items, writer);
			}
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
	}

	private File getConfigLocationAsFile() throws IOException {
		Location configLocation = getConfigLocation();
		File parentDirectory = new File(new File(configLocation.getURL().getFile()), getBundle().getSymbolicName());
		if (!parentDirectory.exists()) {
			parentDirectory.mkdirs();
		}
		return parentDirectory;
	}

	private Location getConfigLocation() throws IOException {
		Location configLocation = Platform.getConfigurationLocation();
		if (configLocation == null || configLocation.isReadOnly()) {
			configLocation = Platform.getUserLocation();
		}
		return configLocation;
	}
}
