package com.chabicht.codeintelligence.preferences;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Tuple;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.util.ModelUtil;

final class PreferenceValidationSupport {

	enum Severity {
		NONE,
		WARNING,
		ERROR
	}

	record ValidationResult(Severity severity, String message) {
		static ValidationResult ok() {
			return new ValidationResult(Severity.NONE, null);
		}

		static ValidationResult warning(String message) {
			return new ValidationResult(Severity.WARNING, message);
		}

		static ValidationResult error(String message) {
			return new ValidationResult(Severity.ERROR, message);
		}

		boolean isOk() {
			return severity == Severity.NONE;
		}

		boolean isWarning() {
			return severity == Severity.WARNING;
		}

		boolean isError() {
			return severity == Severity.ERROR;
		}
	}

	private PreferenceValidationSupport() {
		// No instances.
	}

	static String normalizeConfiguredModel(String value) {
		return ModelUtil.normalizeConfiguredModel(value);
	}

	static ValidationResult validateInt(String value, String fieldName) {
		try {
			Integer.parseInt(value);
			return ValidationResult.ok();
		} catch (NumberFormatException e) {
			return ValidationResult.error(fieldName + " must be a valid integer");
		}
	}

	static ValidationResult validateModel(String value, String fieldName, List<AiApiConnection> connections) {
		if (StringUtils.isBlank(value)) {
			return ValidationResult.warning(fieldName + " is not configured");
		}

		Optional<Tuple<String, String>> configuredModel = ModelUtil.getProviderModelTuple(value);
		if (configuredModel.isEmpty()) {
			return ValidationResult.warning("Invalid format for " + fieldName + ". Expected: connectionName/modelId");
		}

		String connectionName = configuredModel.get().getFirst();
		String modelId = configuredModel.get().getSecond();

		AiApiConnection targetConnection = findConnectionByName(connectionName, connections);

		if (targetConnection == null) {
			return ValidationResult.warning("Connection '" + connectionName + "' not found for " + fieldName);
		}

		if (!targetConnection.isEnabled()) {
			return ValidationResult.warning("Connection '" + connectionName + "' is disabled");
		}

		try {
			List<AiModel> models = targetConnection.getApiClient().getModels();
			boolean modelExists = models.stream().anyMatch(model -> StringUtils.equals(model.getId(), modelId));

			if (!modelExists) {
				return ValidationResult.warning(
						"Model '" + modelId + "' not found in connection '" + connectionName + "'");
			}
		} catch (RuntimeException e) {
			String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
			return ValidationResult.warning("Error validating " + fieldName + ": " + message);
		}

		return ValidationResult.ok();
	}

	private static AiApiConnection findConnectionByName(String connectionName, List<AiApiConnection> connections) {
		for (AiApiConnection conn : connections) {
			if (StringUtils.equals(StringUtils.stripToEmpty(conn.getName()),
					StringUtils.stripToEmpty(connectionName))) {
				return conn;
			}
		}
		return null;
	}
}
