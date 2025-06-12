package com.chabicht.code_intelligence.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.model.PromptTemplate;

public class ChatSettings extends Bean {
	private static final Pattern CLAUDE_4_PATTERN = Pattern.compile("claude-[^-]+-4");

	private String model;
	private PromptTemplate promptTemplate;
	private boolean reasoningEnabled = true;
	private int maxResponseTokens = Activator.getDefault().getMaxChatTokens();
	private int reasoningTokens = 8192;
	private boolean toolsEnabled = true;
	private Map<String, Boolean> toolEnabledStates = new HashMap<>();

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
		return supportsReasoning(model) && reasoningEnabled;
	}

	public void setReasoningEnabled(boolean reasoningEnabled) {
		propertyChangeSupport.firePropertyChange("reasoningEnabled", this.reasoningEnabled,
				this.reasoningEnabled = reasoningEnabled);
	}

	public int getMaxResponseTokens() {
		return maxResponseTokens;
	}

	public void setMaxResponseTokens(int maxTokens) {
		propertyChangeSupport.firePropertyChange("maxResponseTokens", this.maxResponseTokens,
				this.maxResponseTokens = maxTokens);
	}

	public int getReasoningTokens() {
		return reasoningTokens;
	}

	public void setReasoningTokens(int reasoningTokens) {
		propertyChangeSupport.firePropertyChange("reasoningTokens", this.reasoningTokens,
				this.reasoningTokens = reasoningTokens);
	}

	public boolean isToolsEnabled() {
		return toolsEnabled;
	}

	public void setToolsEnabled(boolean toolsEnabled) {
		propertyChangeSupport.firePropertyChange("toolsEnabled", this.toolsEnabled,
				this.toolsEnabled = toolsEnabled);
	}

	public Map<String, Boolean> getToolEnabledStates() {
		return toolEnabledStates;
	}

	public void setToolEnabledStates(Map<String, Boolean> toolEnabledStates) {
		propertyChangeSupport.firePropertyChange("toolEnabledStates", this.toolEnabledStates,
				this.toolEnabledStates = toolEnabledStates);
	}

	public boolean isToolEnabled(String toolName) {
		return toolEnabledStates.getOrDefault(toolName, true);
	}

	public void setToolEnabled(String toolName, boolean enabled) {
		boolean oldValue = toolEnabledStates.put(toolName, enabled);
		propertyChangeSupport.firePropertyChange("toolEnabledStates." + toolName, oldValue, enabled);
	}

	public static boolean supportsReasoning(String modelId) {
		return modelId != null && (modelId.contains("claude-3-7") || CLAUDE_4_PATTERN.matcher(modelId).find()
				|| modelId.contains("gemini-2.5"));
	}
}
