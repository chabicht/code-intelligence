package com.chabicht.code_intelligence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public class ChatMessageToolCallMarkdownTest {

	@Test
	void rendersAllBatchToolCallsAndResultsInMarkdown() {
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-markdown");

		FunctionCall readFile = new FunctionCall("call-1", "read_file_content", "{\"file_name\":\"Example.java\"}");
		readFile.addPrettyParam("file_name", "Example.java", false);
		FunctionResult readFileResult = new FunctionResult("call-1", "read_file_content");
		readFileResult.addPrettyResult("status", "Success", false);

		FunctionCall applyPatch = new FunctionCall("call-2", "apply_patch", "{\"patch\":\"diff\"}");
		applyPatch.addPrettyParam("patch", "diff --git a/Example.java b/Example.java", true);
		FunctionResult applyPatchResult = new FunctionResult("call-2", "apply_patch");
		applyPatchResult.addPrettyResult("changes", "3", false);

		batch.addCall(readFile);
		batch.addCall(applyPatch);
		batch.setResultForCall(0, readFileResult);
		batch.setResultForCall(1, applyPatchResult);
		message.setFunctionCallBatch(batch);

		assertTrue(message.hasFunctionCalls());
		assertEquals(2, message.getCallableFunctionItems().size());

		String markdown = message.getToolCallDetailsAsMarkdown();

		assertTrue(markdown.contains("## Tool Call 1/2 read_file_content"));
		assertTrue(markdown.contains("## Tool Call 2/2 apply_patch"));
		assertTrue(markdown.contains("**file_name:** Example.java"));
		assertTrue(markdown.contains("**status:** Success"));
		assertTrue(markdown.contains("**patch:**"));
		assertTrue(markdown.contains("**changes:** 3"));
	}

	@Test
	void rendersSingleItemBatchToolCallMarkdownWithoutIndexSuffix() {
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "");
		FunctionCall call = new FunctionCall("call-legacy", "read_file_content", "{\"file_name\":\"Legacy.java\"}");
		call.addPrettyParam("file_name", "Legacy.java", false);
		FunctionResult result = new FunctionResult("call-legacy", "read_file_content");
		result.addPrettyResult("status", "Success", false);

		FunctionCallBatch batch = new FunctionCallBatch("batch-single");
		batch.addCall(call);
		batch.setResultForCall(0, result);
		message.setFunctionCallBatch(batch);

		assertTrue(message.hasFunctionCalls());
		assertEquals(1, message.getCallableFunctionItems().size());

		String markdown = message.getToolCallDetailsAsMarkdown();

		assertTrue(markdown.contains("## Tool Call read_file_content"));
		assertTrue(markdown.contains("**file_name:** Legacy.java"));
		assertTrue(markdown.contains("**status:** Success"));
		assertFalse(markdown.contains("1/1"));
	}
}
