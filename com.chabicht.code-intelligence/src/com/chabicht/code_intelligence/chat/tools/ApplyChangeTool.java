package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.util.GsonUtil;
import com.chabicht.code_intelligence.util.Log;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.google.gson.Gson;

public class ApplyChangeTool {
	private static final Pattern INDENT_SEARCH_PATTERN = Pattern.compile("^\s*");

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
	private Map<String, TextFileChange> pendingFileChanges = new HashMap<>();
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
	public ApplyChangeResult addChange(String fileName, String location, String originalText, String replacement) {
		// Basic validation
		if (fileName == null || fileName.trim().isEmpty()) {
			return ApplyChangeResult.failure("File name cannot be null or empty");
		}
		if (location == null || location.trim().isEmpty()) {
			return ApplyChangeResult.failure("Location cannot be null or empty");
		}
		if (originalText == null || originalText.trim().isEmpty()) {
			return ApplyChangeResult.failure("Original text cannot be null or empty");
		}
		// Allow empty replacements (deletions)
		if (replacement == null) {
			replacement = "";
		}

		// Find the file
		IFile file = resourceAccess.findFileByNameBestEffort(fileName);
		if (file == null) {
			return ApplyChangeResult.failure("File not found: " + fileName);
		}

		// Get document and validate change
		IDocument document = null;
		Map<IFile, IDocument> tempDocMap = new HashMap<>();

		try {
			// Get or create TextFileChange for this file
			String fullPath = file.getFullPath().toString(); // Use full path as key
			TextFileChange tfc = pendingFileChanges.get(fullPath);
			MultiTextEdit multiEdit;

			if (tfc == null) {
				tfc = new TextFileChange("Changes in " + fileName, file);
				if (file.getFileExtension() != null) {
					tfc.setTextType(file.getFileExtension());
				} else {
					tfc.setTextType("txt"); // Default
				}
				multiEdit = new MultiTextEdit();
				tfc.setEdit(multiEdit);
				pendingFileChanges.put(fullPath, tfc);
			} else {
				multiEdit = (MultiTextEdit) tfc.getEdit(); // Assume it's always MultiTextEdit
			}

			document = tfc.getCurrentDocument(new NullProgressMonitor());
			if (document == null) {
				return ApplyChangeResult.failure("Could not get document for file: " + fileName);
			}

			// Parse location string to get search bounds
			int[] searchBounds = parseLocationToOffsets(location, document);
			if (searchBounds == null) {
				return ApplyChangeResult.failure("Invalid location string: " + location);
			}

			try {
				// Find the actual start and end offsets of the matched text
				int[] matchOffsets = findOffsetsByPattern(originalText, document, searchBounds);

				// Generate diff preview the document
				DiffPreviewResult diffResult = generateDiffPreview(file, document, matchOffsets, replacement);

				// Create the TextEdit
				TextEdit edit = createActualTextEdit(document, diffResult.getStartLine(), diffResult.getEndLine(),
						replacement);

				multiEdit.addChild(edit);

				// Create ChangeOperation for the result (still useful for reporting)
				ChangeOperation opForReport = new ChangeOperation(fileName, diffResult.getStartLine(),
						diffResult.getEndLine(), originalText, replacement);

				Gson gson = GsonUtil.createGson();
				String json = gson.toJson(opForReport);
				// Log.logInfo("Validated and added change for file: " + fileName + " at
				// location: " + location
				// + "\n\nJSON:\n" + json);
				Log.logInfo("Validated and added TextEdit for file: " + fileName + " at lines "
						+ diffResult.getStartLine() + "-" + diffResult.getEndLine());

				return ApplyChangeResult.success("Change validated and TextEdit queued for preview", opForReport,
						diffResult.getDiffPreview());
			} catch (IllegalArgumentException | BadLocationException e) {
				return ApplyChangeResult.failure("Failed to locate original text: " + e.getMessage());
			}
		} catch (CoreException e) {
			return ApplyChangeResult.failure("Failed to locate original text: " + e.getMessage());
		} finally {
			// Always disconnect documents we connected during validation
			resourceAccess.disconnectAllDocuments(tempDocMap);
		}
	}

	/**
	 * Generates a diff preview with context from the document.
	 * 
	 * @param file            The file being modified
	 * @param document        The document containing the file content
	 * @param matchOffsets    The actual start and end offsets of the matched text
	 *                        [start, end]
	 * @param replacementText The text that will replace the matched content
	 * @return A DiffPreviewResult containing the markdown-formatted diff preview
	 *         and line information
	 */
	private DiffPreviewResult generateDiffPreview(IFile file, IDocument document, int[] matchOffsets,
			String replacementText) {
		try {
			String original = document.get(matchOffsets[0], matchOffsets[1] - matchOffsets[0]);

			int startLine = document.getLineOfOffset(matchOffsets[0]);
			int endLine = document.getLineOfOffset(matchOffsets[1]);

			// Use the enhanced diff generator with file path and real line numbers
			// startLine + 1 because line numbers are 0-based in IDocument
			String diffText = generateDiffPreview(original, replacementText, file, startLine + 1);

			// Return both the diff preview and the line range information
			return new DiffPreviewResult(diffText, startLine + 1, endLine + 1);
		} catch (BadLocationException e) {
			Log.logError("Error generating diff preview with context", e);
			String errorDiff = "```\nError generating diff preview: " + e.getMessage() + "\n```";
			return new DiffPreviewResult(errorDiff, 0, 0);
		}
	}

	/**
	 * Clear all pending changes from the queue.
	 */
	public void clearChanges() {
		int count = pendingFileChanges.size();
		pendingFileChanges.clear();
		Log.logInfo("Cleared " + count + " pending changes.");
	}

	/**
	 * Get the number of changes currently pending.
	 *
	 * @return The count of pending changes.
	 */
	public int getPendingChangeCount() {
		return pendingFileChanges.size();
	}

	/**
	 * Apply all pending changes using the Eclipse Refactoring API. This will
	 * typically open a preview dialog for the user.
	 */
	public void applyChanges() {
		if (pendingFileChanges.isEmpty()) {
			Log.logInfo("No changes to apply.");
			return;
		}

		try {
			performRefactoring();
		} catch (Exception e) {
			// Log the error and potentially inform the user via UI
			Log.logError("Failed to initiate refactoring process: " + e.getMessage(), e);
			// Consider showing a dialog to the user here
			clearChanges(); // Clear changes to prevent re-attempting a failed operation
		}
	}

	/**
	 * Creates and executes the refactoring operation based on pending changes.
	 */
	private void performRefactoring() throws CoreException {
		// MODIFIED: Use pendingFileChanges directly
		if (pendingFileChanges.isEmpty()) {
			Log.logInfo("No TextFileChanges to apply.");
			return;
		}

		// Create a composite change to hold all individual file changes
		CompositeChange rootChange = new CompositeChange("Code Intelligence Changes");

		// NEW LOGIC: Iterate over the prepared TextFileChange objects
		for (TextFileChange tfc : pendingFileChanges.values()) {
			// A TextFileChange might have been created but no actual edits added if all
			// attempts failed
			// or if it's an empty MultiTextEdit.
			if (tfc.getEdit() instanceof MultiTextEdit) {
				MultiTextEdit mte = (MultiTextEdit) tfc.getEdit();
				if (mte.hasChildren()) {
					rootChange.add(tfc);
				}
			} else if (tfc.getEdit() != null) {
				// This case should ideally not happen if we always set a MultiTextEdit.
				Log.logWarn("TextFileChange for " + tfc.getFile().getName()
						+ " does not have a MultiTextEdit. Adding directly.");
				rootChange.add(tfc);
			}
		}

		// If no valid changes were created, don't proceed
		if (rootChange.getChildren().length == 0) {
			Log.logInfo("No valid changes (TextFileChanges with edits) could be prepared for refactoring.");
			// clearChanges(); // This is now done in the finally block of the asyncExec or
			// if wizard doesn't run
			return;
		}

		// Create the refactoring object
		Refactoring refactoring = new Refactoring() {
			@Override
			public String getName() {
				return "Apply Code Intelligence Changes";
			}

			@Override
			public RefactoringStatus checkInitialConditions(IProgressMonitor pm) {
				// Can add initial checks here if needed
				return new RefactoringStatus(); // Assume OK for now
			}

			@Override
			public RefactoringStatus checkFinalConditions(IProgressMonitor pm) {
				// Can add final checks here, e.g., ensuring files are writable
				return new RefactoringStatus(); // Assume OK for now
			}

			@Override
			public Change createChange(IProgressMonitor pm) {
				// Return the composite change containing all file changes
				return rootChange;
			}
		};

		// Open the refactoring wizard in the UI thread
		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					Log.logError("Cannot apply changes: No active workbench window found.");
					return; // Cannot proceed without a window/shell
				}
				// Use a standard RefactoringWizard
				RefactoringWizard wizard = new RefactoringWizard(refactoring,
						RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
					@Override
					protected void addUserInputPages() {
						// No custom input pages needed for this simple case
					}
				};
				RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
				operation.run(window.getShell(), "Preview Code Changes");

				// Important: Clear pending changes *after* the wizard is potentially closed
				// The user might cancel, but we assume the operation is "done" either way.
				pendingFileChanges.clear();

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Log.logError("Refactoring wizard interrupted: " + e.getMessage(), e);
			} catch (Exception e) { // Catch broader exceptions during wizard operation
				Log.logError("Failed to open or run refactoring wizard: " + e.getMessage(), e);
			}
		});
	}

	/**
	 * Creates a ReplaceEdit by finding the originalText pattern, determining the
	 * lines affected, applying the replacement conceptually, formatting the
	 * affected lines, and creating an edit to replace the original lines with the
	 * formatted ones.
	 *
	 * @param document        The document to apply the edit to.
	 * @param startLine1Based The 1-based starting line of the region to change.
	 * @param endLine1Based   The 1-based ending line of the region to change.
	 * @param replacementText The text to replace the original content with (empty
	 *                        for deletion).
	 * @return The created ReplaceEdit.
	 * @throws BadLocationException If location calculations fail.
	 */
	private TextEdit createActualTextEdit(IDocument document, int startLine1Based, int endLine1Based,
			String replacementText) throws BadLocationException {
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
	 * @return An array [matchStartOffset, matchEndOffset] of the found pattern
	 *         match relative to the beginning of the document.
	 * @throws IllegalArgumentException If the pattern cannot be found within the
	 *                                  specified bounds or if originalText is empty
	 *                                  after processing.
	 * @throws BadLocationException     If searchBounds are invalid for the
	 *                                  document.
	 */
	private int[] findOffsetsByPattern(String originalText, IDocument document, int[] searchBounds)
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

		// Extract the search region text
		String searchRegionText = document.get(searchStart, searchLength);

		// Shortcut for identical match.
		if (StringUtils.stripToEmpty(originalText).equals(StringUtils.stripToEmpty(searchRegionText))) {
			return searchBounds;
		}

		int[] extendedRegion = extendRegion(document, searchStart, searchLength);
		searchRegionText = document.get(extendedRegion[0], extendedRegion[1] - extendedRegion[0]);

		// Use a "searcher" to find matching region
		// ChunkSearcher searcher = new ChunkSearcher();
		SmithWatermanSearcher searcher = new SmithWatermanSearcher();
		int[] matchingRegion = searcher.findMatchingRegion(originalText, searchRegionText);

		if (matchingRegion != null) {
			// Adjust offsets relative to the document start
			return new int[] { extendedRegion[0] + matchingRegion[0], extendedRegion[0] + matchingRegion[1] };
		}

		// If we get here, no match was found
		throw new IllegalArgumentException(
				"Could not find the specified pattern derived from original text within the location bounds ["
						+ searchBounds[0] + "," + searchBounds[1] + "].");
	}

	private int[] extendRegion(IDocument document, int searchStart, int searchLength) throws BadLocationException {
		// Get the line numbers for the search region
		int searchStartLine = document.getLineOfOffset(searchStart);
		int searchEndLine = document.getLineOfOffset(Math.min(searchStart + searchLength, document.getLength() - 1));

		// Count the total number of lines in the search region
		int searchLineCount = searchEndLine - searchStartLine + 1;

		// Add 50% of the line count above and below, but at least 5 lines
		int linesToAdd = Math.max(5, (int) Math.ceil(0.5 * searchLineCount));

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
					3); // Context size of 3 lines

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
