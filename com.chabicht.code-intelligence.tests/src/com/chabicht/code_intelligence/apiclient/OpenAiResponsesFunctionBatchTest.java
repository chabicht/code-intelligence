package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenAiResponsesFunctionBatchTest {

	@Test
	void buildInputItemsForConversationIncludesAllBatchFunctionOutputs() throws Exception {
		OpenAiResponsesApiClient client = new OpenAiResponsesApiClient(createConnection());
		ChatConversation chat = new ChatConversation();

		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = createBatchWithResults();
		assistantMessage.setFunctionCallBatch(batch);
		chat.addMessage(assistantMessage, false);

		JsonArray input = invokeBuildInputItemsForConversation(client, chat);
		assertEquals(List.of("call-1", "call-2"), collectFunctionCallOutputIds(input),
				"All batch results should be serialized once");
	}

	@Test
	void buildIncrementalInputItemsIncludesAllBatchFunctionOutputs() throws Exception {
		OpenAiResponsesApiClient client = new OpenAiResponsesApiClient(createConnection());
		ChatConversation chat = new ChatConversation();

		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		assistantMessage.setMetadata("openai_response_id", "resp_123");
		FunctionCallBatch batch = createBatchWithResults();
		assistantMessage.setFunctionCallBatch(batch);
		chat.addMessage(assistantMessage, false);

		JsonArray input = invokeBuildIncrementalInputItems(client, chat, "resp_123");
		assertEquals(List.of("call-1", "call-2"), collectFunctionCallOutputIds(input),
				"Incremental replay should include all function_call_output items");
	}

	@Test
	void handleStreamingEventAggregatesMultipleFunctionCallsIntoBatch() throws Exception {
		OpenAiResponsesApiClient client = new OpenAiResponsesApiClient(createConnection());
		ChatConversation chat = new ChatConversation();
		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");

		Object accumulator = createAccumulator(client);
		Method handleStreamingEvent = findHandleStreamingEventMethod(accumulator.getClass());

		invokeHandleStreamingEvent(handleStreamingEvent, client, accumulator, assistantMessage, chat, json("""
				{
				  "type": "response.output_item.added",
				  "output_index": 0,
				  "item": {
				    "type": "function_call",
				    "id": "fc_item_1",
				    "call_id": "call-1",
				    "name": "find_files"
				  }
				}
				"""));
		invokeHandleStreamingEvent(handleStreamingEvent, client, accumulator, assistantMessage, chat, json("""
				{
				  "type": "response.function_call_arguments.done",
				  "output_index": 0,
				  "item": {
				    "type": "function_call",
				    "id": "fc_item_1",
				    "call_id": "call-1",
				    "name": "find_files",
				    "arguments": "{\\"query\\":\\"*.java\\"}"
				  }
				}
				"""));
		invokeHandleStreamingEvent(handleStreamingEvent, client, accumulator, assistantMessage, chat, json("""
				{
				  "type": "response.output_item.added",
				  "output_index": 1,
				  "item": {
				    "type": "function_call",
				    "id": "fc_item_2",
				    "call_id": "call-2",
				    "name": "read_file_content"
				  }
				}
				"""));
		invokeHandleStreamingEvent(handleStreamingEvent, client, accumulator, assistantMessage, chat, json("""
				{
				  "type": "response.function_call_arguments.done",
				  "output_index": 1,
				  "item": {
				    "type": "function_call",
				    "id": "fc_item_2",
				    "call_id": "call-2",
				    "name": "read_file_content",
				    "arguments": "{\\"path\\":\\"/project/A.java\\"}"
				  }
				}
				"""));
		invokeHandleStreamingEvent(handleStreamingEvent, client, accumulator, assistantMessage, chat, json("""
				{
				  "type": "response.completed",
				  "response": {
				    "id": "resp_456"
				  }
				}
				"""));

		assertTrue(assistantMessage.getFunctionCallBatch().isPresent(),
				"Multiple tool calls should be attached as one FunctionCallBatch");
		FunctionCallBatch parsedBatch = assistantMessage.getFunctionCallBatch().get();
		assertEquals(2, parsedBatch.getItems().size());
		assertEquals("call-1", parsedBatch.getItems().get(0).getCall().getId());
		assertEquals("find_files", parsedBatch.getItems().get(0).getCall().getFunctionName());
		assertEquals("{\"query\":\"*.java\"}", parsedBatch.getItems().get(0).getCall().getArgsJson());
		assertEquals("call-2", parsedBatch.getItems().get(1).getCall().getId());
		assertEquals("read_file_content", parsedBatch.getItems().get(1).getCall().getFunctionName());
		assertEquals("{\"path\":\"/project/A.java\"}", parsedBatch.getItems().get(1).getCall().getArgsJson());
		assertEquals("resp_456", assistantMessage.getMetadata("openai_response_id"));
	}

	private FunctionCallBatch createBatchWithResults() {
		FunctionCall callOne = new FunctionCall("call-1", "find_files", "{\"query\":\"*.java\"}");
		FunctionResult resultOne = new FunctionResult("call-1", "find_files");
		resultOne.setResultJson("{\"status\":\"ok\",\"items\":[\"A.java\"]}");

		FunctionCall callTwo = new FunctionCall("call-2", "read_file_content", "{\"path\":\"/project/A.java\"}");
		FunctionResult resultTwo = new FunctionResult("call-2", "read_file_content");
		resultTwo.setResultJson("{\"status\":\"ok\",\"content\":\"class A {}\"}");

		FunctionCallBatch batch = new FunctionCallBatch("batch-responses");
		batch.setItems(List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		return batch;
	}

	private JsonArray invokeBuildInputItemsForConversation(OpenAiResponsesApiClient client, ChatConversation chat)
			throws Exception {
		Method method = OpenAiResponsesApiClient.class.getDeclaredMethod("buildInputItemsForConversation",
				ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private JsonArray invokeBuildIncrementalInputItems(OpenAiResponsesApiClient client, ChatConversation chat,
			String previousResponseId) throws Exception {
		Method method = OpenAiResponsesApiClient.class.getDeclaredMethod("buildIncrementalInputItems",
				ChatConversation.class, String.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat, previousResponseId);
	}

	private Object createAccumulator(OpenAiResponsesApiClient client) throws Exception {
		Class<?> accumulatorClass = Class
				.forName(OpenAiResponsesApiClient.class.getName() + "$ToolCallAccumulator");
		Constructor<?> constructor = accumulatorClass.getDeclaredConstructor(OpenAiResponsesApiClient.class);
		constructor.setAccessible(true);
		return constructor.newInstance(client);
	}

	private Method findHandleStreamingEventMethod(Class<?> accumulatorClass) throws Exception {
		Method method = OpenAiResponsesApiClient.class.getDeclaredMethod("handleStreamingEvent", String.class,
				JsonObject.class, ChatMessage.class, ChatConversation.class, accumulatorClass);
		method.setAccessible(true);
		return method;
	}

	private void invokeHandleStreamingEvent(Method handleStreamingEvent, OpenAiResponsesApiClient client,
			Object accumulator, ChatMessage assistantMessage, ChatConversation chat, JsonObject payload)
			throws Exception {
		String type = payload.get("type").getAsString();
		handleStreamingEvent.invoke(client, type, payload, assistantMessage, chat, accumulator);
	}

	private List<String> collectFunctionCallOutputIds(JsonArray input) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < input.size(); i++) {
			JsonObject item = input.get(i).getAsJsonObject();
			if (item.has("type") && "function_call_output".equals(item.get("type").getAsString())) {
				ids.add(item.get("call_id").getAsString());
			}
		}
		return ids;
	}

	private JsonObject json(String payload) {
		return JsonParser.parseString(payload).getAsJsonObject();
	}

	private AiApiConnection createConnection() {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.OPENAI_RESPONSES);
		connection.setApiKey("test-key");
		connection.setEnabled(true);
		return connection;
	}
}
