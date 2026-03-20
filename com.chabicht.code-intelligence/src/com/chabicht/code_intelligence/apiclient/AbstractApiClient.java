package com.chabicht.code_intelligence.apiclient;

import java.util.Map;
import java.util.Map.Entry;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.CustomConfigurationParameters;
import com.chabicht.code_intelligence.model.PromptType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class AbstractApiClient {

	protected final AiApiConnection apiConnection;
	protected final transient Gson gson = initGson();

	private static Gson initGson() {
		Activator activator = Activator.getDefault();
		return activator != null ? activator.createGson() : new Gson();
	}

	public AbstractApiClient(AiApiConnection apiConnection) {
		super();
		this.apiConnection = apiConnection;
	}

	protected JsonObject createFromPresets(PromptType type) {
		Map<String, String> customConfig = CustomConfigurationParameters.getInstance().get(apiConnection.getName());
		if (customConfig.containsKey(type.name())) {
			return gson.fromJson(customConfig.get(type.name()), JsonObject.class);
		} else {
			return new JsonObject();
		}
	}

	protected JsonObject getOrAddJsonObject(JsonObject req, String propertyName) {
		if (req == null || propertyName == null) {
			return new JsonObject();
		}

		if (hasNonNullProperty(req, propertyName) && req.get(propertyName).isJsonObject()) {
			return req.getAsJsonObject(propertyName);
		}

		JsonObject res = new JsonObject();
		req.add(propertyName, res);
		return res;
	}

	protected void setPropertyIfNotPresent(JsonObject object, String propertyName, Number number) {
		if (!hasNonNullProperty(object, propertyName)) {
			object.addProperty(propertyName, number);
		}
	}

	protected boolean hasNonNullProperty(JsonObject object, String propertyName) {
		return object != null && propertyName != null && object.has(propertyName)
				&& !object.get(propertyName).isJsonNull();
	}

	protected void patchMissingProperties(JsonObject target, JsonObject patch) {
		for (Entry<String, JsonElement> e : patch.entrySet()) {
			if (!target.has(e.getKey())) {
				target.add(e.getKey(), e.getValue());
			} else if (target.get(e.getKey()).isJsonObject() && e.getValue().isJsonObject()) {
				patchMissingProperties(target.get(e.getKey()).getAsJsonObject(), e.getValue().getAsJsonObject());
			}
		}
	}

}
