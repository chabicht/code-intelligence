package com.chabicht.code_intelligence.chat;

import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.model.PromptTemplate;

public class ChatSettings extends Bean {
	private String model;
	private PromptTemplate promptTemplate;
	private boolean reasoningEnabled;
	private int reasoningTokens = 8192;

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		propertyChangeSupport.firePropertyChange("model", this.model, this.model = model);
	}

	public PromptTemplate getPromptTemplate() {
		return promptTemplate;
	}

	public void setPromptTemplate(PromptTemplate promptTemplate) {
		propertyChangeSupport.firePropertyChange("promptTemplate", this.promptTemplate,
				this.promptTemplate = promptTemplate);
	}

	public boolean isReasoningEnabled() {
		return reasoningEnabled;
	}

	public void setReasoningEnabled(boolean reasoningEnabled) {
		propertyChangeSupport.firePropertyChange("reasoningEnabled", this.reasoningEnabled,
				this.reasoningEnabled = reasoningEnabled);
	}

	public int getReasoningTokens() {
		return reasoningTokens;
	}

	public void setReasoningTokens(int reasoningTokens) {
		propertyChangeSupport.firePropertyChange("reasoningTokens", this.reasoningTokens,
				this.reasoningTokens = reasoningTokens);
	}
}
