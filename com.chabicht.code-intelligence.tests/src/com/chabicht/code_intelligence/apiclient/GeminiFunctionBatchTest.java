package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

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

public class GeminiFunctionBatchTest {

	@Test
	void parseCandidateContentPartsCapturesMultipleFunctionCalls() throws Exception {
		GeminiApiClient client = new GeminiApiClient(createGeminiConnection());
		ChatConversation chat = new ChatConversation();
		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-parse");
		AtomicBoolean thinkingStarted = new AtomicBoolean(false);

		JsonObject candidate = JsonParser.parseString("""
				{
				  "content": {
				    "parts": [
				      {
				        "functionCall": {
				          "id": "call-1",
				          "name": "find_files",
				          "args": { "query": "*.java" }
				        },
				        "thoughtSignature": "sig-1"
				      },
				      {
				        "functionCall": {
				          "id": "call-2",
				          "name": "read_file_content",
				          "args": { "path": "/project/A.java" }
				        }
				      }
				    ]
				  }
				}
				""").getAsJsonObject();

		Method parseMethod = GeminiApiClient.class.getDeclaredMethod("parseCandidateContentParts", ChatConversation.class,
				ChatMessage.class, JsonObject.class, FunctionCallBatch.class);
		parseMethod.setAccessible(true);
		parseMethod.invoke(client, chat, assistantMessage, candidate, batch);

			assertEquals(2, batch.getItems().size(), "All function calls from one candidate should be captured");
			assertEquals("find_files", batch.getItems().get(0).getCall().getFunctionName());
			assertEquals("read_file_content", batch.getItems().get(1).getCall().getFunctionName());
			assertEquals("sig-1", batch.getThoughtSignature(), "First call thought signature should be stored");
		}

		@Test
	void createChatContentsArrayGroupsBatchCallsAndResponses() throws Exception {
		GeminiApiClient client = new GeminiApiClient(createGeminiConnection());
		ChatConversation chat = new ChatConversation();

		ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
		FunctionCallBatch batch = new FunctionCallBatch("batch-serialize");
		batch.setThoughtSignature("sig-batch");

		FunctionCall callOne = new FunctionCall("call-1", "find_files", "{\"query\":\"*.java\"}");
		FunctionResult resultOne = new FunctionResult("call-1", "find_files");
		resultOne.setResultJson("{\"status\":\"ok\",\"items\":[]}");

		FunctionCall callTwo = new FunctionCall("call-2", "read_file_content", "{\"path\":\"/project/A.java\"}");
		FunctionResult resultTwo = new FunctionResult("call-2", "read_file_content");
		resultTwo.setResultJson("{\"status\":\"ok\",\"content\":\"class A {}\"}");

		batch.setItems(java.util.List.of(new FunctionCallItem(callOne, resultOne), new FunctionCallItem(callTwo, resultTwo)));
		assistantMessage.setFunctionCallBatch(batch);
		chat.addMessage(assistantMessage, false);

		JsonArray contents = invokeCreateChatContentsArray(client, chat);
		assertEquals(2, contents.size(), "Batch call turn should serialize as model + user response turns");

		JsonObject modelTurn = contents.get(0).getAsJsonObject();
		assertEquals("model", modelTurn.get("role").getAsString());
		JsonArray modelParts = modelTurn.getAsJsonArray("parts");
		assertEquals(2, modelParts.size());
		assertEquals("find_files", modelParts.get(0).getAsJsonObject().getAsJsonObject("functionCall").get("name").getAsString());
		assertEquals("read_file_content",
				modelParts.get(1).getAsJsonObject().getAsJsonObject("functionCall").get("name").getAsString());
		assertTrue(modelParts.get(0).getAsJsonObject().has("thoughtSignature"));
		assertFalse(modelParts.get(1).getAsJsonObject().has("thoughtSignature"));

		JsonObject userTurn = contents.get(1).getAsJsonObject();
		assertEquals("user", userTurn.get("role").getAsString());
		JsonArray responseParts = userTurn.getAsJsonArray("parts");
		assertEquals(2, responseParts.size());
		assertEquals("call-1",
				responseParts.get(0).getAsJsonObject().getAsJsonObject("functionResponse").get("id").getAsString());
		assertEquals("call-2",
				responseParts.get(1).getAsJsonObject().getAsJsonObject("functionResponse").get("id").getAsString());
	}

	@Test
		void createChatContentsArraySupportsSingleItemBatchPath() throws Exception {
			GeminiApiClient client = new GeminiApiClient(createGeminiConnection());
			ChatConversation chat = new ChatConversation();

			ChatMessage assistantMessage = new ChatMessage(Role.ASSISTANT, "");
			FunctionCall call = new FunctionCall("legacy-call", "find_files", "{\"query\":\"*.md\"}");
			FunctionResult result = new FunctionResult("legacy-call", "find_files");
			result.setResultJson("{\"status\":\"ok\",\"items\":[]}");
			FunctionCallBatch batch = new FunctionCallBatch("batch-single");
			batch.setItems(java.util.List.of(new FunctionCallItem(call, result)));
			assistantMessage.setFunctionCallBatch(batch);
			chat.addMessage(assistantMessage, false);

			JsonArray contents = invokeCreateChatContentsArray(client, chat);
			assertEquals(2, contents.size(), "Single-item batch call/result should serialize as two turns");

		JsonObject modelTurn = contents.get(0).getAsJsonObject();
		assertEquals("model", modelTurn.get("role").getAsString());
		assertEquals(1, modelTurn.getAsJsonArray("parts").size());
		assertNotNull(modelTurn.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("functionCall"));

		JsonObject userTurn = contents.get(1).getAsJsonObject();
		assertEquals("user", userTurn.get("role").getAsString());
		assertEquals(1, userTurn.getAsJsonArray("parts").size());
		assertNotNull(userTurn.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("functionResponse"));
	}

	private JsonArray invokeCreateChatContentsArray(GeminiApiClient client, ChatConversation chat) throws Exception {
		Method m = GeminiApiClient.class.getDeclaredMethod("createChatContentsArray", ChatConversation.class);
		m.setAccessible(true);
		return (JsonArray) m.invoke(client, chat);
	}

	private AiApiConnection createGeminiConnection() {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(AiApiConnection.ApiType.GEMINI);
		connection.setApiKey("test-key");
		connection.setEnabled(true);
		return connection;
	}
}
