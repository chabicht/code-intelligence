package com.chabicht.code_intelligence.apiclient;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.Tuple;
import com.chabicht.code_intelligence.util.ModelUtil;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ConnectionFactory {
	public static List<AiApiConnection> getApis() {
		return Activator.getDefault().loadApiConnections();
	}

	public static AiModelConnection forCompletions() {
		String completionModelName = Activator.getDefault().getPreferenceStore()
				.getString(PreferenceConstants.COMPLETION_MODEL_NAME);
		Tuple<String, String> configuredModel = requireConfiguredModel(completionModelName, "completion");
		String completionConnectionName = configuredModel.getFirst();
		String modelName = configuredModel.getSecond();

		for (AiApiConnection conn : getApis()) {
			if (conn.getName().equals(completionConnectionName)) {
				return new AiModelConnection(conn, modelName);
			}
		}

		throw new IllegalStateException(
				"No connection found for completion model '" + completionModelName + "'. Check your preferences.");
	}

	public static AiModelConnection forChat(String chatModelId) {
		if (StringUtils.isBlank(chatModelId)) {
			chatModelId = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.CHAT_MODEL_NAME);
		}

		Tuple<String, String> configuredModel = requireConfiguredModel(chatModelId, "chat");
		String completionConnectionName = configuredModel.getFirst();
		String modelName = configuredModel.getSecond();

		for (AiApiConnection conn : getApis()) {
			if (conn.getName().equals(completionConnectionName)) {
				return new AiModelConnection(conn, modelName);
			}
		}

		throw new IllegalStateException("No connection found for chat model '" + chatModelId + "'. Check your preferences.");
	}

	private static Tuple<String, String> requireConfiguredModel(String configuredModel, String fieldName) {
		return ModelUtil.getProviderModelTuple(configuredModel)
				.orElseThrow(() -> new IllegalStateException("Invalid " + fieldName + " model setting '"
						+ StringUtils.defaultIfBlank(ModelUtil.normalizeConfiguredModel(configuredModel), "<empty>")
						+ "'. Expected: connectionName/modelId. Check your preferences."));
	}
}
