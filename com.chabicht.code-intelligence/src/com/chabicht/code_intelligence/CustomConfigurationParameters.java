package com.chabicht.code_intelligence;

import java.util.Collections;
import java.util.Map;

public class CustomConfigurationParameters {
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
			return Collections.emptyMap();
		}
	}

	public Map<String, Map<String, String>> getMap() {
		return Collections.unmodifiableMap(map);
	}

	public void setMap(Map<String, Map<String, String>> replacement) {
		Activator.getDefault().saveCustomConfigurationParameters(replacement);
		map = replacement;
	}
}
