package com.chabicht.code_intelligence.changelistener;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;

/**
 * This aggregator collects multiple single-character or short changes into one
 * "edit chunk."
 *
 * Each chunk is represented by a separate EditChunk instance containing: -
 * startOffset, endOffset - startLine, endLine - a StringBuilder for combined
 * edits
 *
 * We assume an edit is part of the same chunk if it occurs within a margin of
 * lines around the existing chunk range. Otherwise, we finalize the old chunk
 * and start a new one.
 */
public class DocumentEditAggregator {

	private final IDocument document;

	/**
	 * Represents one contiguous chunk of edits.
	 */
	private static class EditChunk {
		int startOffset;
		int endOffset;
		int startLine;
		int endLine;
		StringBuilder text;

		EditChunk(int startOffset, int endOffset, int startLine, int endLine, String initialText) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.startLine = startLine;
			this.endLine = endLine;
			this.text = new StringBuilder(initialText);
		}
	}

	/**
	 * The chunk currently being aggregated (if any).
	 */
	private EditChunk currentChunk;

	// For detecting user "pauses"
	private long lastChangeTimestamp;

	// How many lines above/below the current chunk do we allow new edits
	// before declaring that a new chunk has begun?
	private static final int LINE_MARGIN = 1;

	private final LastEditsDocumentListener listener;

	public DocumentEditAggregator(IDocument doc, LastEditsDocumentListener listener) {
		this.document = doc;
		this.listener = listener;
	}

	/**
	 * Called whenever a new DocumentEvent arrives for this document. The aggregator
	 * decides if the event is contiguous with the current chunk or if it should
	 * finalize the chunk and start a new one.
	 */
	public void addChange(DocumentEvent event) {
		// If there is no current chunk, create a new one.
		if (currentChunk == null || isEmpty()) {
			startNewChunk(event);
		} else {
			// Check if it belongs to the existing chunk.
			if (isContiguous(event)) {
				mergeChange(event);
			} else {
				finalizeCurrentChunk();
				startNewChunk(event);
			}
		}
		// Update timestamp for debounce logic
		lastChangeTimestamp = System.currentTimeMillis();
	}

	private void startNewChunk(DocumentEvent event) {
		int offset = event.fOffset;
		int length = event.fLength;
		String newText = event.getText();
		if (newText == null) {
			newText = "";
		}
		int startOffset = offset;
		int endOffset = offset + length;
		try {
			int startLine = document.getLineOfOffset(offset);
			int endLine = document.getLineOfOffset(offset + length);
			currentChunk = new EditChunk(startOffset, endOffset, startLine, endLine, newText);
		} catch (BadLocationException e) {
			// fallback if something goes wrong
			currentChunk = new EditChunk(startOffset, endOffset, 0, 0, newText);
		}
	}

	/**
	 * Determine if an incoming event is contiguous with the current chunk. We check
	 * two main things: 1) If the lines of the new edit are within (currentChunk
	 * startLine - margin) and (currentChunk endLine + margin). 2) (Optional) You
	 * might also check offset adjacency. Here, we'll rely mainly on lines.
	 */
	private boolean isContiguous(DocumentEvent event) {
		try {
			int eventStartLine = document.getLineOfOffset(event.fOffset);
			int eventEndLine = document.getLineOfOffset(event.fOffset + event.fLength);

			// If the event is within or near the line range of the current chunk, treat as
			// contiguous.
			boolean withinLineRange = (eventEndLine >= currentChunk.startLine - LINE_MARGIN)
					&& (eventStartLine <= currentChunk.endLine + LINE_MARGIN);
			return withinLineRange;
		} catch (BadLocationException e) {
			return false;
		}
	}

	/**
	 * Merge the new event into the current chunk, potentially updating
	 * offsets/lines.
	 */
	private void mergeChange(DocumentEvent event) {
		int offset = event.fOffset;
		int length = event.fLength;
		String newText = event.getText();
		if (newText == null) {
			newText = "";
		}

		// Update chunk offsets
		int newStartOffset = Math.min(currentChunk.startOffset, offset);
		int newEndOffset = Math.max(currentChunk.endOffset, offset + length);
		currentChunk.startOffset = newStartOffset;
		currentChunk.endOffset = newEndOffset;

		// Update chunk lines
		try {
			int eventStartLine = document.getLineOfOffset(offset);
			int eventEndLine = document.getLineOfOffset(offset + length);
			currentChunk.startLine = Math.min(currentChunk.startLine, eventStartLine);
			currentChunk.endLine = Math.max(currentChunk.endLine, eventEndLine);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		// Append new text to chunk's buffer
		currentChunk.text.append(newText);
	}

	/**
	 * Finalize the current chunk (if any), e.g., log or store it. Then reset
	 * currentChunk to null.
	 */
	public void finalizeCurrentChunk() {
		if (currentChunk != null && currentChunk.text.length() > 0) {
			// System.out.println("Finalizing chunk: [" + currentChunk.startOffset + "," +
			// currentChunk.endOffset + "]\n"
			// + "Lines [" + currentChunk.startLine + "," + currentChunk.endLine + "]\n" +
			// currentChunk.text);
			try {
				if (StringUtils.isNotBlank(currentChunk.text.toString())) {
					int start = document.getLineOffset(currentChunk.startLine);
					int end = document.getLineOffset(currentChunk.endLine + 1) - 1;
					String currentChunkText = document.get(start, end - start);
					int lineCount = countLines(currentChunkText);
					if (lineCount < 50) {
						listener.getLastEdits().add(currentChunkText);
					}
				}
			} catch (BadLocationException e) {
				System.err.println(e);
				// Ignore
			}
		}
		currentChunk = null;
	}

	private int countLines(String currentChunkText) {
		return currentChunkText.split("\r\n|\r|\n").length;
	}

	/**
	 * Return true if no chunk is in progress or if the chunk is empty.
	 */
	public boolean isEmpty() {
		return (currentChunk == null || currentChunk.text.length() == 0);
	}

	public IDocument getDocument() {
		return document;
	}

	public long getLastChangeTimestamp() {
		return lastChangeTimestamp;
	}
}
