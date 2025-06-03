package com.chabicht.code_intelligence.chat.tools;

import com.chabicht.code_intelligence.util.Log;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.BadLocationException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class ReadFileContentTool {

	private final IResourceAccess resourceAccess;

	public ReadFileContentTool(IResourceAccess resourceAccess) {
		this.resourceAccess = resourceAccess;
	}

	public static class ReadFileContentResult {
		private final boolean success;
		private final String message;
		private final String filePath;
		private final String contentWithLineNumbers;
		private final int actualStartLine; // 1-based
		private final int actualEndLine; // 1-based

		public ReadFileContentResult(boolean success, String message, String filePath, String contentWithLineNumbers,
				int actualStartLine, int actualEndLine) {
			this.success = success;
			this.message = message;
			this.filePath = filePath;
			this.contentWithLineNumbers = contentWithLineNumbers;
			this.actualStartLine = actualStartLine;
			this.actualEndLine = actualEndLine;
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public String getFilePath() {
			return filePath;
		}

		public String getContentWithLineNumbers() {
			return contentWithLineNumbers;
		}

		public int getActualStartLine() {
			return actualStartLine;
		}

		public int getActualEndLine() {
			return actualEndLine;
		}

		public static ReadFileContentResult failure(String message) {
			return new ReadFileContentResult(false, message, null, null, 0, 0);
		}
	}

	private static String prefixLines(String rawContent, int startLine, int endLine) {
		if (rawContent == null) {
			return "";
		}
		String[] lines = rawContent.split("\r?\n", -1);
		List<String> prefixedLines = new ArrayList<>();

		int maxLineNumberForFormatting = endLine;
		int maxLineNumberLength = String.valueOf(maxLineNumberForFormatting).length();
		if (maxLineNumberLength <= 0) {
			maxLineNumberLength = 1;
		}

		for (int i = 0; i < lines.length; i++) {
			int currentLineNumber = startLine + i;
			String prefix = String.format("%" + maxLineNumberLength + "d: ", currentLineNumber);
			prefixedLines.add(prefix + lines[i]);
		}
		return String.join("\n", prefixedLines);
	}

	public ReadFileContentResult readFileContent(String fileName, Integer startLineParam, Integer endLineParam) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return ReadFileContentResult.failure("File name cannot be null or empty.");
		}

		IFile file = resourceAccess.findFileByNameBestEffort(fileName);
		if (file == null || !file.exists()) {
			return ReadFileContentResult.failure("File not found or not accessible: " + fileName);
		}

		Map<IFile, IDocument> documentMap = new HashMap<>();
		IDocument document = null;
		try {
			document = resourceAccess.getDocumentAndConnect(file, documentMap);
			if (document == null) {
				return ReadFileContentResult.failure("Could not get document for file: " + fileName);
			}

			int totalLines = document.getNumberOfLines();
			if (totalLines == 0 && document.getLength() == 0) {
				return new ReadFileContentResult(true, "File is empty.", file.getFullPath().toString(), "", 0, 0);
			}

			int actualReadStartLine;
			int actualReadEndLine;

			if (startLineParam == null && endLineParam == null) { // Read whole file
				actualReadStartLine = 1;
				actualReadEndLine = totalLines;
			} else {
				actualReadStartLine = (startLineParam != null) ? startLineParam : 1;
				actualReadEndLine = (endLineParam != null) ? endLineParam : (startLineParam != null ? startLineParam : totalLines);
				if (startLineParam != null && endLineParam == null) actualReadEndLine = actualReadStartLine; // if only start is given, read that one line
			}

			// Validate and adjust actualReadStartLine and actualReadEndLine
			if (actualReadStartLine < 1) actualReadStartLine = 1;
			if (actualReadEndLine > totalLines) actualReadEndLine = totalLines;

			if (actualReadStartLine > actualReadEndLine && totalLines > 0) { // Invalid range after adjustments
				return ReadFileContentResult.failure(
						String.format("Invalid line range requested. Effective start: %d, effective end: %d. File has %d lines.",
								actualReadStartLine, actualReadEndLine, totalLines)
				);
			}

			String finalContentToPrefix;
			if (totalLines == 0 || actualReadStartLine > actualReadEndLine) {
				finalContentToPrefix = "";
				actualReadStartLine = 0;
				actualReadEndLine = 0;
			} else {
				org.eclipse.jface.text.IRegion startRegion = document.getLineInformation(actualReadStartLine - 1);
				org.eclipse.jface.text.IRegion endRegion = document.getLineInformation(actualReadEndLine - 1);
				int offset = startRegion.getOffset();
				int length = (endRegion.getOffset() + endRegion.getLength()) - offset;
				finalContentToPrefix = document.get(offset, length);
			}

			String contentWithPrefixes = prefixLines(finalContentToPrefix, actualReadStartLine, actualReadEndLine);

			String successMessage;
			if (startLineParam != null || endLineParam != null) { // If any range was specified
				successMessage = String.format("Successfully read lines %d to %d from %s.", actualReadStartLine, actualReadEndLine, file.getName());
				if (totalLines == 0) successMessage = String.format("File %s is empty.", file.getName());
			} else {
				successMessage = String.format("Successfully read the entire file %s (%d lines).", file.getName(), totalLines);
				if (totalLines == 0) successMessage = String.format("File %s is empty.", file.getName());
			}

			return new ReadFileContentResult(true, successMessage, file.getFullPath().toString(), contentWithPrefixes, actualReadStartLine, actualReadEndLine);

		} catch (BadLocationException e) {
			Log.logError("Failed to read file content for " + fileName + ": " + e.getMessage(), e);
			return ReadFileContentResult.failure("Failed to read file content: " + e.getMessage());
		} catch (Exception e) {
			Log.logError("Unexpected error reading file " + fileName + ": " + e.getMessage(), e);
			return ReadFileContentResult.failure("Unexpected error reading file: " + e.getMessage());
		} finally {
			resourceAccess.disconnectAllDocuments(documentMap);
		}
	}
}

