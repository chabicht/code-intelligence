package com.chabicht.code_intelligence.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.Tuple;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.util.ModelUtil;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ChatSettings extends Bean {
	public static enum ReasoningControlMode {
		NONE, TOKEN_BUDGET, EFFORT, OLLAMA_EFFORT;
	}

	public static enum ReasoningEffort {
		DEFAULT("Model default", null), NONE("None", "none"), MINIMAL("Minimal", "minimal"), LOW("Low", "low"),
		MEDIUM("Medium", "medium"), HIGH("High", "high"), XHIGH("XHigh", "xhigh");

		private final String displayName;
		private final String apiValue;

		private ReasoningEffort(String displayName, String apiValue) {
			this.displayName = displayName;
			this.apiValue = apiValue;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getApiValue() {
			return apiValue;
		}
	}

	private String model;
	private PromptTemplate promptTemplate;
	private boolean reasoningEnabled = true;
	private int maxResponseTokens = getDefaultMaxChatTokens();
	private int reasoningTokens = 8192;
	private ReasoningEffort reasoningEffort = ReasoningEffort.DEFAULT;
	private boolean toolsEnabled = true;
	private ToolProfile toolProfile = loadDefaultToolProfile();
	private Map<String, Boolean> toolEnabledStates = new HashMap<>();
	private static final ReasoningEffort[] OLLAMA_REASONING_EFFORTS = new ReasoningEffort[] {
			ReasoningEffort.DEFAULT, ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.MEDIUM,
			ReasoningEffort.HIGH };

	private static int getDefaultMaxChatTokens() {
		Activator activator = Activator.getDefault();
		return activator != null ? activator.getMaxChatTokens() : 8192;
	}

	private static ToolProfile loadDefaultToolProfile() {
		try {
			String stored = Activator.getDefault().getPreferenceStore()
					.getString(PreferenceConstants.CHAT_TOOL_PROFILE);
			if (stored != null && !stored.isEmpty()) {
				// Backward compatibility for removed profile.
				if ("READ_WRITE".equals(stored)) {
					return ToolProfile.ALL;
				}
				return ToolProfile.valueOf(stored);
			}
		} catch (Exception e) {
			// ignore, use default
		}
		return ToolProfile.ALL;
	}

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

	public ReasoningEffort getReasoningEffort() {
		return reasoningEffort;
	}

	public void setReasoningEffort(ReasoningEffort reasoningEffort) {
		propertyChangeSupport.firePropertyChange("reasoningEffort", this.reasoningEffort,
				this.reasoningEffort = reasoningEffort != null ? reasoningEffort : ReasoningEffort.DEFAULT);
	}

	public boolean hasExplicitReasoningEffort() {
		return getEffectiveReasoningEffort().getApiValue() != null;
	}

	public ReasoningEffort getEffectiveReasoningEffort() {
		return normalizeReasoningEffort(getReasoningControlMode(), reasoningEffort);
	}

	public boolean isToolsEnabled() {
		return toolsEnabled;
	}

	public void setToolsEnabled(boolean toolsEnabled) {
		propertyChangeSupport.firePropertyChange("toolsEnabled", this.toolsEnabled, this.toolsEnabled = toolsEnabled);
	}

	public ToolProfile getToolProfile() {
		return toolProfile;
	}

	public void setToolProfile(ToolProfile toolProfile) {
		propertyChangeSupport.firePropertyChange("toolProfile", this.toolProfile, this.toolProfile = toolProfile);
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

	public static ReasoningControlMode getReasoningControlMode(AiApiConnection.ApiType apiType) {
		if (apiType == null) {
			return ReasoningControlMode.NONE;
		}

		switch (apiType) {
		case OPENAI:
		case OPENAI_RESPONSES:
			return ReasoningControlMode.EFFORT;
		case OLLAMA:
			return ReasoningControlMode.OLLAMA_EFFORT;
		case ANTHROPIC:
		case GEMINI:
			return ReasoningControlMode.TOKEN_BUDGET;
		default:
			return ReasoningControlMode.NONE;
		}
	}

	public static ReasoningControlMode getReasoningControlMode(String modelId) {
		Optional<Tuple<String, String>> tuple = ModelUtil.getProviderModelTuple(modelId);
		if (tuple.isEmpty()) {
			return ReasoningControlMode.NONE;
		}

		String connectionName = tuple.get().getFirst();
		for (AiApiConnection connection : ConnectionFactory.getApis()) {
			if (StringUtils.equals(connection.getName(), connectionName)) {
				return getReasoningControlMode(connection.getType());
			}
		}
		return ReasoningControlMode.NONE;
	}

	public static boolean supportsReasoning(String modelId) {
		return getReasoningControlMode(modelId) != ReasoningControlMode.NONE;
	}

	public static ReasoningEffort[] getSupportedReasoningEfforts(ReasoningControlMode mode) {
		if (mode == null) {
			return new ReasoningEffort[0];
		}

		switch (mode) {
		case EFFORT:
			return ReasoningEffort.values();
		case OLLAMA_EFFORT:
			return OLLAMA_REASONING_EFFORTS.clone();
		default:
			return new ReasoningEffort[0];
		}
	}

	public static ReasoningEffort normalizeReasoningEffort(ReasoningControlMode mode, ReasoningEffort effort) {
		ReasoningEffort normalized = effort != null ? effort : ReasoningEffort.DEFAULT;
		if (mode != ReasoningControlMode.OLLAMA_EFFORT) {
			return normalized;
		}

		switch (normalized) {
		case MINIMAL:
			return ReasoningEffort.LOW;
		case XHIGH:
			return ReasoningEffort.HIGH;
		default:
			for (ReasoningEffort supported : OLLAMA_REASONING_EFFORTS) {
				if (supported == normalized) {
					return normalized;
				}
			}
			return ReasoningEffort.DEFAULT;
		}
	}

	public ReasoningControlMode getReasoningControlMode() {
		return getReasoningControlMode(model);
	}

	public boolean isReasoningSupportedAndEnabled() {
		switch (getReasoningControlMode()) {
		case TOKEN_BUDGET:
			return isReasoningEnabled();
		case EFFORT:
		case OLLAMA_EFFORT:
			return hasExplicitReasoningEffort();
		default:
			return false;
		}
	}
}
