package com.chabicht.code_intelligence.chat.tools;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IDocumentPartitioningListener;
import org.eclipse.jface.text.IPositionUpdater;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;

public class TestDocument implements IDocument {

	private final String fileName;
	private String content;

	public TestDocument(String fileName, String content) {
		this.fileName = fileName;
		this.content = content;
	}

	@Override
	public char getChar(int offset) throws BadLocationException {
		return content.charAt(offset);
	}

	@Override
	public int getLength() {
		return content.length();
	}

	@Override
	public String get() {
		return content;
	}

	@Override
	public String get(int offset, int length) throws BadLocationException {
		return content.substring(offset, offset + length);
	}

	@Override
	public void set(String text) {
		content = text;
	}

	@Override
	public void replace(int offset, int length, String text) throws BadLocationException {
		content = content.substring(0, offset) + text + content.substring(offset + length);
	}

	@Override
	public void addDocumentListener(IDocumentListener listener) {

	}

	@Override
	public void removeDocumentListener(IDocumentListener listener) {

	}

	@Override
	public void addPrenotifiedDocumentListener(IDocumentListener documentAdapter) {

	}

	@Override
	public void removePrenotifiedDocumentListener(IDocumentListener documentAdapter) {

	}

	@Override
	public void addPositionCategory(String category) {

	}

	@Override
	public void removePositionCategory(String category) throws BadPositionCategoryException {

	}

	@Override
	public String[] getPositionCategories() {
		return null;
	}

	@Override
	public boolean containsPositionCategory(String category) {
		return false;
	}

	@Override
	public void addPosition(Position position) throws BadLocationException {

	}

	@Override
	public void removePosition(Position position) {

	}

	@Override
	public void addPosition(String category, Position position)
			throws BadLocationException, BadPositionCategoryException {

	}

	@Override
	public void removePosition(String category, Position position) throws BadPositionCategoryException {

	}

	@Override
	public Position[] getPositions(String category) throws BadPositionCategoryException {
		return null;
	}

	@Override
	public boolean containsPosition(String category, int offset, int length) {
		return false;
	}

	@Override
	public int computeIndexInCategory(String category, int offset)
			throws BadLocationException, BadPositionCategoryException {
		return 0;
	}

	@Override
	public void addPositionUpdater(IPositionUpdater updater) {

	}

	@Override
	public void removePositionUpdater(IPositionUpdater updater) {

	}

	@Override
	public void insertPositionUpdater(IPositionUpdater updater, int index) {

	}

	@Override
	public IPositionUpdater[] getPositionUpdaters() {
		return null;
	}

	@Override
	public String[] getLegalContentTypes() {
		return null;
	}

	@Override
	public String getContentType(int offset) throws BadLocationException {
		return null;
	}

	@Override
	public ITypedRegion getPartition(int offset) throws BadLocationException {
		return null;
	}

	@Override
	public ITypedRegion[] computePartitioning(int offset, int length) throws BadLocationException {
		return null;
	}

	@Override
	public void addDocumentPartitioningListener(IDocumentPartitioningListener listener) {

	}

	@Override
	public void removeDocumentPartitioningListener(IDocumentPartitioningListener listener) {

	}

	@Override
	public void setDocumentPartitioner(IDocumentPartitioner partitioner) {

	}

	@Override
	public IDocumentPartitioner getDocumentPartitioner() {
		return null;
	}

	@Override
	public int getLineLength(int line) throws BadLocationException {
		String[] lines = content.split("\\r?\\n");
		if (line < 0 || line >= lines.length) {
			throw new BadLocationException("Invalid line number: " + line);
		}
		return lines[line].length();
	}

	@Override
	public int getLineOfOffset(int offset) throws BadLocationException {
		String[] lines = content.split("\\r?\\n");
		int currentOffset = 0;
		for (int i = 0; i < lines.length; i++) {
			if (offset >= currentOffset && offset < currentOffset + lines[i].length()
					+ (i < lines.length - 1 ? lines[i].endsWith("\r") ? 2 : 1 : 0)) {
				return i;
			}
			currentOffset += lines[i].length() + (i < lines.length - 1 ? lines[i].endsWith("\r") ? 2 : 1 : 0);
		}
		throw new BadLocationException("Invalid offset: " + offset);
	}

	@Override
	public int getLineOffset(int line) throws BadLocationException {
		String[] lines = content.split("\\r?\\n");
		if (line < 0 || line >= lines.length) {
			throw new BadLocationException("Invalid line number: " + line);
		}
		int offset = 0;
		for (int i = 0; i < line; i++) {
			offset += lines[i].length() + (i < lines.length - 1 ? lines[i].endsWith("\r") ? 2 : 1 : 0);
		}
		return offset;
	}

	@Override
	public IRegion getLineInformation(int line) throws BadLocationException {
		if (line < 0) {
			throw new BadLocationException("Invalid line number: " + line);
		}

		int currentOffset = 0;
		int currentLine = 0;
		int contentLen = content.length();

		for (int i = 0; i < contentLen; i++) {
			if (currentLine == line) {
				int lineStartOffset = currentOffset;
				int lineEndOffset = contentLen; // Assume end of content initially

				// Find the end of the current line (before the next delimiter)
				for (int j = i; j < contentLen; j++) {
					char c = content.charAt(j);
					if (c == '\r') {
						if (j + 1 < contentLen && content.charAt(j + 1) == '\n') {
							lineEndOffset = j; // Found \r\n, end is before \r
							break;
						} else {
							lineEndOffset = j; // Found \r, end is before \r
							break;
						}
					} else if (c == '\n') {
						lineEndOffset = j; // Found \n, end is before \n
						break;
					}
				}
				return new Region(lineStartOffset, lineEndOffset - lineStartOffset);
			}

			char c = content.charAt(i);
			if (c == '\r') {
				if (i + 1 < contentLen && content.charAt(i + 1) == '\n') {
					i++; // Consume the next character as part of \r\n
				}
				currentOffset = i + 1; // Offset is after the delimiter
				currentLine++;
			} else if (c == '\n') {
				currentOffset = i + 1; // Offset is after the delimiter
				currentLine++;
			}
		}

		// Check if the last line was requested and exists
		if (currentLine == line && currentOffset <= contentLen) {
			 return new Region(currentOffset, contentLen - currentOffset);
		}

		throw new BadLocationException("Invalid line number: " + line);
	}

	@Override
	public IRegion getLineInformationOfOffset(int offset) throws BadLocationException {
		return null;
	}

	@Override
	public int getNumberOfLines() {
		return content.split("\n").length;
	}

	@Override
	public int getNumberOfLines(int offset, int length) throws BadLocationException {
		String subString = content.substring(offset, offset + length);
		return subString.split("\n").length;
	}

	@Override
	public int computeNumberOfLines(String text) {
		return 0;
	}

	@Override
	public String[] getLegalLineDelimiters() {
		return null;
	}

	@Override
	public String getLineDelimiter(int line) throws BadLocationException {
		return "\n";
	}

	@Override
	public int search(int startOffset, String findString, boolean forwardSearch, boolean caseSensitive,
			boolean wholeWord) throws BadLocationException {
		return 0;
	}

}
