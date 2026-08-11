package com.chabicht.code_intelligence.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.util.ReasoningSplitter.MessageContentWithReasoning;

public class ReasoningSplitterTest {

	@Test
	void splitsInlineThinkTags() {
		MessageContentWithReasoning split = ReasoningSplitter.split("<think>Let me check.</think>The answer is 42.");

		assertEquals("Let me check.", split.getThoughts());
		assertEquals("The answer is 42.", split.getMessage());
		assertTrue(split.isEndOfReasoningReached());
	}

	@Test
	void treatsUnterminatedThinkTagAsOngoingReasoning() {
		MessageContentWithReasoning split = ReasoningSplitter.split("<think>Still thinking");

		assertEquals("Still thinking", split.getThoughts());
		assertEquals("", split.getMessage());
		assertFalse(split.isEndOfReasoningReached());
	}

	@Test
	void ignoresThinkTagInTheMiddleOfAMessage() {
		String content = "A model may write <think> when talking about reasoning.";
		MessageContentWithReasoning split = ReasoningSplitter.split(content);

		assertEquals("", split.getThoughts());
		assertEquals(content, split.getMessage());
	}

	@Test
	void outOfBandThinkingContentWins() {
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "The answer is 42.");
		message.setThinkingContent("Out-of-band reasoning.");
		message.setThinkingComplete(true);

		MessageContentWithReasoning split = ReasoningSplitter.split(message);

		assertEquals("Out-of-band reasoning.", split.getThoughts());
		assertEquals("The answer is 42.", split.getMessage());
		assertTrue(split.isEndOfReasoningReached());
	}

	@Test
	void stripsSolutionMarkers() {
		MessageContentWithReasoning split = ReasoningSplitter
				.split("<|begin_of_thought|>Hmm.<|end_of_thought|><|begin_of_solution|>Done.<|end_of_solution|>");

		assertEquals("Hmm.", split.getThoughts());
		assertEquals("Done.", split.getMessage());
	}

	@Test
	void handlesNullMessage() {
		MessageContentWithReasoning split = ReasoningSplitter.split((ChatMessage) null);

		assertEquals("", split.getThoughts());
		assertEquals("", split.getMessage());
	}
}
