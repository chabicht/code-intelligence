package com.chabicht.code_intelligence.model;

public class CompletionPrompt {
	private final float temperature;
	private final String promptString;
	private final Object[] promptArgs;

	public CompletionPrompt(float temperature, String promptString, Object... promptArgs) {
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

	public Object[] getPromptArgs() {
		return promptArgs;
	}

	public String compile() {
		return String.format(promptString, promptArgs);
	}
}
