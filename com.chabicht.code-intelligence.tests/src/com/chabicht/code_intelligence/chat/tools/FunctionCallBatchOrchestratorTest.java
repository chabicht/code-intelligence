package com.chabicht.code_intelligence.chat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public class FunctionCallBatchOrchestratorTest {

	@Test
	void executesAllCallsAndContinuesWhenCallFails() {
		FunctionCallSession session = new FunctionCallSession();
		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-orchestrator");

		// Invalid function names trigger error results without touching workspace APIs.
		batch.addCall(new FunctionCall("call-1", "", "{}"));
		batch.addCall(new FunctionCall("call-2", "", "{}"));
		assistantMessage.setFunctionCallBatch(batch);

		session.enqueueBatch(assistantMessage);
		FunctionCallSession.BatchExecutionReport report = session.executePendingBatchesSequentially();

		assertEquals(1, report.getBatchesExecuted());
		assertEquals(2, report.getCallsExecuted());
		assertEquals(2, report.getCallsFailed());
		assertTrue(batch.isExecutionComplete());
		assertEquals(2, batch.getItems().size());
		assertNotNull(batch.getItems().get(0).getResult());
		assertNotNull(batch.getItems().get(1).getResult());
		assertTrue(batch.getItems().get(0).getResult().getResultJson().contains("\"status\""));
		assertTrue(batch.getItems().get(1).getResult().getResultJson().contains("\"status\""));
	}

	@Test
	void executesSpecificBatchWithoutQueueing() {
		FunctionCallSession session = new FunctionCallSession();
		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-direct-execution");
		batch.addCall(new FunctionCall("call-1", "", "{}"));
		batch.addCall(new FunctionCall("call-2", "", "{}"));
		assistantMessage.setFunctionCallBatch(batch);

		FunctionCallSession.BatchExecutionReport report = session.executeBatch(assistantMessage);

		assertEquals(1, report.getBatchesExecuted());
		assertEquals(2, report.getCallsExecuted());
		assertEquals(2, report.getCallsFailed());
		assertEquals(1, report.getUpdatedMessages().size());
		assertEquals(assistantMessage, report.getUpdatedMessages().get(0));
		assertTrue(batch.isExecutionComplete());
		assertNotNull(batch.getItems().get(0).getResult());
		assertNotNull(batch.getItems().get(1).getResult());
	}
}
