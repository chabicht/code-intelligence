package com.chabicht.code_intelligence.model;

import org.apache.commons.lang3.StringUtils;

public enum PromptType {
	INSTRUCT("Instruct"), CHAT("Chat");

	private String label;

	private PromptType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}

	public static PromptType ofLabel(String label) {
		for (PromptType pt : PromptType.values()) {
			if (pt.getLabel().equalsIgnoreCase(StringUtils.stripToEmpty(label))) {
				return pt;
			}
		}
		return null;
	}
}
