package com.chabicht.code_intelligence.apiclient;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ChatMessagePayloadUtil {

	private ChatMessagePayloadUtil() {
	}

	public static String compileMessageContent(ChatMessage message) {
		StringBuilder contentBuilder = new StringBuilder(256);
		if (!message.getContext().isEmpty()) {
			contentBuilder.append("Context information:\n\n");
			for (MessageContext ctx : message.getContext()) {
				contentBuilder.append(ctx.compile(true));
				contentBuilder.append("\n");
			}
		}
		if (message.getContent() != null) {
			contentBuilder.append(message.getContent());
		}
		return contentBuilder.toString();
	}

	public static boolean hasImageAttachments(ChatMessage message) {
		return message != null && message.getImageAttachments() != null && !message.getImageAttachments().isEmpty();
	}

	public static boolean shouldUseUserImagePayload(ChatMessage message) {
		return message != null && Role.USER.equals(message.getRole()) && hasImageAttachments(message);
	}

	public static JsonElement buildOpenAiChatContent(ChatMessage message) {
		String text = compileMessageContent(message);
		if (!shouldUseUserImagePayload(message)) {
			return new JsonPrimitive(text);
		}

		JsonArray content = new JsonArray();
		JsonObject textPart = new JsonObject();
		textPart.addProperty("type", "text");
		textPart.addProperty("text", StringUtils.defaultString(text));
		content.add(textPart);

		for (ImageAttachment image : message.getImageAttachments()) {
			JsonObject imagePart = new JsonObject();
			imagePart.addProperty("type", "image_url");
			JsonObject imageUrl = new JsonObject();
			imageUrl.addProperty("url", image.getDataUrl());
			imagePart.add("image_url", imageUrl);
			content.add(imagePart);
		}
		return content;
	}

	public static JsonElement buildXAiContent(ChatMessage message) {
		String text = compileMessageContent(message);
		if (!shouldUseUserImagePayload(message)) {
			return new JsonPrimitive(text);
		}

		JsonArray content = new JsonArray();
		addInputTextPart(content, text);
		addInputImageParts(content, message);
		return content;
	}

	public static JsonArray buildResponsesContent(ChatMessage message) {
		JsonArray content = new JsonArray();
		addInputTextPart(content, compileMessageContent(message));
		if (shouldUseUserImagePayload(message)) {
			addInputImageParts(content, message);
		}
		return content;
	}

	public static JsonArray buildResponsesContent(String text) {
		JsonArray content = new JsonArray();
		addInputTextPart(content, text);
		return content;
	}

	public static void addAnthropicTextAndImageBlocks(JsonArray contentArray, ChatMessage message, String text) {
		boolean hasImages = shouldUseUserImagePayload(message);
		if (StringUtils.isNotBlank(text) || hasImages) {
			JsonObject textContent = new JsonObject();
			textContent.addProperty("type", "text");
			textContent.addProperty("text", StringUtils.defaultString(text));
			contentArray.add(textContent);
		}
		if (!hasImages) {
			return;
		}
		for (ImageAttachment image : message.getImageAttachments()) {
			JsonObject imageContent = new JsonObject();
			imageContent.addProperty("type", "image");
			JsonObject source = new JsonObject();
			source.addProperty("type", "base64");
			source.addProperty("media_type", image.getMediaType());
			source.addProperty("data", image.getBase64Data());
			imageContent.add("source", source);
			contentArray.add(imageContent);
		}
	}

	public static void addGeminiTextAndImageParts(JsonArray partsArray, ChatMessage message) {
		String text = compileMessageContent(message);
		JsonObject textPart = new JsonObject();
		textPart.addProperty("text", StringUtils.defaultString(text));
		partsArray.add(textPart);

		if (!shouldUseUserImagePayload(message)) {
			return;
		}
		for (ImageAttachment image : message.getImageAttachments()) {
			JsonObject inlineData = new JsonObject();
			inlineData.addProperty("mime_type", image.getMediaType());
			inlineData.addProperty("data", image.getBase64Data());
			JsonObject imagePart = new JsonObject();
			imagePart.add("inline_data", inlineData);
			partsArray.add(imagePart);
		}
	}

	public static JsonArray buildOllamaImages(ChatMessage message) {
		JsonArray images = new JsonArray();
		if (!shouldUseUserImagePayload(message)) {
			return images;
		}
		for (ImageAttachment image : message.getImageAttachments()) {
			images.add(image.getBase64Data());
		}
		return images;
	}

	private static void addInputTextPart(JsonArray content, String text) {
		JsonObject contentItem = new JsonObject();
		contentItem.addProperty("type", "input_text");
		contentItem.addProperty("text", StringUtils.defaultString(text));
		content.add(contentItem);
	}

	private static void addInputImageParts(JsonArray content, ChatMessage message) {
		for (ImageAttachment image : message.getImageAttachments()) {
			JsonObject imagePart = new JsonObject();
			imagePart.addProperty("type", "input_image");
			imagePart.addProperty("image_url", image.getDataUrl());
			content.add(imagePart);
		}
	}
}
