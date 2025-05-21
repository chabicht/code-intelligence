package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.util.GsonUtil; // If you still want to log JSON
import com.chabicht.code_intelligence.util.Log; // Your Log utility
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.google.gson.Gson;

public class ApplyPatchTool {

	static class ChangeOperation {
		private final String fileName;
		private final String originalText;
		private final String replacement;
		private final int startLine; // 1-based
		private final int endLine; // 1-based, (startLine - 1) for pure insert before startLine

		public ChangeOperation(String fileName, int startLine, int endLine, String originalText, String replacement) {
			this.fileName = fileName;
			this.startLine = startLine;
			this.endLine = endLine;
			this.originalText = originalText; // Text from the patch's original hunk
			this.replacement = replacement; // Text from the patch's revised hunk
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

	public static class ApplyPatchResult {
		private final boolean success;
		private final String message;
		private final String inputPatchPreview; // The patch string that was processed
		private final List<ChangeOperation> operations; // List of operations created

		private ApplyPatchResult(boolean success, String message, String inputPatchPreview,
				List<ChangeOperation> operations) {
			this.success = success;
			this.message = message;
			this.inputPatchPreview = inputPatchPreview;
			this.operations = operations != null ? operations : new ArrayList<>();
		}

		public static ApplyPatchResult success(String message, String inputPatchPreview,
				List<ChangeOperation> operations) {
			return new ApplyPatchResult(true, message, inputPatchPreview, operations);
		}

		public static ApplyPatchResult failure(String message) {
			return new ApplyPatchResult(false, message, null, null);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public String getInputPatchPreview() {
			return inputPatchPreview;
		}

		public List<ChangeOperation> getOperations() {
			return operations;
		}
	}

	private List<ChangeOperation> pendingChanges = new ArrayList<>();
	private IResourceAccess resourceAccess;

	public ApplyPatchTool(IResourceAccess resourceAccess) {
		this.resourceAccess = resourceAccess;
	}

	/**
	 * Adds changes from a unified diff patch string to the queue.
	 *
	 * @param fileName    The name of the file to patch.
	 * @param patchString The unified diff patch content as a string.
	 * @return An ApplyPatchResult indicating success or failure.
	 */
	public ApplyPatchResult addChangesFromPatch(String fileName, String patchString) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return ApplyPatchResult.failure("File name cannot be null or empty");
		}
		if (patchString == null || patchString.trim().isEmpty()) {
			return ApplyPatchResult.failure("Patch string cannot be null or empty");
		}

		IFile file = resourceAccess.findFileByNameBestEffort(fileName);
		if (file == null) {
			return ApplyPatchResult.failure("File not found: " + fileName);
		}

		Map<IFile, IDocument> tempDocMap = new HashMap<>();
		IDocument document = null;
		List<ChangeOperation> newOperations = new ArrayList<>();

		try {
			document = resourceAccess.getDocumentAndConnect(file, tempDocMap);
			if (document == null) {
				return ApplyPatchResult.failure("Could not get document for file: " + fileName);
			}

			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			List<String> originalDocLines = Arrays.asList(document.get().split("\\R", -1));
			List<String> patchLines = Arrays.asList(patchString.split("\\R", -1));

			Patch<String> patch;
			try {
				// DiffUtils.parseUnifiedDiff expects the patch lines, not the original file
				// lines.
				patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
			} catch (Exception e) { // Catch broader exceptions from parsing, though parseUnifiedDiff is quite
									// robust
				Log.logError("Failed to parse unified diff: " + e.getMessage(), e);
				return ApplyPatchResult.failure("Failed to parse unified diff: " + e.getMessage());
			}

			// Validate the patch by trying to apply it (in memory)
			List<String> patchedDocLines;
			try {
				patchedDocLines = patch.applyFuzzy(originalDocLines, 10);
			} catch (PatchFailedException e) {
				Log.logError("Patch validation failed for file " + fileName + ": " + e.getMessage(), e);
				return ApplyPatchResult.failure("Patch cannot be applied to the current file state: " + e.getMessage());
			}

			// Create a single ChangeOperation for the entire file content
			String originalText = String.join(lineDelimiter, originalDocLines);
			String replacementText = String.join(lineDelimiter, patchedDocLines);

			ChangeOperation op = new ChangeOperation(fileName, 1, originalDocLines.size(), originalText,
					replacementText);
			newOperations.add(op);

			pendingChanges.addAll(newOperations);
			Log.logInfo("Validated and added " + newOperations.size() + " change operations from patch for file: "
					+ fileName);
			return ApplyPatchResult.success(newOperations.size() + " change operations queued from patch.", patchString,
					newOperations);

		} catch (Exception e) { // Catch any other unexpected errors
			Log.logError("Unexpected error processing patch for file " + fileName + ": " + e.getMessage(), e);
			return ApplyPatchResult.failure("Unexpected error processing patch: " + e.getMessage());
		} finally {
			resourceAccess.disconnectAllDocuments(tempDocMap);
		}
	}

	public void clearChanges() {
		int count = pendingChanges.size();
		pendingChanges.clear();
		Log.logInfo("Cleared " + count + " pending changes.");
	}

	public int getPendingChangeCount() {
		return pendingChanges.size();
	}

	public void applyPendingChanges() {
		if (pendingChanges.isEmpty()) {
			Log.logInfo("No changes to apply.");
			return;
		}
		try {
			performRefactoring();
		} catch (Exception e) {
			Log.logError("Failed to initiate refactoring process for patch: " + e.getMessage(), e);
			clearChanges();
		}
	}

	private void performRefactoring() throws CoreException {
		Map<String, List<ChangeOperation>> changesByFile = new HashMap<>();
		for (ChangeOperation op : pendingChanges) {
			changesByFile.computeIfAbsent(op.getFileName(), k -> new ArrayList<>()).add(op);
		}

		CompositeChange compositeChange = new CompositeChange("Apply Patched Changes");
		Map<IFile, IDocument> documentMap = new HashMap<>();

		try {
			for (Map.Entry<String, List<ChangeOperation>> entry : changesByFile.entrySet()) {
				String fileName = entry.getKey();
				List<ChangeOperation> fileChanges = entry.getValue();
				IFile file = resourceAccess.findFileByNameBestEffort(fileName);
				if (file == null) {
					Log.logError("Skipping patch changes for file not found: " + fileName);
					continue;
				}

				IDocument document = resourceAccess.getDocumentAndConnect(file, documentMap);
				if (document == null) {
					Log.logError("Skipping patch changes, could not get document: " + fileName);
					continue;
				}

				TextFileChange textFileChange = new TextFileChange("Patched changes in " + fileName, file);
				textFileChange.setTextType(file.getFileExtension() != null ? file.getFileExtension() : "txt");
				MultiTextEdit multiEdit = new MultiTextEdit();
				textFileChange.setEdit(multiEdit);

				for (ChangeOperation op : fileChanges) {
					try {
						ReplaceEdit edit = createEditForOperation(document, op);
						multiEdit.addChild(edit);
					} catch (BadLocationException | IllegalArgumentException | MalformedTreeException e) {
						Gson gson = GsonUtil.createGson();
						String json = gson.toJson(op);
						Log.logError("Failed to create edit from patch operation for " + fileName + " (op lines "
								+ op.getStartLine() + "-" + op.getEndLine() + "): " + e.getMessage() + "\nJSON Op:\n"
								+ json, e);
					}
				}
				if (multiEdit.hasChildren()) {
					compositeChange.add(textFileChange);
				}
			}

			if (compositeChange.getChildren().length == 0) {
				Log.logInfo("No valid patch changes could be prepared for refactoring.");
				clearChanges();
				return;
			}

			Refactoring refactoring = new Refactoring() {
				@Override
				public String getName() {
					return "Apply Patched Code Changes";
				}

				@Override
				public RefactoringStatus checkInitialConditions(IProgressMonitor pm) {
					return new RefactoringStatus();
				}

				@Override
				public RefactoringStatus checkFinalConditions(IProgressMonitor pm) {
					return new RefactoringStatus();
				}

				@Override
				public Change createChange(IProgressMonitor pm) {
					return compositeChange;
				}
			};

			Display.getDefault().asyncExec(() -> {
				try {
					IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
					if (window == null) {
						Log.logError("Cannot apply patch changes: No active workbench window.");
						return;
					}
					RefactoringWizard wizard = new RefactoringWizard(refactoring,
							RefactoringWizard.DIALOG_BASED_USER_INTERFACE
									| RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
						@Override
						protected void addUserInputPages() {
							/* None needed */ }
					};
					RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
					operation.run(window.getShell(), "Preview Patched Code Changes");
					pendingChanges.clear(); // Clear after wizard is handled
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					Log.logError("Patch refactoring wizard interrupted: " + e.getMessage(), e);
				} catch (Exception e) {
					Log.logError("Failed to open or run patch refactoring wizard: " + e.getMessage(), e);
				}
			});
		} finally {
			resourceAccess.disconnectAllDocuments(documentMap);
		}
	}

	/**
	 * Creates a ReplaceEdit for a given ChangeOperation derived from a patch delta.
	 */
	private ReplaceEdit createEditForOperation(IDocument document, ChangeOperation op)
			throws BadLocationException, IllegalArgumentException {
		int opStartLine1Based = op.getStartLine();
		int opEndLine1Based = op.getEndLine(); // This is (opStartLine1Based - 1) for pure inserts
		String replacementText = op.getReplacement();

		// Handle pure insert case (where opEndLine1Based < opStartLine1Based)
		if (opEndLine1Based < opStartLine1Based) {
			int insertLine0Based = opStartLine1Based - 1;

			// Check if inserting before the first line (insertLine0Based = 0 if
			// opStartLine1Based = 1)
			// or appending to document (insertLine0Based = document.getNumberOfLines() if
			// opStartLine1Based = document.getNumberOfLines() + 1)
			if (opStartLine1Based < 1 || opStartLine1Based > document.getNumberOfLines() + 1) {
				throw new BadLocationException("Invalid line number for insert: " + opStartLine1Based + ", doc lines: "
						+ document.getNumberOfLines());
			}

			int offset;
			if (opStartLine1Based == document.getNumberOfLines() + 1) { // Append to end of document
				offset = document.getLength();
			} else { // Insert before an existing line or at the beginning of an empty document
				if (document.getNumberOfLines() == 0 && opStartLine1Based == 1) {
					offset = 0;
				} else {
					offset = document.getLineOffset(insertLine0Based); // insertLine0Based is correct for getLineOffset
				}
			}
			return new ReplaceEdit(offset, 0, replacementText);
		} else {
			// Modification or Deletion
			int startLine0Based = opStartLine1Based - 1;
			int endLine0Based = opEndLine1Based - 1; // This is the last line index of the original chunk

			if (startLine0Based < 0 || endLine0Based < startLine0Based
					|| endLine0Based >= document.getNumberOfLines()) {
				throw new BadLocationException("Invalid line range for operation: " + opStartLine1Based + "-"
						+ opEndLine1Based + ", doc lines: " + document.getNumberOfLines());
			}

			IRegion startLineInfo = document.getLineInformation(startLine0Based);
			IRegion endLineInfo = document.getLineInformation(endLine0Based);

			int offset = startLineInfo.getOffset();
			// For deletion, original text spans from start of startLine to end of endLine
			int length = (endLineInfo.getOffset() + endLineInfo.getLength()) - offset;

			return new ReplaceEdit(offset, length, replacementText);
		}
	}
}
