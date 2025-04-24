package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor; // Import needed
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

public class ApplyChangeTool {
	// Inner class to hold change information
	private static class ChangeOperation {
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
		pendingChanges.add(new ChangeOperation(fileName, location, originalText, replacement));
		Activator.logInfo("Added change for file: " + fileName + " at location: " + location);
	}

	/**
	 * Clear all pending changes from the queue.
	 */
	public void clearChanges() {
		int count = pendingChanges.size();
		pendingChanges.clear();
		Activator.logInfo("Cleared " + count + " pending changes.");
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
			Activator.logInfo("No changes to apply.");
			return;
		}

		try {
			performRefactoring();
		} catch (Exception e) {
			// Log the error and potentially inform the user via UI
			Activator.logError("Failed to initiate refactoring process: " + e.getMessage(), e);
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

				IFile file = findFileByNameBestEffort(fileName);
				if (file == null) {
					Activator.logError("Skipping changes for file not found: " + fileName);
					continue; // Skip this file if not found
				}

				// Get the document for the file, managing connection
				IDocument document = getDocumentAndConnect(file, documentMap);
				if (document == null) {
					Activator.logError("Skipping changes for file, could not get document: " + fileName);
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
					} catch (BadLocationException | IllegalArgumentException e) {
						Activator.logError("Failed to create edit for " + fileName + " at " + op.getLocation() + ": "
								+ e.getMessage(), e);
					}
				}

				// Only add the change if it contains edits
				if (multiEdit.hasChildren()) {
					compositeChange.add(textFileChange);
				}
			}

			// If no valid changes were created, don't proceed
			if (compositeChange.getChildren().length == 0) {
				Activator.logInfo("No valid changes could be prepared for refactoring.");
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
						Activator.logError("Cannot apply changes: No active workbench window found.");
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
					Activator.logError("Refactoring wizard interrupted: " + e.getMessage(), e);
				} catch (Exception e) { // Catch broader exceptions during wizard operation
					Activator.logError("Failed to open or run refactoring wizard: " + e.getMessage(), e);
				}
			});

		} finally {
			// Ensure all connected documents are disconnected
			disconnectAllDocuments(documentMap);
		}
	}

	/**
	 * Creates a ReplaceEdit based on searching for the originalText within the
	 * document. The location string provided during addChange is currently ignored
	 * by this implementation.
	 *
	 * @param file     The file being modified.
	 * @param document The document to apply the edit to.
	 * @param op       The change operation details, containing the originalText to
	 *                 search for.
	 * @return The created ReplaceEdit.
	 * @throws BadLocationException     If the location derived from the search is
	 *                                  invalid (should not happen if found).
	 * @throws IllegalArgumentException If the originalText cannot be found in the
	 *                                  document.
	 */
	private ReplaceEdit createTextEdit(IFile file, IDocument document, ChangeOperation op)
			throws BadLocationException, IllegalArgumentException {

		String content = document.get();
		String originalTextToFind = op.getOriginalText();

		if (originalTextToFind == null || originalTextToFind.isEmpty()) {
			throw new IllegalArgumentException("Original text cannot be null or empty for search-based replacement.");
		}

		int startOffset = content.indexOf(originalTextToFind);

		if (startOffset == -1) {
			// Original text not found in the document
			// Consider adding more context to the error, like the first few chars of
			// originalText
			throw new IllegalArgumentException("Original text not found in file " + file.getName()
					+ " for change at location hint: " + op.getLocation());
			// TODO: Add better handling for multiple occurrences? For now, uses the first
			// match.
		}

		int length = originalTextToFind.length();

		// Format the replacement text before creating the edit
		String originalReplacement = op.getReplacement();
		// Formatting should still use the found startOffset to determine indentation
		String formattedReplacement = formatReplacementText(originalReplacement, file, document, startOffset);

		// Note: The original text validation block is implicitly handled now,
		// because we *found* the original text to determine the offset.

		return new ReplaceEdit(startOffset, length, formattedReplacement);
	}

	/**
	 * Formats the given replacement text using the CodeFormatter and adjusts its
	 * indentation to match the insertion point in the target document.
	 *
	 * @param replacementText The raw replacement text.
	 * @param document        The target document where the text will be inserted.
	 * @param insertionOffset The offset in the document where the replacement will
	 *                        start.
	 * @return The formatted and indented replacement text.
	 */
	private String formatReplacementText(String replacementText, IFile file, IDocument document, int insertionOffset) {
		if (replacementText == null || replacementText.isEmpty()) {
			return replacementText;
		}

		IProject project = file.getProject();
		IJavaProject javaProject = null;
		if (project != null) {
			javaProject = JavaCore.create(project);
		}

		int indentationLevel = 0;
		Map<String, String> options = JavaCore.getOptions(); // Default options

		// 1. Determine target indentation level
		try {
			if (javaProject != null) {
				options = javaProject.getOptions(true); // Use project-specific options if available
			}

			int lineIndex = document.getLineOfOffset(insertionOffset);
			IRegion lineInfo = document.getLineInformation(lineIndex);
			String currentLine = document.get(lineInfo.getOffset(), lineInfo.getLength());

			String leadingWhitespace = "";
			for (int i = 0; i < currentLine.length(); i++) {
				char c = currentLine.charAt(i);
				if (Character.isWhitespace(c)) {
					leadingWhitespace += c;
				} else {
					break;
				}
			}

			int indentWidth = CodeFormatterUtil.getIndentWidth(javaProject); // Use project-aware util
			int tabWidth = CodeFormatterUtil.getTabWidth(javaProject); // Use project-aware util
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
			Activator.logWarn("Could not determine indentation level for replacement text: " + e.getMessage());
			// Proceed with indentationLevel = 0 and default options
			options = JavaCore.getOptions(); // Reset to default if project options failed
		}

		// 2. Format the code snippet with the determined level
		CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
		if (formatter != null) {
			// Use K_STATEMENTS as a guess, might need adjustment depending on typical
			// replacement content.
			String lineSeparator = TextUtilities.getDefaultLineDelimiter(document); // Use document's line delimiter
			TextEdit edit = formatter.format(CodeFormatter.K_STATEMENTS, replacementText, 0, // offset in
																								// replacementText
					replacementText.length(), // length of replacementText
					indentationLevel, lineSeparator);

			if (edit != null) {
				try {
					org.eclipse.jface.text.IDocument tempDoc = new org.eclipse.jface.text.Document(replacementText);
					edit.apply(tempDoc);
					return tempDoc.get();
				} catch (MalformedTreeException | BadLocationException e) {
					// Log error or warning - formatting the snippet failed, proceed with
					// unformatted
					Activator.logWarn("Could not format replacement snippet: " + e.getMessage());
					return replacementText; // Fallback to original on formatting error
				}
			} else {
				// Formatter returned null edit (e.g., syntax error in snippet), return original
				return replacementText;
			}
		}

		// Fallback if formatter couldn't be created or formatting failed
		return replacementText;
	}

	/**
	 * Gets the IDocument for a file, managing the connection via
	 * TextFileBufferManager. Stores the connected document in the provided map.
	 *
	 * @param file        The file to get the document for.
	 * @param documentMap A map to store the connected document and its buffer.
	 * @return The IDocument, or null if connection fails.
	 */
	private IDocument getDocumentAndConnect(IFile file, Map<IFile, IDocument> documentMap) {
		if (documentMap.containsKey(file)) {
			return documentMap.get(file);
		}

		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		IPath path = file.getFullPath();
		IDocument document = null;
		try {
			// Connect to the file buffer
			bufferManager.connect(path, LocationKind.IFILE, new NullProgressMonitor());
			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
			if (textFileBuffer != null) {
				document = textFileBuffer.getDocument();
				documentMap.put(file, document); // Store for later disconnection
			} else {
				Activator.logError("Could not get text file buffer for: " + file.getName());
			}
		} catch (CoreException e) {
			Activator.logError("Failed to connect or get document for " + file.getName() + ": " + e.getMessage(), e);
			// Ensure disconnection if connection partially succeeded but failed later
			try {
				bufferManager.disconnect(path, LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException disconnectEx) {
				Activator.logError("Error during buffer disconnect cleanup for " + file.getName(), disconnectEx);
			}
			return null; // Return null if document couldn't be obtained
		}
		return document;
	}

	/**
	 * Disconnects all documents managed in the provided map.
	 *
	 * @param documentMap The map containing files and their connected documents.
	 */
	private void disconnectAllDocuments(Map<IFile, IDocument> documentMap) {
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		for (IFile file : documentMap.keySet()) {
			try {
				bufferManager.disconnect(file.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException e) {
				Activator.logError("Failed to disconnect document for " + file.getName() + ": " + e.getMessage(), e);
			}
		}
		documentMap.clear(); // Clear the map after disconnecting
	}

	/**
	 * Finds the IFile resource corresponding to the given file name. Provides basic
	 * handling for ambiguity (multiple files with the same name).
	 *
	 * @param fileName The simple name of the file (e.g., "MyClass.java").
	 * @return The IFile if found uniquely, or a best guess, or null.
	 */
	private IFile findFileByNameBestEffort(String fileName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final List<IFile> foundFiles = new ArrayList<>();

		try {
			root.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource.getType() == IResource.FILE && resource.getName().equals(fileName)) {
						foundFiles.add((IFile) resource);
						// Optimization: If you only ever want the *first* match, uncomment the next
						// line
						// return false;
					}
					// Continue searching in subfolders regardless
					return true;
				}
			});
		} catch (CoreException e) {
			Activator.logError("Error searching for file: " + fileName, e);
			return null; // Return null on error during search
		}

		// Evaluate search results
		if (foundFiles.size() == 1) {
			return foundFiles.get(0); // Unique match found
		} else if (foundFiles.isEmpty()) {
			Activator.logInfo("No file found with name: " + fileName);
			return null; // No file found
		} else {
			// Ambiguous case: multiple files found
			// Log a warning and return the first one found as a best effort.
			// Consider making this behavior configurable or throwing an error.
			Activator.logInfo("Multiple files found with name: " + fileName + ". Using the first one: "
					+ foundFiles.get(0).getFullPath());
			// You might want to try finding the file in the active editor first here
			// IFile fileFromEditor = findFileInActiveEditor(fileName);
			// if (fileFromEditor != null) return fileFromEditor;
			return foundFiles.get(0); // Return the first match as a fallback
		}
	}

}
