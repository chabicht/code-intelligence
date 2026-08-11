package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.ToolCallDetail;

/**
 * Covers the configurable "copy entire conversation" output.
 */
public class ChatCopyOptionsExportTest {

	private static final String LONG_PATCH = "diff --git a/Example.java b/Example.java\n"
			+ "@@ -1,3 +1,4 @@\n-old line\n+new line\n";

	private static ChatConversation conversationWithToolCall(String content, String thinkingContent) {
		ChatConversation conversation = new ChatConversation();
		ChatMessage message = new ChatMessage(Role.ASSISTANT, content);
		if (thinkingContent != null) {
			message.setThinkingContent(thinkingContent);
			message.setThinkingComplete(true);
		}

		FunctionCall call = new FunctionCall("call-1", "apply_patch", "{}");
		call.addPrettyParam("file_name", "Example.java", false);
		call.addPrettyParam("patch_content", LONG_PATCH, true);
		FunctionResult result = new FunctionResult("call-1", "apply_patch");
		result.addPrettyResult("status", "Success", false);
		result.addPrettyResult("file_content", "line one\nline two\nline three", true);

		FunctionCallBatch batch = new FunctionCallBatch("batch-1");
		batch.addCall(call);
		batch.setResultForCall(0, result);
		message.setFunctionCallBatch(batch);

		conversation.addMessage(message, false);
		return conversation;
	}

	private static String export(ChatConversation conversation, boolean reasoning, ToolCallDetail detail) {
		return ChatMarkdownExporter.exportConversation(conversation, new Date(0),
				new ChatCopyOptions(reasoning, detail));
	}

	@Test
	void nestsToolCallsBelowTheMessageHeading() {
		String markdown = export(conversationWithToolCall("Applied it.", null), true, ToolCallDetail.DETAILED);

		assertTrue(markdown.contains("## Assistant"));
		assertTrue(markdown.contains("### Tool Call apply_patch"));
		assertTrue(markdown.contains("#### Parameters"));
		assertTrue(markdown.contains("#### Results"));
		assertFalse(markdown.contains("\n## Tool Call"));
	}

	@Test
	void rendersOutOfBandReasoningAsBlockquote() {
		String markdown = export(conversationWithToolCall("Applied it.", "First I check the file."), true,
				ToolCallDetail.DETAILED);

		assertTrue(markdown.contains("### Reasoning"));
		assertTrue(markdown.contains("> First I check the file."));
	}

	@Test
	void rendersInlineReasoningTheSameWayAsOutOfBand() {
		String inline = export(conversationWithToolCall("<think>First I check the file.</think>Applied it.", null),
				true, ToolCallDetail.DETAILED);
		String outOfBand = export(conversationWithToolCall("Applied it.", "First I check the file."), true,
				ToolCallDetail.DETAILED);

		assertEquals(outOfBand, inline);
	}

	@Test
	void omitsReasoningAndLeavesNoTagResidue() {
		String markdown = export(conversationWithToolCall("<think>Internal.</think>Applied it.", null), false,
				ToolCallDetail.DETAILED);

		assertFalse(markdown.contains("Reasoning"));
		assertFalse(markdown.contains("Internal."));
		assertFalse(markdown.contains("<think>"));
		assertTrue(markdown.contains("Applied it."));
	}

	@Test
	void briefKeepsShortValuesAndElidesBulkOnes() {
		String markdown = export(conversationWithToolCall("Applied it.", null), true, ToolCallDetail.BRIEF);

		assertTrue(markdown.contains("**file_name:** Example.java"));
		assertTrue(markdown.contains("**status:** Success"));
		assertFalse(markdown.contains("diff --git"));
		assertFalse(markdown.contains("line three"));
		assertTrue(markdown.contains("**patch_content:** _(omitted, 4 lines)_"));
		assertTrue(markdown.contains("**file_content:** _(omitted, 3 lines)_"));
	}

	@Test
	void briefKeepsShortMarkdownFlaggedValuesSuchAsRegexPatterns() {
		ChatConversation conversation = new ChatConversation();
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "Searching.");
		FunctionCall call = new FunctionCall("call-1", "find_in_files", "{}");
		call.addPrettyParam("search_term", "foo.*bar", true); // markdown only for styling
		FunctionCallBatch batch = new FunctionCallBatch("batch-1");
		batch.addCall(call);
		message.setFunctionCallBatch(batch);
		conversation.addMessage(message, false);

		String markdown = export(conversation, true, ToolCallDetail.BRIEF);

		assertTrue(markdown.contains("**search_term:** foo.*bar"));
		assertFalse(markdown.contains("omitted"));
	}

	@Test
	void toolCallsOffDropsTheWholeSection() {
		String markdown = export(conversationWithToolCall("Applied it.", null), true, ToolCallDetail.OFF);

		assertFalse(markdown.contains("Tool Call"));
		assertFalse(markdown.contains("Parameters"));
		assertTrue(markdown.contains("Applied it."));
	}

	@Test
	void dropsMessagesThatEndUpEmptyIncludingTheirSeparator() {
		ChatConversation conversation = new ChatConversation();
		conversation.addMessage(new ChatMessage(Role.USER, "Fix it."), false);

		// Assistant turn that carries nothing but reasoning and a tool call.
		ChatMessage toolOnly = new ChatMessage(Role.ASSISTANT, "");
		toolOnly.setThinkingContent("Thinking about it.");
		FunctionCall call = new FunctionCall("call-1", "read_file_content", "{}");
		call.addPrettyParam("file_name", "Example.java", false);
		FunctionCallBatch batch = new FunctionCallBatch("batch-1");
		batch.addCall(call);
		toolOnly.setFunctionCallBatch(batch);
		conversation.addMessage(toolOnly, false);

		conversation.addMessage(new ChatMessage(Role.ASSISTANT, "Done."), false);

		String withEverything = export(conversation, true, ToolCallDetail.DETAILED);
		assertEquals(3, countOccurrences(withEverything, "\n## "));

		String filtered = export(conversation, false, ToolCallDetail.OFF);
		assertEquals(2, countOccurrences(filtered, "\n## "));
		assertFalse(filtered.contains("Thinking about it."));
		// One separator between the two remaining messages, plus the header rule.
		assertEquals(2, countOccurrences(filtered, "\n---\n"));
		assertFalse(filtered.endsWith("---\n\n"));
	}

	@Test
	void keepsContextOnlyMessages() {
		ChatConversation conversation = new ChatConversation();
		ChatMessage message = new ChatMessage(Role.USER, "");
		message.getContext().add(new MessageContext("Example.java", 1, 1, "class Example {}"));
		conversation.addMessage(message, false);

		String markdown = export(conversation, false, ToolCallDetail.OFF);

		assertTrue(markdown.contains("## User"));
		assertTrue(markdown.contains("### Context"));
	}

	@Test
	void defaultOptionsMatchTheLegacySignature() {
		ChatConversation conversation = conversationWithToolCall("Applied it.", null);

		assertEquals(ChatMarkdownExporter.exportConversation(conversation, new Date(0)),
				export(conversation, true, ToolCallDetail.DETAILED));
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int idx = haystack.indexOf(needle);
		while (idx >= 0) {
			count++;
			idx = haystack.indexOf(needle, idx + needle.length());
		}
		return count;
	}
}
