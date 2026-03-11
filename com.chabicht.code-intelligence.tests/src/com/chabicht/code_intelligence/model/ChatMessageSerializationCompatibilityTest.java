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
import com.chabicht.code_intelligence.util.GsonUtil;
import com.google.gson.Gson;

public class ChatMessageSerializationCompatibilityTest {

	private final Gson gson = GsonUtil.createGson();

	@Test
	void deserializesLegacySingularToolFieldsIntoBatch() {
		String json = """
				{
				  "role": "ASSISTANT",
				  "content": "",
				  "functionCall": {
				    "id": "call-1",
				    "functionName": "read_file_content",
				    "argsJson": "{\\"file_name\\":\\"Example.java\\"}"
				  },
				  "functionResult": {
				    "id": "call-1",
				    "functionName": "read_file_content",
				    "resultJson": "{\\"status\\":\\"Success\\"}"
				  }
				}
				""";

		ChatMessage message = gson.fromJson(json, ChatMessage.class);

		assertTrue(message.getFunctionCallBatch().isPresent(), "Legacy singular JSON should be normalized into a batch");
		FunctionCallBatch batch = message.getFunctionCallBatch().get();
		assertEquals(1, batch.getItems().size());
		assertEquals("read_file_content", batch.getItems().get(0).getCall().getFunctionName());
		assertEquals("call-1", batch.getItems().get(0).getResult().getId());
	}

	@Test
	void batchStoresCallAndResultOnFirstItem() {
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-accessor");

		FunctionCall firstCall = new FunctionCall("call-1", "find_files", "{\"pattern\":\"*.java\"}");
		FunctionResult firstResult = new FunctionResult("call-1", "find_files");
		firstResult.setResultJson("{\"status\":\"Success\"}");
		FunctionCall secondCall = new FunctionCall("call-2", "search_text", "{\"query\":\"TODO\"}");

		batch.addCall(firstCall);
		batch.addCall(secondCall);
		batch.setResultForCall(0, firstResult);
		message.setFunctionCallBatch(batch);

		assertEquals(2, message.getFunctionCallBatch().get().getItems().size());
		assertEquals("call-1", message.getFunctionCallBatch().get().getItems().get(0).getCall().getId());
		assertEquals("call-1", message.getFunctionCallBatch().get().getItems().get(0).getResult().getId());
	}

	@Test
	void serializesBatchToolStateWithoutLegacySingularFields() {
		ChatMessage message = new ChatMessage(Role.ASSISTANT, "");
		FunctionCall call = new FunctionCall("call-1", "read_file_content", "{\"file_name\":\"Example.java\"}");
		FunctionResult result = new FunctionResult("call-1", "read_file_content");
		result.setResultJson("{\"status\":\"Success\"}");

		FunctionCallBatch batch = new FunctionCallBatch("batch-serialize");
		batch.addCall(call);
		batch.setResultForCall(0, result);
		message.setFunctionCallBatch(batch);

		String json = gson.toJson(message);

		assertTrue(json.contains("\"functionCallBatch\""));
		assertFalse(json.contains("\"functionCall\""), "New serialization should not emit the legacy singular call field");
		assertFalse(json.contains("\"functionResult\""),
				"New serialization should not emit the legacy singular result field");
	}
}
