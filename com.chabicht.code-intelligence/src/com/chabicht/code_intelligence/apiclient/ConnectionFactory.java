package com.chabicht.code_intelligence.apiclient;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

	public static AiModelConnection forChat(String chatModelName) {
		if (StringUtils.isBlank(chatModelName)) {
			chatModelName = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.CHAT_MODEL_NAME);
		}

		int firstSlashIndex = chatModelName.indexOf('/');
		String completionConnectionName = chatModelName.substring(0, firstSlashIndex);
		String modelName = chatModelName.substring(firstSlashIndex + 1);

		for (AiApiConnection conn : getApis()) {
			if (conn.getName().equals(completionConnectionName)) {
				return new AiModelConnection(conn, modelName);
			}
		}

		throw new IllegalStateException("No connection found for completion model. Check your preferences.");
	}
}
