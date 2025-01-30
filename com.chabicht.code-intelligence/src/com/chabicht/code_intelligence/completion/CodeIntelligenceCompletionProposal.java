package com.chabicht.code_intelligence.completion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class CodeIntelligenceCompletionProposal implements IJavaCompletionProposal, ICompletionProposalExtension,
		ICompletionProposalExtension4, ICompletionProposalExtension5 {
	private static final Pattern TRAILING_NEWLINE_PATTERN = Pattern.compile("[\r\n]*$", Pattern.MULTILINE);

	private final String replacementString;
	private final int replacementOffset;
	private final int replacementLength;
	private final Image image;
	private final String displayString;
	private final int relevance;
	private int cursorOffset;

	/**
	 * Constructor for a code-completion proposal
	 *
	 * @param replacementString the text to insert into the document
	 * @param replacementOffset the offset at which to insert
	 * @param replacementLength the length of the text to replace
	 * @param image             the icon to display (optional, can be null)
	 * @param displayString     the text to display to the user in the completion
	 *                          dropdown
	 * @param relevance         the relative importance of this proposal compared to
	 *                          others
	 */
	public CodeIntelligenceCompletionProposal(String replacementString, int replacementOffset, int replacementLength,
			Image image, String displayString, int relevance) {
		this.replacementString = replacementString;
		this.replacementOffset = replacementOffset;
		this.replacementLength = replacementLength;
		this.image = image;
		this.displayString = displayString;
		this.relevance = relevance;
	}

	@Override
	public void apply(IDocument document) {
		try {
			int cursorOffset = replacementOffset + replacementLength;
			int line = document.getLineOfOffset(cursorOffset);
			int lineStart = document.getLineOffset(line);
			String replStr = replacementString;

			// This is the text in the current line from its start up to the cursor.
			String lineStartText = document.get(lineStart, cursorOffset - lineStart);

			// 1) Find overlap between the end of lineStartText and the start of
			// replacementString
			int overlapLength = findOverlap(lineStartText, replStr);

			// 2) If we found an overlap, strip it off the replacement
			if (overlapLength > 0) {
				replStr = replStr.substring(overlapLength);
			}

			Matcher matcher = TRAILING_NEWLINE_PATTERN.matcher(replStr);
			replStr = matcher.replaceAll("");

			document.replace(cursorOffset, 0, replStr);
			formatCode(document, cursorOffset, replStr.length());
			setCursorOffset(cursorOffset + replStr.length());
		} catch (BadLocationException e) {
			// Handle appropriately (log, rethrow, etc.)
			e.printStackTrace();
		}
	}

	private void formatCode(IDocument document, int offset, int length) {
		CodeFormatter formatter = ToolFactory.createCodeFormatter(null);
		if (formatter != null) {
			TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT, document.get(), offset, length, 0, null);
			if (edit != null) {
				try {
					edit.apply(document);
				} catch (MalformedTreeException | BadLocationException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Finds the largest overlap between the end of 'lineSoFar' and the beginning of
	 * 'replacement'. Returns the number of overlapping characters.
	 *
	 * Example: lineSoFar = "HydraQuicklogState " replacement = "HydraQuicklogState
	 * state = getState();" => overlap = length("HydraQuicklogState ")
	 */
	private int findOverlap(String lineSoFar, String replacement) {
		int maxOverlap = 0;
		int maxPossible = Math.min(lineSoFar.length(), replacement.length());

		// Check from 1 up to maxPossible characters
		for (int i = 1; i <= maxPossible; i++) {
			// Compare the last i chars of lineSoFar to the first i chars of replacement
			if (lineSoFar.regionMatches(lineSoFar.length() - i, replacement, 0, i)) {
				maxOverlap = i;
			}
		}
		return maxOverlap;
	}

	/**
	 * Returns the new cursor position after applying the proposal. Often set to
	 * just after the inserted text.
	 */
	@Override
	public Point getSelection(IDocument document) {
		// Move the caret to the end of the inserted text
		return new Point(getCursorOffset(), 0);
	}

	/**
	 * Returns additional information that may be displayed (e.g. in a hover) when
	 * the user selects this proposal from the list.
	 */
	@Override
	public String getAdditionalProposalInfo() {
		// You can return more useful details if desired
		return "Inserts: " + replacementString;
	}

	/**
	 * The string displayed in the completion popup.
	 */
	@Override
	public String getDisplayString() {
		return displayString;
	}

	/**
	 * The icon to display alongside this proposal (can be null if you don't need an
	 * icon).
	 */
	@Override
	public Image getImage() {
		return image;
	}

	/**
	 * Returns context information (e.g. method parameters), if applicable.
	 * Returning null indicates no special context info is provided.
	 */
	@Override
	public IContextInformation getContextInformation() {
		return null;
	}

	/**
	 * Called when a proposal is applied with a specific trigger character. Often
	 * simply delegates to {@link #apply(IDocument)} if you don't need special
	 * handling of the trigger character.
	 */
	@Override
	public void apply(IDocument document, char trigger, int offset) {
		apply(document);
	}

	/**
	 * Checks if the proposal is still valid for a given offset in the document
	 * (e.g., if the user has typed further characters).
	 *
	 * A common approach is checking whether the user is still editing within or at
	 * the boundary of the intended replacement region, or if the typed text still
	 * matches the expected prefix.
	 */
	@Override
	public boolean isValidFor(IDocument document, int offset) {
		// Simple check: within or at the boundary of the replacement region
		return offset >= replacementOffset && offset <= (replacementOffset + replacementLength);
	}

	/**
	 * Characters that can trigger the application of this proposal. Often `.` for
	 * Java. Return null if no special trigger characters are needed.
	 */
	@Override
	public char[] getTriggerCharacters() {
		// Example: trigger completion on a period.
		// Return null if you don't need any specific trigger chars.
		return new char[] { '.' };
	}

	/**
	 * Returns the offset where the context information (if any) should be
	 * displayed.
	 */
	@Override
	public int getContextInformationPosition() {
		// Commonly the same as the replacement offset
		return replacementOffset;
	}

	/**
	 * Returns how important this proposal is relative to others (higher number ->
	 * higher priority).
	 */
	@Override
	public int getRelevance() {
		return relevance;
	}

	public int getCursorOffset() {
		return cursorOffset;
	}

	public void setCursorOffset(int cursorOffset) {
		this.cursorOffset = cursorOffset;
	}

	@Override
	public boolean isAutoInsertable() {
		return false;
	}

	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		return replacementString;
	}
}
