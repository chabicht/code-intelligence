package com.chabicht.code_intelligence.model;

import com.chabicht.code_intelligence.Bean;

public class PromptTemplate extends Bean {
	private String name;
	private PromptType type;
	private String connectionName;
	private String modelId;
	private String prompt;
	boolean enabled;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PromptType getType() {
		return type;
	}

	public void setType(PromptType type) {
		propertyChangeSupport.firePropertyChange("type", this.type, this.type = type);
	}

	public String getConnectionName() {
		return connectionName;
	}

	public void setConnectionName(String connectionName) {
		propertyChangeSupport.firePropertyChange("connectionName", this.connectionName,
				this.connectionName = connectionName);
	}

	public String getModelId() {
		return modelId;
	}

	public void setModelId(String modelId) {
		propertyChangeSupport.firePropertyChange("modelId", this.modelId, this.modelId = modelId);
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		propertyChangeSupport.firePropertyChange("prompt", this.prompt, this.prompt = prompt);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		propertyChangeSupport.firePropertyChange("enabled", this.enabled, this.enabled = enabled);
	}

}
