package com.chabicht.code_intelligence.chat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.ToolCallDetail;
import com.chabicht.code_intelligence.util.ReasoningSplitter;
import com.chabicht.code_intelligence.util.ReasoningSplitter.MessageContentWithReasoning;

public final class ChatMarkdownExporter {

	/** Heading level of the per-message headings in an exported conversation. */
	private static final int MESSAGE_HEADING_LEVEL = 2;

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

		appendContextSection(markdown, "## Context", message.getContext(), true);
		appendImageSection(markdown, message.getImageAttachments());
		markdown.append(message.getToolCallDetailsAsMarkdown(MESSAGE_HEADING_LEVEL, ToolCallDetail.DETAILED));
		return markdown.toString();
	}

	public static String exportConversation(ChatConversation conversation, Date exportedAt) {
		return exportConversation(conversation, exportedAt, ChatCopyOptions.defaults());
	}

	public static String exportConversation(ChatConversation conversation, Date exportedAt, ChatCopyOptions options) {
		if (conversation == null || conversation.getMessages().isEmpty()) {
			return "";
		}
		if (options == null) {
			options = ChatCopyOptions.defaults();
		}

		StringBuilder markdown = new StringBuilder();
		markdown.append("# Chat Conversation\n\n");

		if (StringUtils.isNotBlank(conversation.getCaption())) {
			markdown.append("**Title:** ").append(conversation.getCaption()).append("\n\n");
		}

		markdown.append("**Exported:** ").append(exportedAt == null ? new Date() : exportedAt).append("\n\n");
		markdown.append("---\n\n");

		// Render first, then drop the messages that ended up without any payload, so
		// that no stray separators remain.
		List<String> renderedMessages = new ArrayList<>();
		for (ChatMessage message : conversation.getMessages()) {
			String rendered = renderMessage(message, options);
			if (StringUtils.isNotBlank(rendered)) {
				renderedMessages.add(rendered);
			}
		}

		markdown.append(String.join("---\n\n", renderedMessages));

		return markdown.toString();
	}

	/**
	 * Renders a single message including its role heading, or an empty string if
	 * nothing is left of it under the given options.
	 */
	private static String renderMessage(ChatMessage message, ChatCopyOptions options) {
		MessageContentWithReasoning split = ReasoningSplitter.split(message);

		StringBuilder body = new StringBuilder();

		if (options.isIncludeReasoning() && StringUtils.isNotBlank(split.getThoughts())) {
			body.append("> ").append(headingPrefix(MESSAGE_HEADING_LEVEL + 1)).append("💭 Reasoning\n");
			appendBlockquote(body, split.getThoughts());
			body.append("\n");
		}

		if (StringUtils.isNotBlank(split.getMessage())) {
			body.append(split.getMessage().strip()).append("\n\n");
		}

		appendContextSection(body, headingPrefix(MESSAGE_HEADING_LEVEL + 1) + "Context", message.getContext(), false);
		appendImageSection(body, message.getImageAttachments());

		String toolCallDetails = message.getToolCallDetailsAsMarkdown(MESSAGE_HEADING_LEVEL + 1,
				options.getToolCallDetail());
		if (StringUtils.isNotBlank(toolCallDetails)) {
			body.append(toolCallDetails.strip()).append("\n\n");
		}

		if (StringUtils.isBlank(body)) {
			return "";
		}

		return headingPrefix(MESSAGE_HEADING_LEVEL) + formatRoleHeader(message.getRole()) + "\n\n" + body;
	}

	private static void appendBlockquote(StringBuilder markdown, String text) {
		for (String line : text.strip().split("\\r?\\n")) {
			markdown.append("> ").append(line).append("\n");
		}
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

	private static String headingPrefix(int level) {
		return "#".repeat(Math.max(1, level)) + " ";
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
