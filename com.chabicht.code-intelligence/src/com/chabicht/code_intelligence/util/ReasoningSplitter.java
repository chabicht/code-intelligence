package com.chabicht.code_intelligence.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;

/**
 * Splits the reasoning ("thinking") part of a message from its actual content.
 *
 * <p>
 * Reasoning is stored in one of two ways, depending on the provider:
 * <ul>
 * <li>out of band in a dedicated field ({@link ChatMessage#getThinkingContent()}),
 * as done by e.g. the Anthropic API, or</li>
 * <li>inline in the message content, wrapped in {@code <think>...</think>} tags
 * or one of the equivalent markers other models use.</li>
 * </ul>
 * This class normalizes both forms.
 */
public final class ReasoningSplitter {

	private static final Pattern PATTERN_THINK_START = Pattern.compile("<think>|<\\|begin_of_thought\\|>|<thought>");
	private static final Pattern PATTERN_THINK_END = Pattern.compile("<[/]think>|<\\|end_of_thought\\|>|</thought>");
	private static final Pattern PATTERN_TAGS_TO_REMOVE = Pattern
			.compile("<\\|begin_of_solution\\|>|<\\|end_of_solution\\|>");

	private ReasoningSplitter() {
	}

	public static MessageContentWithReasoning split(ChatMessage message) {
		if (message == null) {
			return new MessageContentWithReasoning("", "", false);
		}
		return split(message.getContent(), message.getThinkingContent(), message.isThinkingComplete());
	}

	/**
	 * Splits a message into reasoning and content, preferring out-of-band reasoning
	 * if present.
	 *
	 * @param content          the message content
	 * @param thinkingContent  out-of-band reasoning, may be blank
	 * @param thinkingComplete whether the out-of-band reasoning is complete
	 */
	public static MessageContentWithReasoning split(String content, String thinkingContent, boolean thinkingComplete) {
		if (StringUtils.isNotBlank(thinkingContent)) {
			return new MessageContentWithReasoning(thinkingContent, StringUtils.defaultString(content),
					thinkingComplete);
		}
		return split(content);
	}

	public static MessageContentWithReasoning split(String content) {
		content = StringUtils.stripToEmpty(content);
		Matcher thinkStartMatcher = PATTERN_THINK_START.matcher(content);
		Matcher thinkEndMatcher = PATTERN_THINK_END.matcher(content);

		String thinkContent = "";
		String messageContent = content;
		boolean endOfThinkingReached = false;
		match_found: if (thinkStartMatcher.find()) {

			// If we encounter a start tag in the middle of a conversation, it's probably a
			// model talking about reasoning.
			if ((thinkStartMatcher.start() > 0)) {
				break match_found;
			}

			if (thinkEndMatcher.find()) {
				int endPosition = thinkEndMatcher.start();
				thinkContent = messageContent.substring(thinkStartMatcher.end(), endPosition);
				messageContent = messageContent.substring(thinkEndMatcher.end());
				endOfThinkingReached = true;
			} else {
				thinkContent = messageContent.substring(thinkStartMatcher.end());
				messageContent = "";
			}
		}
		messageContent = PATTERN_TAGS_TO_REMOVE.matcher(messageContent).replaceAll("");

		return new MessageContentWithReasoning(thinkContent, messageContent, endOfThinkingReached);
	}

	public static class MessageContentWithReasoning {
		private final String thoughts;
		private final String message;
		private final boolean endOfReasoningReached;

		public MessageContentWithReasoning(String thoughts, String message, boolean endOfReasoningReached) {
			this.thoughts = thoughts;
			this.message = message;
			this.endOfReasoningReached = endOfReasoningReached;
		}

		public String getThoughts() {
			return thoughts;
		}

		public String getMessage() {
			return message;
		}

		public boolean isEndOfReasoningReached() {
			return endOfReasoningReached;
		}
	}
}
