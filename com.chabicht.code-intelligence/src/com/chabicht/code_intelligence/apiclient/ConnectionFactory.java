package com.chabicht.code_intelligence.apiclient;

import java.util.List;
import java.util.Optional;

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
		int firstSlashIndex = completionModelName.indexOf('/');
		String completionConnectionName = completionModelName.substring(0, firstSlashIndex);
		String modelName = completionModelName.substring(firstSlashIndex + 1);

		for (AiApiConnection conn : getApis()) {
			if (conn.getName().equals(completionConnectionName)) {
				return new AiModelConnection(conn, modelName);
			}
		}

		throw new IllegalStateException("No connection found for completion model. Check your preferences.");
	}

	public static AiModelConnection forChat(String chatModelId) {
		if (StringUtils.isBlank(chatModelId)) {
			chatModelId = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.CHAT_MODEL_NAME);
		}

		Optional<Tuple<String, String>> tOpt = ModelUtil.getProviderModelTuple(chatModelId);
		if (tOpt.isPresent()) {
			Tuple<String, String> t = tOpt.get();
			String completionConnectionName = t.getFirst();
			String modelName = t.getSecond();

			for (AiApiConnection conn : getApis()) {
				if (conn.getName().equals(completionConnectionName)) {
					return new AiModelConnection(conn, modelName);
				}
			}
		}

		throw new IllegalStateException("No connection found for chat model. Check your preferences.");
	}
}
