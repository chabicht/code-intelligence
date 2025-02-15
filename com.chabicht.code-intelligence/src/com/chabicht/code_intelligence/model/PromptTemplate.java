package com.chabicht.code_intelligence.model;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Bean;

public class PromptTemplate extends Bean {
	private String name;
	private PromptType type;
	private String connectionName;
	private String modelId;
	private String prompt;
	boolean enabled;
	boolean useByDefault;

	/**
	 * @param connectionName Name of the connection in use. If null, templates for
	 *                       all connections are valid.
	 * @param modelId        ID of the model in use. If null, the model isn't
	 *                       applied in the comparison.
	 * @return True if the template is applicable for the given model coordinates.
	 */
	public boolean isApplicable(String connectionName, String modelId) {
		connectionName = StringUtils.stripToEmpty(connectionName);
		modelId = StringUtils.stripToEmpty(modelId);

		boolean res = true;

		if (!StringUtils.isBlank(this.connectionName) && !StringUtils.isBlank(connectionName)) {
			res &= StringUtils.equalsIgnoreCase(this.connectionName, connectionName);
		}

		if (!StringUtils.isBlank(this.modelId) && !StringUtils.isBlank(modelId)) {
			res &= StringUtils.equalsIgnoreCase(this.modelId, modelId);
		}

		return res;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PromptTemplate other = (PromptTemplate) obj;
		return Objects.equals(name, other.name) && type == other.type;
	}

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

	public boolean isUseByDefault() {
		return useByDefault;
	}

	public void setUseByDefault(boolean useByDefault) {
		propertyChangeSupport.firePropertyChange("useByDefault", this.useByDefault, this.useByDefault = useByDefault);
	}
}
