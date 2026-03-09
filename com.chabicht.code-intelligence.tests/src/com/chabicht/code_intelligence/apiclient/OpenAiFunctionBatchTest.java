package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenAiFunctionBatchTest {

	@Test
	void buildMessagesJsonIncludesAllBatchToolCallsAndResults() throws Exception {
		OpenAiApiClient client = new OpenAiApiClient(createConnection());
		ChatConversation chat = new ChatConversation();

		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = createBatchWithResults();
		assistantMessage.setFunctionCallBatch(batch);
		assistantMessage.setFunctionCall(batch.getItems().get(0).getCall());
		assistantMessage.setFunctionResult(batch.getItems().get(0).getResult());
		chat.addMessage(assistantMessage, false);

		JsonArray messages = invokeBuildMessagesJson(client, chat);
		assertEquals(3, messages.size(), "Batch replay should serialize one assistant turn plus two tool result messages");

		JsonObject assistantJson = messages.get(0).getAsJsonObject();
		assertEquals("assistant", assistantJson.get("role").getAsString());
		assertEquals(2, assistantJson.getAsJsonArray("tool_calls").size());
		assertEquals("call-1",
				assistantJson.getAsJsonArray("tool_calls").get(0).getAsJsonObject().get("id").getAsString());
		assertEquals("call-2",
				assistantJson.getAsJsonArray("tool_calls").get(1).getAsJsonObject().get("id").getAsString());

		JsonObject firstToolResult = messages.get(1).getAsJsonObject();
		assertEquals("tool", firstToolResult.get("role").getAsString());
		assertEquals("call-1", firstToolResult.get("tool_call_id").getAsString());

		JsonObject secondToolResult = messages.get(2).getAsJsonObject();
		assertEquals("tool", secondToolResult.get("role").getAsString());
		assertEquals("call-2", secondToolResult.get("tool_call_id").getAsString());
	}

	@Test
	void buildMessagesJsonKeepsLegacySingleCallPath() throws Exception {
		OpenAiApiClient client = new OpenAiApiClient(createConnection());
		ChatConversation chat = new ChatConversation();

		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCall legacyCall = new FunctionCall("legacy-call", "find_files", "{\"query\":\"*.md\"}");
		FunctionResult legacyResult = new FunctionResult("legacy-call", "find_files");
		legacyResult.setResultJson("{\"status\":\"ok\",\"items\":[]}");
		assistantMessage.setFunctionCall(legacyCall);
		assistantMessage.setFunctionResult(legacyResult);
		chat.addMessage(assistantMessage, false);

		JsonArray messages = invokeBuildMessagesJson(client, chat);
		assertEquals(2, messages.size(), "Legacy replay should stay as assistant + one tool result");
		assertEquals(1, messages.get(0).getAsJsonObject().getAsJsonArray("tool_calls").size());
		assertEquals("legacy-call", messages.get(1).getAsJsonObject().get("tool_call_id").getAsString());
	}

	@Test
	void finalizeToolCallsAggregatesMultipleStreamedToolCallsIntoBatch() throws Exception {
		OpenAiApiClient client = new OpenAiApiClient(createConnection());
		ChatConversation chat = new ChatConversation();
		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		AtomicInteger functionCallNotifications = new AtomicInteger();

		chat.addListener(new ChatListener() {
			@Override
			public void onMessageAdded(ChatMessage message, boolean updating) {
			}

			@Override
			public void onMessageUpdated(ChatMessage message) {
			}

			@Override
			public void onFunctionCall(ChatMessage message) {
				functionCallNotifications.incrementAndGet();
			}

			@Override
			public void onChatResponseFinished(ChatMessage message) {
			}
		});

		Map<Integer, Object> activeToolCalls = new TreeMap<>();
		invokeHandleToolCallDelta(client, activeToolCalls, assistantMessage, chat, jsonArray("""
				[
				  {
				    "index": 1,
				    "id": "call-2",
				    "function": {
				      "name": "read_file_content",
				      "arguments": "{\\"path\\":\\""
				    }
				  },
				  {
				    "index": 0,
				    "id": "call-1",
				    "function": {
				      "name": "find_files",
				      "arguments": "{\\"query\\":\\""
				    }
				  }
				]
				"""));
		invokeHandleToolCallDelta(client, activeToolCalls, assistantMessage, chat, jsonArray("""
				[
				  {
				    "index": 1,
				    "function": {
				      "arguments": "/project/A.java\\"}"
				    }
				  },
				  {
				    "index": 0,
				    "function": {
				      "arguments": "*.java\\"}"
				    }
				  }
				]
				"""));
		invokeFinalizeToolCalls(client, activeToolCalls, assistantMessage, chat);

		assertTrue(assistantMessage.getFunctionCallBatch().isPresent(),
				"Multiple streamed tool calls should be attached as one FunctionCallBatch");
		FunctionCallBatch batch = assistantMessage.getFunctionCallBatch().get();
		assertEquals(2, batch.getItems().size());
		assertEquals("call-1", batch.getItems().get(0).getCall().getId());
		assertEquals("find_files", batch.getItems().get(0).getCall().getFunctionName());
		assertEquals("{\"query\":\"*.java\"}", batch.getItems().get(0).getCall().getArgsJson());
		assertEquals("call-2", batch.getItems().get(1).getCall().getId());
		assertEquals("read_file_content", batch.getItems().get(1).getCall().getFunctionName());
		assertEquals("{\"path\":\"/project/A.java\"}", batch.getItems().get(1).getCall().getArgsJson());
		assertEquals(1, functionCallNotifications.get(), "Function-call notification should fire once per assistant turn");
		assertTrue(assistantMessage.getFunctionCall().isPresent(), "Legacy first-call shim should remain populated");
		assertEquals("call-1", assistantMessage.getFunctionCall().get().getId());
	}

	private FunctionCallBatch createBatchWithResults() {
		FunctionCall callOne = new FunctionCall("call-1", "find_files", "{\"query\":\"*.java\"}");
		FunctionResult resultOne = new FunctionResult("call-1", "find_files");
		resultOne.setResultJson("{\"status\":\"ok\",\"items\":[\"A.java\"]}");

		FunctionCall callTwo = new FunctionCall("call-2", "read_file_content", "{\"path\":\"/project/A.java\"}");
		FunctionResult resultTwo = new FunctionResult("call-2", "read_file_content");
		resultTwo.setResultJson("{\"status\":\"ok\",\"content\":\"class A {}\"}");

		FunctionCallBatch batch = new FunctionCallBatch("batch-openai");
		batch.setItems(
				java.util.List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		return batch;
	}

	private JsonArray invokeBuildMessagesJson(OpenAiApiClient client, ChatConversation chat) throws Exception {
		Method method = OpenAiApiClient.class.getDeclaredMethod("buildMessagesJson", ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private void invokeHandleToolCallDelta(OpenAiApiClient client, Map<Integer, Object> activeToolCalls,
			ChatMessage assistantMessage, ChatConversation chat, JsonArray toolCallDeltas) throws Exception {
		Method method = OpenAiApiClient.class.getDeclaredMethod("handleToolCallDelta", JsonArray.class, Map.class,
				ChatMessage.class, ChatConversation.class);
		method.setAccessible(true);
		method.invoke(client, toolCallDeltas, activeToolCalls, assistantMessage, chat);
	}

	private void invokeFinalizeToolCalls(OpenAiApiClient client, Map<Integer, Object> activeToolCalls,
			ChatMessage assistantMessage, ChatConversation chat) throws Exception {
		Method method = OpenAiApiClient.class.getDeclaredMethod("finalizeToolCalls", Map.class, ChatMessage.class,
				ChatConversation.class);
		method.setAccessible(true);
		method.invoke(client, activeToolCalls, assistantMessage, chat);
	}

	private JsonArray jsonArray(String payload) {
		return JsonParser.parseString(payload).getAsJsonArray();
	}

	private AiApiConnection createConnection() {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.OPENAI);
		connection.setApiKey("test-key");
		connection.setEnabled(true);
		return connection;
	}
}
