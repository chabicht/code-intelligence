package com.chabicht.code_intelligence.apiclient;

import java.util.List;

import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.Prompt;

public interface IAiApiClient {
	/**
	 * Queries the models provided by this API.
	 */
	List<AiModel> getModels();

	/**
	 * Performs a completion request with the given prompt.
	 * 
	 * @param modelName        Name of the model to use (from the preferences).
	 * @param completionPrompt The prompt to use.
	 * @return The completion result.
	 */
	CompletionResult performCompletion(String modelName, Prompt completionPrompt);

}
