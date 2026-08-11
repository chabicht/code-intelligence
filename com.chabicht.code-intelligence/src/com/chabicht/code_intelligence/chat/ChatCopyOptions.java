package com.chabicht.code_intelligence.chat;

import org.eclipse.jface.preference.IPreferenceStore;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ToolCallDetail;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

/**
 * What to include when copying a chat conversation to the clipboard.
 *
 * @see ChatMarkdownExporter
 */
public class ChatCopyOptions {

	private final boolean includeReasoning;
	private final ToolCallDetail toolCallDetail;

	public ChatCopyOptions(boolean includeReasoning, ToolCallDetail toolCallDetail) {
		this.includeReasoning = includeReasoning;
		this.toolCallDetail = toolCallDetail == null ? ToolCallDetail.DETAILED : toolCallDetail;
	}

	/** Everything included - the behavior before the options existed. */
	public static ChatCopyOptions defaults() {
		return new ChatCopyOptions(true, ToolCallDetail.DETAILED);
	}

	public static ChatCopyOptions fromPreferences() {
		Activator activator = Activator.getDefault();
		if (activator == null) {
			return defaults();
		}
		IPreferenceStore store = activator.getPreferenceStore();
		return new ChatCopyOptions(store.getBoolean(PreferenceConstants.CHAT_COPY_INCLUDE_REASONING), ToolCallDetail
				.fromString(store.getString(PreferenceConstants.CHAT_COPY_TOOL_CALL_DETAIL), ToolCallDetail.DETAILED));
	}

	public boolean isIncludeReasoning() {
		return includeReasoning;
	}

	public ToolCallDetail getToolCallDetail() {
		return toolCallDetail;
	}
}
