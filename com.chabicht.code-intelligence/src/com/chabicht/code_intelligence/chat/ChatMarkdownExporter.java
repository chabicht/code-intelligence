package com.chabicht.code_intelligence.chat;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public final class ChatMarkdownExporter {

	private ChatMarkdownExporter() {
	}

	public static String exportMessage(ChatMessage message) {
		if (message == null) {
			return "";
		}

		StringBuilder markdown = new StringBuilder();
		if (StringUtils.isNotBlank(message.getContent())) {
			markdown.append(message.getContent());
		}

		appendContextSection(markdown, "# Context:", message.getContext(), true);
		appendImageSection(markdown, message.getImageAttachments());
		markdown.append(message.getToolCallDetailsAsMarkdown());
		return markdown.toString();
	}

	public static String exportConversation(ChatConversation conversation, Date exportedAt) {
		if (conversation == null || conversation.getMessages().isEmpty()) {
			return "";
		}

		StringBuilder markdown = new StringBuilder();
		markdown.append("# Chat Conversation\n\n");

		if (StringUtils.isNotBlank(conversation.getCaption())) {
			markdown.append("**Title:** ").append(conversation.getCaption()).append("\n\n");
		}

		markdown.append("**Exported:** ").append(exportedAt == null ? new Date() : exportedAt).append("\n\n");
		markdown.append("---\n\n");

		List<ChatMessage> messages = conversation.getMessages();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage message = messages.get(i);
			markdown.append("## ").append(formatRoleHeader(message.getRole())).append("\n\n");

			if (StringUtils.isNotBlank(message.getContent())) {
				markdown.append(message.getContent()).append("\n\n");
			}

			appendContextSection(markdown, "### Context", message.getContext(), false);
			appendImageSection(markdown, message.getImageAttachments());

			String toolCallDetails = message.getToolCallDetailsAsMarkdown();
			if (StringUtils.isNotBlank(toolCallDetails)) {
				markdown.append(toolCallDetails).append("\n");
			}

			if (i < messages.size() - 1) {
				markdown.append("---\n\n");
			}
		}

		return markdown.toString();
	}

	private static void appendContextSection(StringBuilder markdown, String heading, List<MessageContext> contexts,
			boolean compact) {
		if (contexts == null || contexts.isEmpty()) {
			return;
		}

		appendSectionBreak(markdown);
		markdown.append(heading).append("\n");
		if (!heading.endsWith(":")) {
			markdown.append("\n");
		}

		if (compact) {
			markdown.append(contexts.stream().map(c -> c.compile(true)).collect(Collectors.joining("\n")));
			return;
		}

		for (MessageContext ctx : contexts) {
			markdown.append("```\n");
			markdown.append(ctx.compile(true));
			markdown.append("\n```\n\n");
		}
	}

	private static void appendImageSection(StringBuilder markdown, List<ImageAttachment> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return;
		}

		appendSectionBreak(markdown);
		for (ImageAttachment attachment : attachments) {
			markdown.append("![").append(escapeMarkdownAlt(attachment.getDisplayName())).append("](")
					.append(attachment.getDataUrl()).append(")\n\n");
		}
	}

	private static void appendSectionBreak(StringBuilder markdown) {
		if (markdown.length() == 0) {
			return;
		}
		if (!markdown.toString().endsWith("\n\n")) {
			markdown.append("\n\n");
		}
	}

	private static String escapeMarkdownAlt(String value) {
		return StringUtils.defaultString(value).replace("[", "\\[").replace("]", "\\]");
	}

	private static String formatRoleHeader(Role role) {
		if (role == null) {
			return "Message";
		}
		switch (role) {
		case USER:
			return "User";
		case ASSISTANT:
			return "Assistant";
		case SYSTEM:
			return "System";
		case TOOL_SUMMARY:
			return "Tool Summary";
		default:
			return role.name();
		}
	}
}
