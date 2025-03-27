package com.chabicht.code_intelligence.completion;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.changelistener.LastEditsDocumentListener;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.DefaultPrompts;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class CodeIntelligenceCompletionProposalComputer implements IJavaCompletionProposalComputer {

	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("^(\\h*)");

	private Image completionIcon;

	public CodeIntelligenceCompletionProposalComputer() {
		ImageDescriptor imageDescriptor = ImageDescriptor.createFromFile(getClass(), "/icons/completion.png");
		completionIcon = imageDescriptor.createImage();
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext invocationContext,
			IProgressMonitor progressMonitor) {
		boolean debugPromptLoggingEnabled = isDebugPromptLoggingEnabled();
		StringBuilder debugPromptSB = new StringBuilder();

		try {
			System.out.println("CodeIntelligenceCompletionProposalComputer.computeCompletionProp(" + invocationContext
					+ "," + progressMonitor + ")");

			IDocument doc = invocationContext.getDocument();
			ITextSelection textSelection = invocationContext.getTextSelection();

			int selectionStartOffset = textSelection.getOffset();
			int selectionEndOffset = textSelection.getOffset() + textSelection.getLength();

			int startLine = textSelection.getStartLine();
			int endLine = textSelection.getEndLine();

			int ctxBeforeStartOffset = doc.getLineOffset(Math.max(0, startLine - 10));
			int selectedLinesStartOffset = doc.getLineOffset(doc.getLineOfOffset(textSelection.getOffset()));
			int selectedLinesEndOffset = doc
					.getLineOffset(doc.getLineOfOffset(textSelection.getOffset() + textSelection.getLength()) + 1);
			int ctxAfterEndOffset = doc.getLineOffset(Math.min(doc.getNumberOfLines() - 1, endLine + 5));

			int cursorOffset = invocationContext.getInvocationOffset();
			int lineOfCursor = doc.getLineOfOffset(cursorOffset);
			int lineOfCursorOffset = doc.getLineOffset(lineOfCursor);
			String currentLine = doc.get(selectedLinesStartOffset, selectedLinesEndOffset - selectedLinesStartOffset);

			String contextStringWithTags = addCursorTags(doc, selectionStartOffset, selectionEndOffset,
					ctxBeforeStartOffset, ctxAfterEndOffset, cursorOffset);

			String prefix = doc.get(ctxBeforeStartOffset, cursorOffset - ctxBeforeStartOffset);
			String suffix = doc.get(selectionEndOffset, ctxAfterEndOffset - selectionEndOffset);
			String selection = getSelection(doc, selectionStartOffset, selectionEndOffset);

			String lastEdits = createLastEdits();

			PromptTemplate promptTemplate = selectPromptToUse();
			CompletionPrompt completionPrompt = new CompletionPrompt(0f, promptTemplate.getPrompt(),
					Map.of("recentEdits", lastEdits, "prefix", prefix, "suffix", suffix, "selection", selection,
							"contextWithTags", contextStringWithTags));

			if (debugPromptLoggingEnabled) {
				debugPromptSB.append("Prompt \"" + StringUtils.trim(currentLine) + "\"");
				debugPromptSB.append("\n===================================================\n");
				debugPromptSB.append(completionPrompt.compile()).append("\n");
				debugPromptSB.append("===================================================\n");
			}

			CompletionResult completionResult = ConnectionFactory.forCompletions().complete(completionPrompt);

			if (debugPromptLoggingEnabled) {
				debugPromptSB.append("Completion:\n").append("===================================================\n");
				debugPromptSB.append(completionResult.getCompletion()).append("\n");
				debugPromptSB.append("===================================================\n");
			}

			String completion = completionResult.getCompletion();

			CodeIntelligenceCompletionProposal res = new CodeIntelligenceCompletionProposal(completion,
					lineOfCursorOffset, cursorOffset - lineOfCursorOffset, completionIcon,
					completionResult.getCaption(), 10000);

			return List.of(res);
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		} finally {
			if (debugPromptLoggingEnabled) {
				Activator.logInfo(debugPromptSB.toString());
			}
		}
	}

	private String getSelection(IDocument doc, int selectionStartOffset, int selectionEndOffset)
			throws BadLocationException {
		boolean selectionEmpty = selectionStartOffset == selectionEndOffset;
		String res = null;
		if (selectionEmpty) {
			res = "";
		} else {
			res = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
		}
		return res;
	}

	private String addCursorTags(IDocument doc, int selectionStartOffset, int selectionEndOffset,
			int ctxBeforeStartOffset, int ctxAfterEndOffset, int cursorOffset) throws BadLocationException {
		boolean selectionEmpty = selectionStartOffset == selectionEndOffset;
		boolean cursorBeforeSelection = cursorOffset <= selectionStartOffset;
		boolean cursorInSelection = cursorOffset > selectionStartOffset && cursorOffset < selectionEndOffset;
		String contextStringWithTags = null;
		if (selectionEmpty) {
			String startToCursor = doc.get(ctxBeforeStartOffset, cursorOffset - ctxBeforeStartOffset);
			String cursorToEnd = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
			contextStringWithTags = startToCursor + "<<<cursor>>>" + cursorToEnd;
		} else if (cursorBeforeSelection) {
			String startToCursor = doc.get(ctxBeforeStartOffset, cursorOffset - ctxBeforeStartOffset);
			String cursorToSelection = doc.get(cursorOffset, selectionStartOffset - cursorOffset);
			String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
			String rest = doc.get(selectionEndOffset, ctxAfterEndOffset - selectionEndOffset);
			contextStringWithTags = startToCursor + "<<<cursor>>>" + cursorToSelection + "<<<selection_start>>>"
					+ selection + "<<<selection_end>>>" + rest;
		} else if (cursorInSelection) {
			String startToSelection = doc.get(ctxBeforeStartOffset, selectionStartOffset - ctxBeforeStartOffset);
			String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
			String selectionToCursor = doc.get(selectionEndOffset, cursorOffset - selectionEndOffset);
			String rest = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
			contextStringWithTags = startToSelection + "<<<selection_start>>>" + selection + "<<<selection_end>>>"
					+ selectionToCursor + "<<<cursor>>>" + rest;
		} else {
			String startToSelection = doc.get(ctxBeforeStartOffset, selectionStartOffset - ctxBeforeStartOffset);
			String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
			String selectionToCursor = doc.get(selectionEndOffset, cursorOffset - selectionEndOffset);
			String rest = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
			contextStringWithTags = startToSelection + "<<<selection_start>>>" + selection + "<<<selection_end>>>"
					+ selectionToCursor + "<<<cursor>>>" + rest;
		}
		return contextStringWithTags;
	}

	private PromptTemplate selectPromptToUse() {
		return Activator.getDefault().loadPromptTemplates().stream()
				.filter(pt -> PromptType.INSTRUCT.equals(pt.getType()) && pt.isEnabled() && pt.isUseByDefault())
				.findFirst().orElseGet(() -> defaultPromptTemplate());
	}

	private PromptTemplate defaultPromptTemplate() {
		PromptTemplate res = new PromptTemplate();
		res.setType(PromptType.INSTRUCT);
		res.setName("<Default>");
		res.setPrompt(DefaultPrompts.INSTRUCT_PROMPT);
		return res;
	}

	private boolean isDebugPromptLoggingEnabled() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS);
	}

	private String createLastEdits() {
		StringBuilder sb = new StringBuilder();
		for (String edit : LastEditsDocumentListener.getInstance().getLastEdits()) {
			int lineCount = countLines(edit);
			if (lineCount < 50) {
				sb.append("```\n").append(edit).append("```\n\n");
			}
		}
		return sb.toString();
	}

	private int countLines(String currentChunkText) {
		return currentChunkText.split("\r\n|\r|\n").length;
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext invocationContext,
			IProgressMonitor progressMonitor) {
		System.out.println("CodeIntelligenceCompletionProposalComputer.computeContextInformation(" + invocationContext
				+ "," + progressMonitor + ")");
		return Collections.emptyList();
	}

	@Override
	public String getErrorMessage() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.getErrorMessage()");
		return null;
	}

	@Override
	public void sessionEnded() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.sessionEnded()");
	}

	@Override
	public void sessionStarted() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.sessionStarted()");
	}

}
