package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public class ChatMarkdownExporterTest {

	@Test
	void exportsSingleMessageImagesAsDataUrlMarkdown() {
		ChatMessage message = new ChatMessage(Role.USER, "What is shown?");
		message.getImageAttachments()
				.add(new ImageAttachment("screenshot.png", "image/png", "iVBORw0KGgo=", 10, 20, 8));

		String markdown = ChatMarkdownExporter.exportMessage(message);

		assertTrue(markdown.contains("What is shown?"));
		assertTrue(markdown.contains("![screenshot.png](data:image/png;base64,iVBORw0KGgo=)"));
	}

	@Test
	void exportsConversationWithContextImagesAndToolDetails() {
		ChatConversation conversation = new ChatConversation();
		conversation.setCaption("Investigation");
		ChatMessage message = new ChatMessage(Role.USER, "Please inspect this.");
		message.getContext().add(new MessageContext("Example.java", 1, 1, "class Example {}"));
		message.getImageAttachments()
				.add(new ImageAttachment("photo.jpg", "image/jpeg", "/9j/4AAQSkZJRg==", 4, 5, 11));
		conversation.addMessage(message, false);

		String markdown = ChatMarkdownExporter.exportConversation(conversation, new Date(0));

		assertTrue(markdown.contains("# Chat Conversation"));
		assertTrue(markdown.contains("**Title:** Investigation"));
		assertTrue(markdown.contains("## User"));
		assertTrue(markdown.contains("### Context"));
		assertTrue(markdown.contains("Example.java line 1 to 1"));
		assertTrue(markdown.contains("![photo.jpg](data:image/jpeg;base64,/9j/4AAQSkZJRg==)"));
		assertFalse(markdown.contains("null"));
	}
}
