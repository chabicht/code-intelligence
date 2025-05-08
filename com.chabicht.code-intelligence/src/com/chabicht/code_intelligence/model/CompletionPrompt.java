package com.chabicht.code_intelligence.model;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class CompletionPrompt {
	private final float temperature;
	private final String promptString;
	private final Map<String, Object> promptArgs;
	private String markdown;

	public CompletionPrompt(float temperature, String promptString, Map<String, Object> promptArgs) {
		this.temperature = temperature;
		this.promptString = promptString;
		this.promptArgs = promptArgs;
	}

	public float getTemperature() {
		return temperature;
	}

	public String getPromptString() {
		return promptString;
	}

	public Map<String, Object> getPromptArgs() {
		return promptArgs;
	}

	public String compile() {
		if (markdown != null)
			return markdown;
		
		Template tmpl = Mustache.compiler().escapeHTML(false).compile(StringUtils.stripToEmpty(promptString));
		markdown = tmpl.execute(promptArgs);
		return markdown;
	}
}
