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
				    "temperature": 0.8,
				    "top_k": 20,
				    "top_p": 0.9,
				    "min_p": 0.0,
				    "typical_p": 0.7,
				    "repeat_last_n": 33,
				    "repeat_penalty": 1.2,
				    "presence_penalty": 1.5,
				    "frequency_penalty": 1.0,
				    "mirostat": 1,
				    "mirostat_tau": 0.8,
				    "mirostat_eta": 0.6,
				    "stop": ["\\n", "user:"],
				    "num_keep": 5,
				    "num_predict": 100,
				    "num_ctx": 1024,
				    "num_batch": 2,
				    "num_gpu": 1,
				    "main_gpu": 0,
				    "low_vram": false,
				    "vocab_only": false,
				    "use_mmap": true,
				    "use_mlock": false,
				    "num_thread": 8
				  },
				  "keep_alive": "5m"
				}
				""");
		res.put(tCompletion, res.get(tChat));

		tChat = Tuple.of(ApiType.OPENAI, PromptType.CHAT);
		tCompletion = Tuple.of(ApiType.OPENAI, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "store": false,
				  "reasoning_effort": null,
				  "metadata": null,
				  "frequency_penalty": 0,
				  "logit_bias": null,
				  "logprobs": false,
				  "top_logprobs": null,
				  "max_tokens": null,
				  "max_completion_tokens": null,
				  "n": 1,
				  "modalities": null,
				  "prediction": null,
				  "audio": null,
				  "presence_penalty": 0,
				  "response_format": null,
				  "seed": null,
				  "service_tier": "auto",
				  "stop": null,
				  "stream": false,
				  "stream_options": null,
				  "temperature": 1,
				  "top_p": 1,
				  "tools": null,
				  "tool_choice": "none",
				  "parallel_tool_calls": true,
				  "user": null,
				  "function_call": "none",
				  "functions": null
				}
				""");
		res.put(tCompletion, res.get(tChat));

		tChat = Tuple.of(ApiType.OPENAI_RESPONSES, PromptType.CHAT);
		tCompletion = Tuple.of(ApiType.OPENAI_RESPONSES, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "store": false,
				  "temperature": 1,
				  "top_p": 1,
				  "max_output_tokens": null,
				  "reasoning": null,
				  "metadata": null,
				  "tools": null,
				  "tool_choice": "auto",
				  "parallel_tool_calls": false
				}
				""");
		res.put(tCompletion, res.get(tChat));

		tChat = Tuple.of(ApiType.ANTHROPIC, PromptType.CHAT);
		tCompletion = Tuple.of(ApiType.ANTHROPIC, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "max_tokens": 8192,
				  "temperature": 1.0,
				  "top_p": 1.0,
				  "top_k": 50,
				  "stop_sequences": [],
				  "metadata": {},
				  "system": "",
				  "tool_choice": null,
				  "tools": []
				}
				""");
		res.put(tCompletion, res.get(tChat));

		tChat = Tuple.of(ApiType.GEMINI, PromptType.CHAT);
		tCompletion = Tuple.of(ApiType.GEMINI, PromptType.INSTRUCT);
		res.put(tChat, """
				{
				  "safetySettings": [
				    {
				      "category": "HARM_CATEGORY_HATE_SPEECH",
				      "threshold": "BLOCK_MEDIUM_AND_ABOVE"
				    },
				    {
				      "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT",
				      "threshold": "BLOCK_MEDIUM_AND_ABOVE"
				    },
				    {
				      "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
				      "threshold": "BLOCK_MEDIUM_AND_ABOVE"
				    },
				    {
				      "category": "HARM_CATEGORY_HARASSMENT",
				      "threshold": "BLOCK_MEDIUM_AND_ABOVE"
				    },
				    {
				      "category": "HARM_CATEGORY_CIVIC_INTEGRITY",
				      "threshold": "BLOCK_MEDIUM_AND_ABOVE"
				    }
				  ],
				  "generationConfig": {
				    "candidateCount": 1,
				    "temperature": 0.7,
				    "topP": 0.9,
				    "topK": 40,
				    "seed": 1234,
				    "presencePenalty": 0.0,
				    "frequencyPenalty": 0.0,
				    "stopSequences": [],
				    "responseMimeType": "text/plain"
				  }
				}
				""");
		res.put(tCompletion, res.get(tChat));

		return res;
	}
}
