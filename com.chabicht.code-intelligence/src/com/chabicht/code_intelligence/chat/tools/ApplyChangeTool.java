package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.chabicht.code_intelligence.chat.tools.TokenizedSearcher.Language;
import com.chabicht.code_intelligence.util.Log;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

public class ApplyChangeTool {
	// Inner class to hold change information
	static class ChangeOperation {
		private final String fileName;
		private final String originalText; // Keep for potential future validation
		private final String replacement;
		private int startLine;
		private int endLine;

		public ChangeOperation(String fileName, int startLine, int endLine, String originalText, String replacement) {
			this.fileName = fileName;
			this.startLine = startLine;
			this.endLine = endLine;
			this.originalText = originalText;
			this.replacement = replacement;
		}

		public String getFileName() {
			return fileName;
		}

		public int getStartLine() {
			return startLine;
		}

		public int getEndLine() {
			return endLine;
		}

		public String getOriginalText() {
			return originalText;
		}

		public String getReplacement() {
			return replacement;
		}
	}

	public static class ApplyChangeResult {
		private final boolean success;
		private final String message;
		private final ChangeOperation operation; // null if failed
		private String diffPreview;

		private ApplyChangeResult(boolean success, String message, ChangeOperation operation, String diffPreview) {
			this.success = success;
			this.message = message;
			this.operation = operation;
			this.diffPreview = diffPreview;
		}

		public static ApplyChangeResult success(String message, ChangeOperation operation, String diffPreview) {
			return new ApplyChangeResult(true, message, operation, diffPreview);
		}

		public static ApplyChangeResult failure(String message) {
			return new ApplyChangeResult(false, message, null, null);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public ChangeOperation getOperation() {
			return operation;
		}

		public String getDiffPreview() {
			return diffPreview;
		}
	}

	// List to store pending changes
	private IResourceAccess resourceAccess;

	public ApplyChangeTool(IResourceAccess resourceAccess) {
		this.resourceAccess = resourceAccess;
	}

	/**
	 * Add a change to the queue of pending changes, but only after validating it.
	 *
	 * @param fileName     The name of the file to change.
	 * @param location     The location string (e.g., "l10:15").
	 * @param originalText The original text expected at the location (for
	 *                     validation).
	 * @param replacement  The text to replace the original content with.
	 * @return An ApplyChangeResult indicating success or failure with details.
	 */
	public ToolChangePreparationResult prepareChange(String fileName, String location, String originalText,
			String replacement) {
		// Basic validation
		if (fileName == null || fileName.trim().isEmpty()) {
			return ToolChangePreparationResult.failure("File name cannot be null or empty");
		}
		if (location == null || location.trim().isEmpty()) {
			return ToolChangePreparationResult.failure("Location cannot be null or empty");
		}
		if (originalText == null) {
			originalText = ""; // Treat null as empty for insertions
		}
		if (replacement == null) {
			replacement = ""; // Allow empty replacements (deletions)
		}

		IFile file = resourceAccess.findFileByNameBestEffort(fileName);
		if (file == null) {
			return ToolChangePreparationResult.failure("File not found: " + fileName);
		}

		String contentType = null;
		try {
			IContentDescription contentDescription = file.getContentDescription();
			if (contentDescription != null) {
				contentType = Optional.ofNullable(contentDescription.getContentType()).map(IContentType::getId)
						.orElse(null);
			}
		} catch (CoreException e) {
			contentType = null;
		}

		Map<IFile, IDocument> tempDocMap = new HashMap<>();
		try {
			IDocument document = resourceAccess.getDocumentAndConnect(file, tempDocMap);
			if (document == null) {
				return ToolChangePreparationResult.failure("Could not get document for file: " + fileName);
			}

			// If originalText is empty, this is an insertion. Otherwise, it's a
			// replace/delete.
			final boolean isInsertion = originalText.isEmpty();

			// Parse location string to get search bounds
			int[] searchBounds = parseLocationToOffsets(location, document);
			if (searchBounds == null) {
				return ToolChangePreparationResult.failure("Invalid location string: " + location);
			}

			int[] matchOffsets;
			if (isInsertion) {
				// For an insertion, the location specifies the exact point.
				// We use the start of the provided location range as the insertion offset.
				// The location could be "o150:150" for a specific character offset,
				// or "l10:10" for the start of a line.
				matchOffsets = new int[] { searchBounds[0], searchBounds[0] };
			} else {
				// For replace/delete, find the actual start and end offsets of the matched text
				matchOffsets = findOffsetsByPattern(originalText, document, searchBounds, contentType);
			}

			int startLine1Based = document.getLineOfOffset(matchOffsets[0]) + 1;
			int endLine1Based = document.getLineOfOffset(matchOffsets[1]) + 1;

			// Create the TextEdit
			TextEdit edit = createActualTextEdit(document, startLine1Based, endLine1Based, replacement, isInsertion,
					matchOffsets[0], location);

			Document previewDoc = new Document(document.get());
			TextEdit previewEdit = edit.copy();
			previewEdit.apply(previewDoc, TextEdit.NONE);

			String diff = generateDiffPreview(document.get(), previewDoc.get(), file, 1);

			Log.logInfo("Prepared TextEdit for file: " + fileName);

			return ToolChangePreparationResult.success("Change validated and prepared for preview.", file,
					java.util.Collections.singletonList(edit), // Return as a list
					diff);
		} catch (IllegalArgumentException | BadLocationException e) {
			return ToolChangePreparationResult.failure("Failed to locate or prepare change: " + e.getMessage());
		} catch (Exception e) {
			Log.logError("Unexpected error preparing change for " + fileName, e);
			return ToolChangePreparationResult.failure("Unexpected error preparing change: " + e.getMessage());
		} finally {
			resourceAccess.disconnectAllDocuments(tempDocMap);
		}
	}

	/**
	 * Creates a TextEdit (either Replace, Delete, or Insert) based on the provided
	 * parameters.
	 *
	 * @param document        The document to apply the edit to.
	 * @param startLine1Based The 1-based starting line of the region to change.
	 * @param endLine1Based   The 1-based ending line of the region to change.
	 * @param replacementText The text to replace the original content with.
	 * @param isInsertion     True if the operation is an insertion.
	 * @param insertionOffset The character offset for the insertion.
	 * @param location        The original location string (e.g., "l10:12").
	 * @return The created TextEdit.
	 * @throws BadLocationException If location calculations fail.
	 */
	private TextEdit createActualTextEdit(IDocument document, int startLine1Based, int endLine1Based,
			String replacementText, boolean isInsertion, int insertionOffset, String location)
			throws BadLocationException {

		if (isInsertion) {
			// For an insertion, we create an InsertEdit.
			// We must decide whether to add a newline. We add it for line-based
			// insertions to ensure the new content is on its own line(s).
			// For offset-based insertions, we don't, to allow for inline insertions.
			String textToInsert = replacementText;
			if (location.trim().toLowerCase().startsWith("l")) {
				String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
				// Also check if the document is empty, in which case no delimiter is needed.
				if (document.getLength() > 0 && !textToInsert.endsWith(lineDelimiter)) {
					textToInsert += lineDelimiter;
				}
			}
			return new InsertEdit(insertionOffset, textToInsert);
		}

		// Logic for Replace and Delete operations
		int startLine0Based = startLine1Based - 1;
		int endLine0Based = endLine1Based - 1;

		if (startLine0Based < 0 || endLine0Based < startLine0Based || endLine0Based >= document.getNumberOfLines()) {
			throw new BadLocationException("Invalid line range for operation: " + startLine1Based + "-" + endLine1Based
					+ ", doc lines: " + document.getNumberOfLines());
		}

		IRegion startLineInfo = document.getLineInformation(startLine0Based);
		int offset = startLineInfo.getOffset();
		int length;

		if (replacementText.isEmpty()) {
			// This is a deletion operation.
			// Calculate length to remove lines including their delimiters.
			if (endLine0Based < document.getNumberOfLines() - 1) {
				// Deleting lines not at the very end of the document.
				// Length extends to the start of the next line.
				IRegion nextLineInfo = document.getLineInformation(endLine0Based + 1);
				length = nextLineInfo.getOffset() - offset;
			} else {
				// Deleting lines at the end of the document.
				// Length extends to the end of the last line being deleted.
				IRegion endLineInfo = document.getLineInformation(endLine0Based);
				length = (endLineInfo.getOffset() + endLineInfo.getLength()) - offset;
			}
			return new DeleteEdit(offset, length);
		} else {
			// This is a replacement operation.
			// Length covers from the start of the first line to the end of the last line.
			IRegion endLineInfo = document.getLineInformation(endLine0Based);
			length = (endLineInfo.getOffset() + endLineInfo.getLength()) - offset;
			return new ReplaceEdit(offset, length, replacementText);
		}
	}

	/**
	 * Searches for a pattern derived from the original text within specified bounds
	 * of the document. The pattern ignores leading/trailing whitespace on each line
	 * and allows for arbitrary whitespace between lines.
	 *
	 * @param originalText The text to derive the search pattern from.
	 * @param document     The document to search within.
	 * @param searchBounds An array [startOffset, endOffset] defining the region to
	 *                     search.
	 * @param contentType  The content type of the file.
	 * @return An array [matchStartOffset, matchEndOffset] of the found pattern
	 *         match relative to the beginning of the document.
	 * @throws IllegalArgumentException If the pattern cannot be found within the
	 *                                  specified bounds or if originalText is empty
	 *                                  after processing.
	 * @throws BadLocationException     If searchBounds are invalid for the
	 *                                  document.
	 */
	private int[] findOffsetsByPattern(String originalText, IDocument document, int[] searchBounds, String contentType)
			throws IllegalArgumentException, BadLocationException {

		if (originalText == null || originalText.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Original text contains only whitespace, cannot create a search pattern.");
		}

		// Validate search bounds
		int searchStart = searchBounds[0];
		int searchLength = searchBounds[1] - searchBounds[0];
		if (searchStart < 0 || searchLength < 0 || searchStart + searchLength > document.getLength()) {
			throw new BadLocationException("Invalid search bounds [" + searchStart + ", " + (searchStart + searchLength)
					+ "] for document length " + document.getLength());
		}

		// Shortcut for identical match.
		String searchRegionText = document.get(searchStart, searchLength);
		if (StringUtils.stripToEmpty(originalText).equals(StringUtils.stripToEmpty(searchRegionText))) {
			return searchBounds;
		}

		int[] matchingRegion = null;
		// String searchRegionText = null; // Will be reassigned in the loop
		int maxRetries = 4; // To prevent an infinite loop

		// Calculate initial line count once
		int searchStartLine = document.getLineOfOffset(searchStart);
		int searchEndLine = document.getLineOfOffset(Math.min(searchStart + searchLength, document.getLength() - 1));
		int searchLineCount = searchEndLine - searchStartLine + 1;

		// Strategy 2: Dynamic Sizing based on the size of the change
		int linesToAdd;
		if (searchLineCount <= 3) {
			// Small change, needs more context
			linesToAdd = 10;
		} else if (searchLineCount > 20) {
			// Large change, likely unique, needs less context
			linesToAdd = 5;
		} else {
			// Medium change, the original heuristic is good
			linesToAdd = Math.max(5, (int) Math.ceil(0.5 * searchLineCount));
		}

		TokenizedSearcher.Config config = new TokenizedSearcher.Config();
		if (contentType != null) {
			Language lang = Language.of(contentType);
			config.setLanguage(lang);
		}
		TokenizedSearcher searcher = new TokenizedSearcher(config);

		// Strategy 1: Iterative Deepening to find the match
		int[] extendedRegion = null;
		for (int i = 0; i < maxRetries; i++) {
			extendedRegion = extendRegion(document, searchStart, searchLength, linesToAdd);
			searchRegionText = document.get(extendedRegion[0], extendedRegion[1] - extendedRegion[0]);

			matchingRegion = searcher.findMatchingRegion(originalText, searchRegionText);

			if (matchingRegion != null) { // Assuming null means "not found"
				// Found a match, break the loop
				break;
			}

			// If no match, double the context for the next try
			linesToAdd *= 2;

			// Safety break: if we're about to extend past the whole document, stop.
			if (linesToAdd > document.getNumberOfLines()) {
				break;
			}
		}

		// If after all retries, matchingRegion is still null, handle the failure.
		if (matchingRegion == null) {
			throw new IllegalArgumentException(
					"Could not find a unique region to apply the change after multiple attempts. Re-reading the relevant part of the file could help.");
		}

		// Adjust offsets relative to the document start
		return new int[] { extendedRegion[0] + matchingRegion[0], extendedRegion[0] + matchingRegion[1] };
	}

	private int[] extendRegion(IDocument document, int searchStart, int searchLength, int linesToAdd) throws BadLocationException {
		// Get the line numbers for the search region
		int searchStartLine = document.getLineOfOffset(searchStart);
		int searchEndLine = document.getLineOfOffset(Math.min(searchStart + searchLength, document.getLength() - 1));

		// Calculate the extended start and end lines
		int extendedStartLine = Math.max(0, searchStartLine - linesToAdd);
		int extendedEndLine = Math.min(document.getNumberOfLines() - 1, searchEndLine + linesToAdd);

		// Get the corresponding offsets
		IRegion startLineInfo = document.getLineInformation(extendedStartLine);
		IRegion endLineInfo = document.getLineInformation(extendedEndLine);

		int extendedStart = startLineInfo.getOffset();
		int extendedEnd = endLineInfo.getOffset() + endLineInfo.getLength();

		return new int[] { extendedStart, extendedEnd };
	}

	/**
	 * Parses a location string (line or character based) into start and end
	 * character offsets. Line numbers are 1-based, character offsets are 0-based.
	 *
	 * @param location The location string (e.g., "l10:12", "o150:200").
	 * @param document The document to resolve line numbers against.
	 * @return An array of two integers [startOffset, endOffset], or null if parsing
	 *         fails or location is invalid.
	 */
	private int[] parseLocationToOffsets(String location, IDocument document) {
		if (location == null || location.isEmpty()) {
			return null;
		}

		try {
			char type = location.charAt(0);
			String range = location.substring(1);
			String[] parts = range.split(":");
			if (parts.length != 2) {
				Log.logWarn("Invalid location format: " + location);
				return null;
			}

			int start, end;

			if (type == 'l' || type == 'L') {
				// Line-based location (1-based)
				int startLine = Integer.parseInt(parts[0]);
				parts[1] = StringUtils.stripToEmpty(parts[1]).replaceAll("[^0-9]", "");
				int endLine = Integer.parseInt(parts[1]);
				int numDocLines = document.getNumberOfLines(); // Get total lines once
				//
				// if (startLine < 1 || endLine < startLine || startLine > numDocLines) {
				// Log.logWarn("Invalid line numbers in location: " + location + " (Doc lines: "
				// + numDocLines + ")");
				// return null;
				// }
				//
				// final int CONTEXT_LINES = 3; // Number of context lines to add before and
				// after
				//
				// Convert 1-based line numbers to 0-based indices for the *original* selection
				int originalStartLineIndex = startLine - 1;
				// Ensure original endLineIndex doesn't exceed document bounds
				int originalEndLineIndex = Math.min(endLine - 1, numDocLines - 1);
				//
				// // Calculate the start line index including context, clamped to document
				// start
				// // (0)
				// int contextStartLineIndex = Math.max(0, originalStartLineIndex -
				// CONTEXT_LINES);
				//
				// // Calculate the end line index including context, clamped to document end
				// int contextEndLineIndex = Math.min(numDocLines - 1, originalEndLineIndex +
				// CONTEXT_LINES);
				//
				// try {
				// // Get start offset from the beginning of the context start line
				// start = document.getLineOffset(contextStartLineIndex);
				//
				// // Get end offset from the end of the context end line
				// IRegion contextEndLineInfo =
				// document.getLineInformation(contextEndLineIndex);
				// end = contextEndLineInfo.getOffset() + contextEndLineInfo.getLength();
				// } catch (BadLocationException e) {
				// // This should theoretically not happen due to boundary checks, but handle
				// // defensively
				// Log.logError("Error calculating offsets with context for location: " +
				// location, e);
				// return null; // Or handle error appropriately
				// }

				int safeStartLineIndex = Math.max(0, originalStartLineIndex);
				int safeEndLineIndex = Math.min(numDocLines - 1, originalEndLineIndex);

				try {
					IRegion contextEndLineInfo = document.getLineInformation(safeEndLineIndex);
					end = contextEndLineInfo.getOffset() + contextEndLineInfo.getLength();
					return new int[] { document.getLineOffset(safeStartLineIndex), end };
				} catch (BadLocationException e) {
					Log.logError("Error calculating offsets with context for location: " + location, e);
					return null;
				}
			} else if (type == 'o' || type == 'O') {
				// Character-based location (0-based)
				start = Integer.parseInt(parts[0]);
				end = Integer.parseInt(parts[1]);

				if (start < 0 || end < start || start > document.getLength()) {
					Log.logWarn("Invalid character offsets in location: " + location + " (Doc length: "
							+ document.getLength() + ")");
					return null;
				}
				// Ensure end offset doesn't exceed document bounds
				end = Math.min(end, document.getLength());

			} else {
				Log.logWarn("Unknown location type prefix: " + location);
				return null;
			}

			return new int[] { start, end };

		} catch (NumberFormatException | IndexOutOfBoundsException e) {
			Log.logWarn("Failed to parse location string '" + location + "': " + e.getMessage());
			return null;
		}
	}

	/**
	 * Creates a human-readable diff preview between original text and replacement
	 * text, using actual file path and line numbers.
	 * 
	 * @param originalText    The text being replaced
	 * @param replacementText The new text being inserted
	 * @param file            The file being modified
	 * @param startLineNumber The starting line number for context
	 * @return A markdown-formatted string showing the differences
	 */
	public String generateDiffPreview(String originalText, String replacementText, IFile file, int startLineNumber) {
		// Handle null inputs safely
		if (originalText == null)
			originalText = "";
		if (replacementText == null)
			replacementText = "";

		// Get the file path for the diff header
		String filePath = file != null ? file.getFullPath().toString() : "Unknown file";

		// Split both texts into separate lines
		// The split limit -1 ensures trailing empty strings are included
		List<String> originalLines = Arrays.asList(originalText.split("\\R", -1));
		List<String> replacementLines = Arrays.asList(replacementText.split("\\R", -1));

		// Generate unified diff format
		try {
			// Create the patch from the original and replacement lines
			Patch<String> patch = DiffUtils.diff(originalLines, replacementLines);

			// Convert the patch to unified diff format with real file path
			List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(filePath, filePath, originalLines, patch,
					5);

			// Adjust line numbers in the unified diff headers to match actual file
			unifiedDiff = adjustLineNumbers(unifiedDiff, startLineNumber);

			// Build a nicely formatted unified diff output
			StringBuilder diffPreview = new StringBuilder();

			// Add each line of the unified diff to our preview
			for (String line : unifiedDiff) {
				diffPreview.append(line).append("\n");
			}

			return diffPreview.toString();

		} catch (Exception e) {
			// If something goes wrong, return a simple error message
			Log.logError("Error generating diff preview", e);
			return "```\nError generating diff preview: " + e.getMessage() + "\n```";
		}
	}

	/**
	 * Adjusts line numbers in the diff output to match actual file line numbers.
	 */
	private List<String> adjustLineNumbers(List<String> unifiedDiff, int startLineNumber) {
		List<String> result = new ArrayList<>();

		for (String line : unifiedDiff) {
			// For @@ lines that contain line number info, adjust them
			if (line.startsWith("@@") && line.contains("@@")) {
				// Parse line range information (format: @@ -1,7 +1,7 @@)
				Pattern pattern = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@");
				Matcher matcher = pattern.matcher(line);

				if (matcher.find()) {
					int origStart = Integer.parseInt(matcher.group(1));
					int origCount = Integer.parseInt(matcher.group(2));
					int newStart = Integer.parseInt(matcher.group(3));
					int newCount = Integer.parseInt(matcher.group(4));

					// Adjust the line numbers by adding the startLineNumber offset
					int adjustedOrigStart = origStart + startLineNumber - 1;
					int adjustedNewStart = newStart + startLineNumber - 1;

					// Create new line with adjusted line numbers
					String adjustedLine = String.format("@@ -%d,%d +%d,%d @@", adjustedOrigStart, origCount,
							adjustedNewStart, newCount);
					result.add(adjustedLine);
					continue;
				}
			}
			result.add(line);
		}

		return result;
	}

	/**
	 * Class representing a diff preview with line range information.
	 */
	public class DiffPreviewResult {
		private final String diffPreview;
		private final int startLine;
		private final int endLine;
		private final int lineCount;

		public DiffPreviewResult(String diffPreview, int startLine, int endLine) {
			this.diffPreview = diffPreview;
			this.startLine = startLine;
			this.endLine = endLine;
			this.lineCount = endLine - startLine + 1;
		}

		public String getDiffPreview() {
			return diffPreview;
		}

		public int getStartLine() {
			return startLine;
		}

		public int getEndLine() {
			return endLine;
		}

		public int getLineCount() {
			return lineCount;
		}
	}
}
