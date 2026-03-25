package com.chabicht.code_intelligence.util;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Tuple;

public class ModelUtil {
	private ModelUtil() {
		// No instances.
	}

	public static String normalizeConfiguredModel(String modelId) {
		modelId = StringUtils.stripToEmpty(modelId);
		int firstSlashIndex = modelId.indexOf('/');
		if (firstSlashIndex < 0) {
			return modelId;
		}

		String connectionName = StringUtils.trim(modelId.substring(0, firstSlashIndex));
		String normalizedModelId = StringUtils.trim(modelId.substring(firstSlashIndex + 1));
		return connectionName + "/" + normalizedModelId;
	}

	public static Optional<Tuple<String, String>> getProviderModelTuple(String modelId) {
		Optional<Tuple<String, String>> res = Optional.empty();

		modelId = normalizeConfiguredModel(modelId);
		int firstSlashIndex = modelId.indexOf('/');
		if (firstSlashIndex > 0 && firstSlashIndex < modelId.length() - 1) {
			String connectionName = StringUtils.trim(modelId.substring(0, firstSlashIndex));
			String normalizedModelId = StringUtils.trim(modelId.substring(firstSlashIndex + 1));
			if (StringUtils.isNoneBlank(connectionName, normalizedModelId)) {
				res = Optional.of(new Tuple<>(connectionName, normalizedModelId));
			}
		}

		return res;
	}
}
