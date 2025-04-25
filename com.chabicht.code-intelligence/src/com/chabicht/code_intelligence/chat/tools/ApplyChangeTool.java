package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.chabicht.code_intelligence.util.Log;
import com.google.gson.Gson;

public class ApplyChangeTool {
	private static final int SEARCH_WINDOW_RADIUS_CHARS = 500;

	// Inner class to hold change information
	static class ChangeOperation {
		private final String fileName;
		private final String location;
		private final String originalText; // Keep for potential future validation
		private final String replacement;

		public ChangeOperation(String fileName, String location, String originalText, String replacement) {
			this.fileName = fileName;
			this.location = location;
			this.originalText = originalText;
			this.replacement = replacement;
		}

		public String getFileName() {
			return fileName;
		}

		public String getLocation() {
			return location;
		}

		public String getOriginalText() {
			return originalText;
		}

		public String getReplacement() {
			return replacement;
		}
	}

	// List to store pending changes
	private List<ChangeOperation> pendingChanges = new ArrayList<>();
	private IResourceAccess resourceAccess;

	public ApplyChangeTool(IResourceAccess resourceAccess) {
		this.resourceAccess = resourceAccess;
	}

	/**
	 * Add a change to the queue of pending changes.
	 *
	 * @param fileName     The name of the file to change.
	 * @param location     The location string (e.g., "l10:15").
	 * @param originalText The original text expected at the location (for
	 *                     validation).
	 * @param replacement  The text to replace the original content with.
	 */
	public void addChange(String fileName, String location, String originalText, String replacement) {
		ChangeOperation op = new ChangeOperation(fileName, location, originalText, replacement);
		pendingChanges.add(op);

		Gson gson = GsonUtil.createGson();
		String json = gson.toJson(op);
		Log.logInfo("Added change for file: " + fileName + " at location: " + location + "\n\nJSON:\n" + json);
	}

	/**
	 * Clear all pending changes from the queue.
	 	 */
	public void clearChanges() {
		int count = pendingChanges.size();
		pendingChanges.clear();
		Log.logInfo("Cleared " + count + " pending changes.");
	}

	/**
	 * Get the number of changes currently pending.
	 *
	 * @return The count of pending changes.
	 */
	public int getPendingChangeCount() {
		return pendingChanges.size();
	}

	/**
	 * Apply all pending changes using the Eclipse Refactoring API. This will
	 * typically open a preview dialog for the user.
	 */
	public void applyChanges() {
				if (pendingChanges.isEmpty()) {
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
		// Group changes by filename
		Map<String, List<ChangeOperation>> changesByFile = new HashMap<>();
		for (ChangeOperation op : pendingChanges) {
			changesByFile.computeIfAbsent(op.getFileName(), k -> new ArrayList<>()).add(op);
		}

		// Create a composite change to hold all individual file changes
		CompositeChange compositeChange = new CompositeChange("Code Intelligence Changes");
		Map<IFile, IDocument> documentMap = new HashMap<>(); // To manage documents

		try {
			// Process changes for each file
			for (Map.Entry<String, List<ChangeOperation>> entry : changesByFile.entrySet()) {
				String fileName = entry.getKey();
				List<ChangeOperation> fileChanges = entry.getValue();

				IFile file = resourceAccess.findFileByNameBestEffort(fileName);
				if (file == null) {
					Log.logError("Skipping changes for file not found: " + fileName);
					continue; // Skip this file if not found
				}

				// Get the document for the file, managing connection
				IDocument document = resourceAccess.getDocumentAndConnect(file, documentMap);
				if (document == null) {
					Log.logError("Skipping changes for file, could not get document: " + fileName);
					continue;
				}

				// Create a TextFileChange for this file
				TextFileChange textFileChange = new TextFileChange("Changes in " + fileName, file);
				textFileChange.setTextType(file.getFileExtension() != null ? file.getFileExtension() : "txt");
				MultiTextEdit multiEdit = new MultiTextEdit();
				textFileChange.setEdit(multiEdit);

				// Add all edits for this file
				for (ChangeOperation op : fileChanges) {
					try {
						ReplaceEdit edit = createTextEdit(file, document, op);
						multiEdit.addChild(edit);
					} catch (BadLocationException | IllegalArgumentException | MalformedTreeException e) {
						Gson gson = GsonUtil.createGson();
						String json = gson.toJson(op);
						Log.logError("Failed to create edit for " + fileName + " at " + op.getLocation() + ": "
								+ e.getMessage() + "\n\nJSON:\n" + json, e);
					}
				}

				// Only add the change if it contains edits
				if (multiEdit.hasChildren()) {
					compositeChange.add(textFileChange);
				}
			}

			// If no valid changes were created, don't proceed
			if (compositeChange.getChildren().length == 0) {
				Log.logInfo("No valid changes could be prepared for refactoring.");
				clearChanges();
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
					return compositeChange;
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
							RefactoringWizard.DIALOG_BASED_USER_INTERFACE
									| RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
						@Override
						protected void addUserInputPages() {
							// No custom input pages needed for this simple case
						}
					};
					RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
					operation.run(window.getShell(), "Preview Code Changes");

					// Important: Clear pending changes *after* the wizard is potentially closed
					// The user might cancel, but we assume the operation is "done" either way.
					pendingChanges.clear();

				
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					Log.logError("Refactoring wizard interrupted: " + e.getMessage(), e);
								} catch (Exception e) { // Catch broader exceptions during wizard operation
					Log.logError("Failed to open or run refactoring wizard: " + e.getMessage(), e);
				}
			});

		} finally {
			// Ensure all connected documents are disconnected
			resourceAccess.disconnectAllDocuments(documentMap);
		}
	}

	/**
	 * Creates a ReplaceEdit by finding the originalText pattern, determining the
	 * lines affected, applying the replacement conceptually, formatting the
	 * affected lines, and creating an edit to replace the original lines with the
	 * formatted ones.
	 *
	 * @param file     The file being modified.
	 * @param document The document to apply the edit to.
	 * @param op       The change operation details.
	 * @return The created ReplaceEdit.
	 * @throws BadLocationException     If location calculations fail.
	 * @throws IllegalArgumentException If the originalText pattern cannot be found,
	 *                                  location is invalid, or formatting fails
	 *                                  critically.
	 */
	private ReplaceEdit createTextEdit(IFile file, IDocument document, ChangeOperation op)
			throws BadLocationException, IllegalArgumentException {

		String originalTextToFind = op.getOriginalText();
		if (originalTextToFind == null || originalTextToFind.isEmpty()) {
			throw new IllegalArgumentException("Original text cannot be null or empty for pattern-based replacement.");
		}
		String rawReplacementText = op.getReplacement() != null ? op.getReplacement() : ""; // Handle null replacement

		// 1. Parse the location string to get initial search bounds [startOffset,
		// endOffset]
		int[] searchBounds = parseLocationToOffsets(op.getLocation(), document);
		if (searchBounds == null) {
			throw new IllegalArgumentException("Invalid location string provided: " + op.getLocation());
		}

		// 2. Find the precise offsets of the match within the bounds
		int[] matchOffsets = findOffsetsByPattern(originalTextToFind, document, searchBounds);
		// findOffsetsByPattern throws IllegalArgumentException if not found
		int matchStartOffset = matchOffsets[0];
		int matchEndOffset = matchOffsets[1];
		// int matchLength = matchEndOffset - matchStartOffset; // Length of the exact
		// match

		// 3. Determine the full lines affected by the match
		int startLine = document.getLineOfOffset(matchStartOffset);
		// If the match ends exactly at the beginning of a line, consider the previous
		// line as the end line
		int endLine = (matchEndOffset > 0
				&& document.getLineOfOffset(matchEndOffset) > document.getLineOfOffset(matchEndOffset - 1))
						? document.getLineOfOffset(matchEndOffset - 1)
						: document.getLineOfOffset(matchEndOffset);

		IRegion startLineInfo = document.getLineInformation(startLine);
		IRegion endLineInfo = document.getLineInformation(endLine);

		int originalLinesStartOffset = startLineInfo.getOffset();
		int originalLinesEndOffset = endLineInfo.getOffset() + endLineInfo.getLength();
		int originalLinesLength = originalLinesEndOffset - originalLinesStartOffset;

		// Include the line delimiter at the end if the original region had one
		// This helps preserve the overall line structure after replacement.
		String originalRegionWithTrailingDelimiter = document.get(originalLinesStartOffset, originalLinesLength);
		String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
		boolean endsWithDelimiter = originalRegionWithTrailingDelimiter.endsWith(lineDelimiter);

		// 4. Get the text of the original lines
		String originalLinesText = document.get(originalLinesStartOffset, originalLinesLength);

		// 5. Create the unformatted text of the lines *after* replacement
		// Calculate the start offset of the match *relative* to the start of the
		// original lines region
		int relativeMatchStart = matchStartOffset - originalLinesStartOffset;
		int relativeMatchEnd = matchEndOffset - originalLinesStartOffset;

		// Build the text block with the replacement applied
		StringBuilder modifiedLinesBuilder = new StringBuilder();
		modifiedLinesBuilder.append(originalLinesText.substring(0, relativeMatchStart));
		modifiedLinesBuilder.append(rawReplacementText);
		modifiedLinesBuilder.append(originalLinesText.substring(relativeMatchEnd));
		String unformattedModifiedLines = modifiedLinesBuilder.toString();

		// 6. Format the modified lines region
		// We pass the start offset of the *first line* to determine base indentation
		String formattedModifiedLines = formatRegionText(unformattedModifiedLines, file, document,
				originalLinesStartOffset);

		// 7. Ensure the formatted text ends with a line delimiter if the original did,
		// unless the formatted text is now empty.
		if (endsWithDelimiter && !formattedModifiedLines.isEmpty() && !formattedModifiedLines.endsWith(lineDelimiter)) {
			// Check if the *content* ends with the delimiter, ignoring trailing whitespace
			// potentially added by formatter
			String trimmedFormatted = formattedModifiedLines.stripTrailing();
			if (!trimmedFormatted.endsWith(lineDelimiter)) {
				formattedModifiedLines += lineDelimiter;
			}
		}
		// Conversely, if the original didn't end with a delimiter (e.g. end of file),
		// but the formatter added one, remove it. This is less common but possible.
		else if (!endsWithDelimiter && formattedModifiedLines.endsWith(lineDelimiter)) {
			formattedModifiedLines = formattedModifiedLines.substring(0,
					formattedModifiedLines.length() - lineDelimiter.length());
		}

		// 8. Create the edit replacing the original lines with the formatted modified
		// lines
		return new ReplaceEdit(originalLinesStartOffset, originalLinesLength, formattedModifiedLines);
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

		// 1. Process originalText to create pattern parts
		String[] lines = originalText.split("\\R"); // Split by any Unicode newline sequence
		List<String> patternParts = Arrays.stream(lines).map(String::trim) // Strip leading/trailing whitespace
				.filter(line -> !line.isEmpty()) // Filter out empty lines
				.map(Pattern::quote) // Quote lines to treat them as literals in regex
				.collect(Collectors.toList());

		if (patternParts.isEmpty()) {
			throw new IllegalArgumentException(
					"Original text contains only whitespace, cannot create a search pattern.");
		}

		// 2. Construct the regex pattern
		String regexPattern = String.join("\\s*", patternParts);
		Pattern pattern = Pattern.compile(regexPattern, Pattern.MULTILINE);

		// 3. Define the search region in the document
		int searchStart = searchBounds[0];
		int searchLength = searchBounds[1] - searchBounds[0];
		if (searchStart < 0 || searchLength < 0 || searchStart + searchLength > document.getLength()) {
			throw new BadLocationException("Invalid search bounds [" + searchStart + ", " + (searchStart + searchLength)
					+ "] for document length " + document.getLength());
		}
		String searchRegionText = document.get(searchStart, searchLength);

		// 4. Search for the pattern within the region
		Matcher matcher = pattern.matcher(searchRegionText);

		if (matcher.find()) {
			// Adjust found offsets relative to the start of the document
			int matchStartOffset = searchStart + matcher.start();
			int matchEndOffset = searchStart + matcher.end();
			return new int[] { matchStartOffset, matchEndOffset };
		} else {
			// Log the pattern for debugging if needed
			// Log.logInfo("Pattern not found: " + regexPattern + " within bounds [" +
			// searchBounds[0] + "," + searchBounds[1] + "]");
			throw new IllegalArgumentException(
					"Could not find the specified pattern derived from original text within the location bounds ["
							+ searchBounds[0] + "," + searchBounds[1] + "]. Pattern: " + regexPattern);
		}
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
				int endLine = Integer.parseInt(parts[1]);
				int numDocLines = document.getNumberOfLines(); // Get total lines once

				if (startLine < 1 || endLine < startLine || startLine > numDocLines) {
					Log.logWarn(
							"Invalid line numbers in location: " + location + " (Doc lines: " + numDocLines + ")");
					return null;
				}

				final int CONTEXT_LINES = 3; // Number of context lines to add before and after

				// Convert 1-based line numbers to 0-based indices for the *original* selection
				int originalStartLineIndex = startLine - 1;
				// Ensure original endLineIndex doesn't exceed document bounds
				int originalEndLineIndex = Math.min(endLine - 1, numDocLines - 1);

				// Calculate the start line index including context, clamped to document start (0)
				int contextStartLineIndex = Math.max(0, originalStartLineIndex - CONTEXT_LINES);

				// Calculate the end line index including context, clamped to document end
				int contextEndLineIndex = Math.min(numDocLines - 1, originalEndLineIndex + CONTEXT_LINES);

				try {
					// Get start offset from the beginning of the context start line
					start = document.getLineOffset(contextStartLineIndex);

					// Get end offset from the end of the context end line
					IRegion contextEndLineInfo = document.getLineInformation(contextEndLineIndex);
					end = contextEndLineInfo.getOffset() + contextEndLineInfo.getLength();
				} catch (BadLocationException e) {
					// This should theoretically not happen due to boundary checks, but handle
					// defensively
					Log.logError("Error calculating offsets with context for location: " + location, e);
					return null; // Or handle error appropriately
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
	 * Formats the given region text using the CodeFormatter, determining the base
	 * indentation from the region's starting position in the original document.
	 *
	 * @param regionText        The text of the code region (potentially multi-line)
	 *                          to format. This text already includes the conceptual
	 *                          replacement.
	 * @param file              The context file.
	 * @param document          The original document (used for options and line
	 *                          delimiter).
	 * @param regionStartOffset The starting offset of this region in the *original*
	 *                          document, used to determine indentation.
	 * @return The formatted region text.
	 */
	private String formatRegionText(String regionText, IFile file, IDocument document, int regionStartOffset) {
		if (regionText == null || regionText.isEmpty()) {
			return regionText; // Nothing to format
		}

		IProject project = file.getProject();
		IJavaProject javaProject = null;
		if (project != null) {
			javaProject = JavaCore.create(project);
		}

		int indentationLevel = 0;
		Map<String, String> options = JavaCore.getOptions(); // Default options

		// 1. Determine target indentation level based on the region's start offset
		try {
			if (javaProject != null) {
				options = javaProject.getOptions(true); // Use project-specific options
			}

			int lineIndex = document.getLineOfOffset(regionStartOffset);
			IRegion lineInfo = document.getLineInformation(lineIndex);
			String firstLine = document.get(lineInfo.getOffset(), lineInfo.getLength());

			// Calculate indentation level of the first line (same logic as before)
			String leadingWhitespace = "";
			for (int i = 0; i < firstLine.length(); i++) {
				char c = firstLine.charAt(i);
				if (Character.isWhitespace(c)) {
					leadingWhitespace += c;
				} else {
					break;
				}
			}
			int indentWidth = CodeFormatterUtil.getIndentWidth(javaProject);
			int tabWidth = CodeFormatterUtil.getTabWidth(javaProject);
			int visualColumn = 0;
			for (int i = 0; i < leadingWhitespace.length(); i++) {
				char c = leadingWhitespace.charAt(i);
				if (c == '\t') {
					visualColumn += tabWidth - (visualColumn % tabWidth);
				} else {
					visualColumn++;
				}
			}
			if (indentWidth > 0) {
				indentationLevel = visualColumn / indentWidth;
			}

		} catch (BadLocationException e) {
			Log.logWarn("Could not determine indentation level for formatting region: " + e.getMessage());
			// Proceed with indentationLevel = 0 and default options
			options = JavaCore.getOptions(); // Reset to default
		}

		// 2. Format the region text
		CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
		if (formatter != null) {
			// K_STATEMENTS is often suitable for regions within methods.
			// K_UNKNOWN might be safer if the region could be anything, but K_STATEMENTS
			// generally works well for typical refactorings/changes within bodies.
			// Consider K_COMPILATION_UNIT if formatting often fails, but it's slower.
			String lineSeparator = TextUtilities.getDefaultLineDelimiter(document);
			TextEdit edit = formatter.format(CodeFormatter.K_STATEMENTS, regionText, 0, // offset in regionText
					regionText.length(), // length of regionText
					indentationLevel, lineSeparator);

			if (edit != null) {
				try {
					// Apply formatting to a temporary document containing the region text
					org.eclipse.jface.text.IDocument tempDoc = new org.eclipse.jface.text.Document(regionText);
					edit.apply(tempDoc);
					return tempDoc.get();
				} catch (MalformedTreeException | BadLocationException e) {
					Activator
							.logWarn("Could not format region snippet: " + e.getMessage() + ". Returning unformatted.");
					return regionText; // Fallback to unformatted on formatting error
				}
			} else {
				// Formatter returned null edit (e.g., syntax error in the modified region)
				Log.logWarn(
						"Code formatter returned null edit for region, possibly due to syntax errors. Returning unformatted.");
				return regionText;
			}
		}

		// Fallback if formatter couldn't be created
		Log.logWarn("Could not create code formatter. Returning unformatted region text.");
		return regionText;
	}

}
