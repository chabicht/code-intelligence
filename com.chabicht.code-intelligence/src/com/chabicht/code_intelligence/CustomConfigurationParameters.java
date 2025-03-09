package com.chabicht.code_intelligence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.model.PromptType;

/**
 * Handles and caches custom model parameters that are dynamically added to chat
 * requests.
 */
public class CustomConfigurationParameters {
	private static final Map<Tuple<ApiType, PromptType>, String> connectionParamTemplates = createTemplates();

	private static CustomConfigurationParameters instance = null;
	private Map<String, Map<String, String>> map;

	public static synchronized CustomConfigurationParameters getInstance() {
		if (instance == null) {
			instance = new CustomConfigurationParameters();
		}
		return instance;
	}

	private CustomConfigurationParameters() {
		this.map = Activator.getDefault().loadCustomConfigurationParameters();
	}

	public Map<String, String> get(String connectionName) {
		if (map.containsKey(connectionName)) {
			return map.get(connectionName);
		} else {
			return Map.of();
		}
	}

	public Map<String, Map<String, String>> getMap() {
		return Collections.unmodifiableMap(map);
	}

	public void setMap(Map<String, Map<String, String>> replacement) {
		Activator.getDefault().saveCustomConfigurationParameters(replacement);
		map = replacement;
	}

	public static Map<Tuple<ApiType, PromptType>, String> getConnectionparamtemplates() {
		return connectionParamTemplates;
	}

	private static Map<Tuple<ApiType, PromptType>, String> createTemplates() {
		HashMap<Tuple<ApiType, PromptType>, String> res = new HashMap<>();

		Tuple<ApiType, PromptType> tChat = Tuple.of(ApiType.OLLAMA, PromptType.CHAT);
		Tuple<ApiType, PromptType> tCompletion = Tuple.of(ApiType.OLLAMA, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "options": {
				    "num_ctx": 8192,
				    "num_batch": 2048
				  },
				  "keep_alive": "15m"
				}
				""");
		res.put(tCompletion, res.get(tChat));

		tChat = Tuple.of(ApiType.OPENAI, PromptType.CHAT);
		tCompletion = Tuple.of(ApiType.OPENAI, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "reasoning_effort": "medium",
				  "temperature": 0.3
				}
				""");
		res.put(tCompletion, res.get(tChat));

		return res;
	}
}
