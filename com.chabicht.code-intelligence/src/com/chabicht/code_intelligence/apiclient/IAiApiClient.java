package com.chabicht.code_intelligence.apiclient;

import java.util.List;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;

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
	CompletionResult performCompletion(String modelName, CompletionPrompt completionPrompt);

	/**
	 * Sends a chat request in streaming mode using the current ChatConversation.
	 * <p>
	 * This method does the following:
	 * <ol>
	 * <li>Builds the JSON request from the conversation messages already present.
	 * (It does not include a reply message yet.)</li>
	 * <li>Adds a new (empty) assistant message to the conversation which will be
	 * updated as the API response streams in.</li>
	 * <li>Sends the request with "stream": true and processes the response
	 * line-by-line.</li>
	 * <li>As each new chunk arrives, it appends the new text to the assistant
	 * message, notifies the conversation listeners, and calls the optional onChunk
	 * callback.</li>
	 * </ol>
	 *
	 * @param modelName the model to use (for example, "gpt-4")
	 * @param chat      the ChatConversation object containing the conversation so
	 *                  far
	 * @param onChunk   a Consumer callback invoked with each new text chunk (may be
	 *                  null)
	 */
	void performChat(String modelName, ChatConversation chat);

	void abortChat();

	boolean isChatPending();

}
