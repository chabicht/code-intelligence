package com.chabicht.code_intelligence;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatHistoryEntry;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.ProviderDefaults;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	private static final String PROMPT_TEMPLATES_FILE = "prompt-templates.json";

	private static final String API_CONNECTIONS_FILE = "api-connections.json";

	public static final String CHAT_HISTORY_FILE = "chat-history.json";

	public static final String CUSTOM_CONFIGURATION_PARAMETERS_FILE = "custom-config.json";

	// The plug-in ID
	public static final String PLUGIN_ID = "com.chabicht.code-intelligence"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final Map<String, ProviderDefaults> SUPPORTED_PROVIDERS = consSupportedProviders();

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
	public static void logError(String message) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message));
	}

	/**
	 * Log a message to the Eclipse Error Log.
	 */
	public static void logInfo(String message) {
		getDefault().getLog().log(new Status(IStatus.INFO, PLUGIN_ID, message));
	}

	/**
	 * Log a warning to the Eclipse Error Log.
	 */
	public static void logWarn(String message) {
		getDefault().getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, message));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<AiApiConnection> loadApiConnections() {
		TypeToken typeToken = new TypeToken<List<AiApiConnection>>() {
		};
		return readFile(API_CONNECTIONS_FILE, typeToken);
	}

	public void saveApiConnections(List<AiApiConnection> apiConnections) {
		writeFile(API_CONNECTIONS_FILE, apiConnections);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<PromptTemplate> loadPromptTemplates() {
		TypeToken typeToken = new TypeToken<List<PromptTemplate>>() {
		};
		return readFile(PROMPT_TEMPLATES_FILE, typeToken);
	}

	public void savePromptTemplates(List<PromptTemplate> promptTemplates) {
		writeFile(PROMPT_TEMPLATES_FILE, promptTemplates);
	}

	public List<ChatHistoryEntry> loadChatHistory() {
		TypeToken<List<ChatHistoryEntry>> typeToken = new TypeToken<List<ChatHistoryEntry>>() {
		};
		List<ChatHistoryEntry> history = readFile(CHAT_HISTORY_FILE, typeToken);
		return history != null ? history : new ArrayList<>();
	}

	public void saveChatHistory(List<ChatHistoryEntry> history) {
		// Limit the size of the history if preference is set.
		int limit = getPreferenceStore().getInt(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT);
		if (limit > 0 && history.size() > limit) {
			history = history.subList(0, limit);
		}
		writeFile(CHAT_HISTORY_FILE, history);
	}

	public void addOrUpdateChatHistory(ChatConversation conversation) {
		if (conversation == null) {
			return;
		}

		List<ChatHistoryEntry> history = new ArrayList<>(loadChatHistory());
		boolean updated = false;

		// Check if the conversation has a conversation ID
		UUID conversationId = conversation.getConversationId();
		if (conversationId == null) {
			// Assign a new ID if it doesn't have one
			conversationId = UUID.randomUUID();
			conversation.setConversationId(conversationId);
		}

		// Check if conversation is already in history
		for (ChatHistoryEntry entry : history) {
			if (entry.getConversation() != null && conversationId.equals(entry.getConversation().getConversationId())) {
				// Update the existing entry
				entry.updateFromConversation(conversation);
				updated = true;
				break;
			}
		}

		// If not found, add as new entry
		if (!updated) {
			ChatHistoryEntry newEntry = new ChatHistoryEntry(conversation);
			history.add(0, newEntry); // Add at beginning of list
		}

		saveChatHistory(history);
	}

	public Map<String, Map<String, String>> loadCustomConfigurationParameters() {
		TypeToken<Map<String, Map<String, String>>> typeToken = new TypeToken<Map<String, Map<String, String>>>() {
		};
		try {
			File parentDirectory = getConfigLocationAsFile();
			File file = new File(parentDirectory, CUSTOM_CONFIGURATION_PARAMETERS_FILE);

			if (!file.exists()) {
				return Collections.emptyMap();
			} else {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					Type mapType = typeToken.getType();
					return createGson().fromJson(reader, mapType);
				}
			}
		} catch (JsonSyntaxException e) {
			return Collections.emptyMap();
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
	}

	public void saveCustomConfigurationParameters(Map<String, Map<String, String>> parameters) {
		try {
			File parentDirectory = getConfigLocationAsFile();
			File file = new File(parentDirectory, CUSTOM_CONFIGURATION_PARAMETERS_FILE);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				createGson().toJson(parameters, writer);
			}
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
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
					List<T> res = createGson().fromJson(reader, listType);

					return res;
				}
			}
		} catch (JsonSyntaxException e) {
			return Collections.emptyList();
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
	}

	private <T> void writeFile(String filename, List<T> items) {
		try {
			File parentDirectory = getConfigLocationAsFile();
			File file = new File(parentDirectory, filename);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				createGson().toJson(items, writer);
			}
		} catch (IOException | JsonIOException e) {
			throw new RuntimeException(e);
		}
	}

	public Gson createGson() {
		return GsonUtil.createGson();
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

	public void triggerConfigChangeNotification() {
		pcs.firePropertyChange("configuration", null, "");
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(propertyName, listener);
	}

	public int getMaxCompletionTokens() {
		int res = getPreferenceStore().getInt(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS);
		if (res <= 0) {
			res = 1024;
		}
		return res;
	}

	public int getMaxChatTokens() {
		int res = getPreferenceStore().getInt(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS);
		if (res <= 0) {
			res = 8192;
		}
		return res;
	}

	/**
	 * Creates a mapping of all supported AI providers (not just the ones whose
	 * <i>protocols</i> are supported).
	 */
	private HashMap<String, ProviderDefaults> consSupportedProviders() {
		HashMap<String, ProviderDefaults> res = new HashMap<>();

		// OpenAI
		res.put("OpenAI", new ProviderDefaults("OpenAI", ApiType.OPENAI, "https://api.openai.com/v1",
				"https://platform.openai.com/settings/organization/api-keys", "gpt-4o-mini", "o3-mini"));

		// Groq
		res.put("Groq", new ProviderDefaults("Groq", ApiType.OPENAI, "https://api.groq.com/openai/v1",
				"https://console.groq.com/keys", "qwen-2.5-coder-32b", "deepseek-r1-distill-llama-70b"));

		// DeepSeek
		res.put("DeepSeek", new ProviderDefaults("DeepSeek", ApiType.OPENAI, "https://api.deepseek.com",
				"https://platform.deepseek.com/api-keys", "deepseek-chat", "deepseek-reasoner"));

		// Ollama
		res.put("Ollama", new ProviderDefaults("Ollama", ApiType.OLLAMA, "http://localhost:11434", "",
				"qwen2.5-coder:14b", "qwen2.5-coder:14b"));

		// Anthropic
		res.put("Anthropic",
				new ProviderDefaults("Anthropic", ApiType.ANTHROPIC, "https://api.anthropic.com/v1",
						"https://console.anthropic.com/settings/keys", "claude-3-5-haiku-20241022",
						"claude-3-7-sonnet-20250219"));

		// Gemini
		res.put("Gemini",
				new ProviderDefaults("Gemini", ApiType.GEMINI, "https://generativelanguage.googleapis.com/v1beta",
						"https://aistudio.google.com/app/apikey", "models/gemini-2.0-flash-lite",

						"models/gemini-2.0-pro-exp"));

		// X.ai
		res.put("X.ai",
				new ProviderDefaults("X.ai", ApiType.XAI, "https://api.x.ai/v1", "https://console.x.ai/", "grok-2-1212",

						"grok-2-1212"));

		return res;
	}

	// Make this accessible
	private HashMap<String, ProviderDefaults> supportedProviders;

	public HashMap<String, ProviderDefaults> getSupportedProviders() {
		if (supportedProviders == null) {
			supportedProviders = consSupportedProviders();
		}
		return supportedProviders;
	}

}
