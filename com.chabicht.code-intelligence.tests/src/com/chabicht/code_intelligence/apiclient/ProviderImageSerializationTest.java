package com.chabicht.code_intelligence.apiclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ProviderImageSerializationTest {

	private static final String BASE64 = "iVBORw0KGgo=";
	private static final String DATA_URL = "data:image/png;base64," + BASE64;

	@Test
	void openAiTextOnlyMessagesKeepStringContent() throws Exception {
		OpenAiApiClient client = new OpenAiApiClient(createConnection(AiApiConnection.ApiType.OPENAI));
		ChatConversation chat = chatWith(new ChatMessage(Role.USER, "hello"));

		JsonArray messages = invokeBuildMessagesJson(OpenAiApiClient.class, client, chat);

		JsonElement content = messages.get(0).getAsJsonObject().get("content");
		assertTrue(content.isJsonPrimitive());
		assertEquals("hello", content.getAsString());
	}

	@Test
	void openAiImageMessagesUseTextThenImageUrlParts() throws Exception {
		OpenAiApiClient client = new OpenAiApiClient(createConnection(AiApiConnection.ApiType.OPENAI));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray messages = invokeBuildMessagesJson(OpenAiApiClient.class, client, chat);

		JsonArray content = messages.get(0).getAsJsonObject().getAsJsonArray("content");
		assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("describe", content.get(0).getAsJsonObject().get("text").getAsString());
		JsonObject imagePart = content.get(1).getAsJsonObject();
		assertEquals("image_url", imagePart.get("type").getAsString());
		assertEquals(DATA_URL, imagePart.getAsJsonObject("image_url").get("url").getAsString());
	}

	@Test
	void responsesImageMessagesUseInputTextThenInputImageParts() throws Exception {
		OpenAiResponsesApiClient client = new OpenAiResponsesApiClient(
				createConnection(AiApiConnection.ApiType.OPENAI_RESPONSES));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray input = invokeBuildInputItemsForConversation(client, chat);

		JsonArray content = input.get(0).getAsJsonObject().getAsJsonArray("content");
		assertEquals("input_text", content.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("describe", content.get(0).getAsJsonObject().get("text").getAsString());
		assertEquals("input_image", content.get(1).getAsJsonObject().get("type").getAsString());
		assertEquals(DATA_URL, content.get(1).getAsJsonObject().get("image_url").getAsString());
	}

	@Test
	void anthropicImageMessagesUseTextThenBase64ImageBlocks() throws Exception {
		AnthropicApiClient client = new AnthropicApiClient(createConnection(AiApiConnection.ApiType.ANTHROPIC));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray messages = invokeCreateMessagesArray(client, chat);

		JsonArray content = messages.get(0).getAsJsonObject().getAsJsonArray("content");
		assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("describe", content.get(0).getAsJsonObject().get("text").getAsString());
		JsonObject imageBlock = content.get(1).getAsJsonObject();
		assertEquals("image", imageBlock.get("type").getAsString());
		JsonObject source = imageBlock.getAsJsonObject("source");
		assertEquals("base64", source.get("type").getAsString());
		assertEquals("image/png", source.get("media_type").getAsString());
		assertEquals(BASE64, source.get("data").getAsString());
	}

	@Test
	void geminiImageMessagesUseTextThenInlineDataParts() throws Exception {
		GeminiApiClient client = new GeminiApiClient(createConnection(AiApiConnection.ApiType.GEMINI));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray contents = invokeCreateChatContentsArray(client, chat);

		JsonArray parts = contents.get(0).getAsJsonObject().getAsJsonArray("parts");
		assertEquals("describe", parts.get(0).getAsJsonObject().get("text").getAsString());
		JsonObject inlineData = parts.get(1).getAsJsonObject().getAsJsonObject("inline_data");
		assertEquals("image/png", inlineData.get("mime_type").getAsString());
		assertEquals(BASE64, inlineData.get("data").getAsString());
	}

	@Test
	void ollamaImageMessagesKeepStringContentAndAddImagesSibling() throws Exception {
		OllamaApiClient client = new OllamaApiClient(createConnection(AiApiConnection.ApiType.OLLAMA));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray messages = invokeBuildMessagesJson(OllamaApiClient.class, client, chat);

		JsonObject message = messages.get(0).getAsJsonObject();
		assertEquals("describe", message.get("content").getAsString());
		assertEquals(BASE64, message.getAsJsonArray("images").get(0).getAsString());
	}

	@Test
	void xAiImageMessagesUseInputTextThenInputImageParts() throws Exception {
		XAiApiClient client = new XAiApiClient(createConnection(AiApiConnection.ApiType.XAI));
		ChatConversation chat = chatWith(imageMessage("describe"));

		JsonArray messages = invokeBuildMessagesJson(XAiApiClient.class, client, chat);

		JsonArray content = messages.get(0).getAsJsonObject().getAsJsonArray("content");
		assertEquals("input_text", content.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("describe", content.get(0).getAsJsonObject().get("text").getAsString());
		assertEquals("input_image", content.get(1).getAsJsonObject().get("type").getAsString());
		assertEquals(DATA_URL, content.get(1).getAsJsonObject().get("image_url").getAsString());
	}

	private ChatMessage imageMessage(String content) {
		ChatMessage message = new ChatMessage(Role.USER, content);
		message.getImageAttachments().add(new ImageAttachment("sample.png", "image/png", BASE64, 3, 2, 8));
		return message;
	}

	private ChatConversation chatWith(ChatMessage message) {
		ChatConversation chat = new ChatConversation();
		chat.addMessage(message, false);
		return chat;
	}

	private JsonArray invokeBuildMessagesJson(Class<?> clientClass, Object client, ChatConversation chat)
			throws Exception {
		Method method = clientClass.getDeclaredMethod("buildMessagesJson", ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private JsonArray invokeBuildInputItemsForConversation(OpenAiResponsesApiClient client, ChatConversation chat)
			throws Exception {
		Method method = OpenAiResponsesApiClient.class.getDeclaredMethod("buildInputItemsForConversation",
				ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private JsonArray invokeCreateMessagesArray(AnthropicApiClient client, ChatConversation chat) throws Exception {
		Method method = AnthropicApiClient.class.getDeclaredMethod("createMessagesArray", ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private JsonArray invokeCreateChatContentsArray(GeminiApiClient client, ChatConversation chat) throws Exception {
		Method method = GeminiApiClient.class.getDeclaredMethod("createChatContentsArray", ChatConversation.class);
		method.setAccessible(true);
		return (JsonArray) method.invoke(client, chat);
	}

	private AiApiConnection createConnection(AiApiConnection.ApiType type) {
		AiApiConnection connection = new AiApiConnection();
		connection.setType(type);
		connection.setApiKey("test-key");
		connection.setEnabled(true);
		return connection;
	}
}
