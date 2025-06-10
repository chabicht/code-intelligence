package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.MultiStateTextFileChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.util.Log;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

public class ApplyPatchTool {

	public static class ApplyPatchResult {
		private final boolean success;
		private final String message;

		private ApplyPatchResult(boolean success, String message) {
			this.success = success;
			this.message = message;
		}

		public static ApplyPatchResult success(String message) {
			return new ApplyPatchResult(true, message);
		}

		public static ApplyPatchResult failure(String message) {
			return new ApplyPatchResult(false, message);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}
	}

	private Map<String, MultiStateTextFileChange> pendingFileChanges = new HashMap<>();
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

		try {
			String fullPath = file.getFullPath().toString(); // Use full path as key
			MultiStateTextFileChange mstfcForFile = pendingFileChanges.get(fullPath);
			boolean isNewMstfc = false;

			if (mstfcForFile == null) {
				mstfcForFile = new MultiStateTextFileChange("Patched changes for " + fileName, file);
				if (file.getFileExtension() != null) {
					mstfcForFile.setTextType(file.getFileExtension());
				} else {
					mstfcForFile.setTextType("txt"); // Default
				}
				isNewMstfc = true;
			}
			IDocument document = mstfcForFile.getCurrentDocument(new NullProgressMonitor());

			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			List<String> originalDocLines = Arrays.asList(document.get().split("\\R", -1));
			List<String> patchLines = Arrays.asList(patchString.split("\\R", -1));

			Patch<String> patch;
			try {
				patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
			} catch (Exception e) {
				Log.logError("Failed to parse unified diff: " + e.getMessage(), e);
				return ApplyPatchResult.failure("Failed to parse unified diff: " + e.getMessage());
			}

			if (patch.getDeltas().isEmpty()) {
				Log.logError("Empty diff for file: " + fileName);
				return ApplyPatchResult.failure(
						"The parser returned an empty patch. This is probably due to the patch_content not being a valid unified diff.  \n"
								+ "Here's an example:\n" + "```diff\n" + "--- /path/to/File.java\n"
								+ "+++ /path/to/File.java\n" + "@@ -123,1 +123,2 @@\n" + " some text\n" + " \n"
								+ "-foo\n" + "+bar\n" + "+baz\n" + "```\n");
			}

			// Hack: The difflib code expects 0-based line numbers, while AI models (and
			// e.g. the diff -u tool) tend to use 1-based line numbers.
			// So we add a blank line at the start of the originalDocLines, apply the patch,
			// and then remove the blank line again.
			List<String> tmp = new ArrayList<String>(originalDocLines.size() + 1);
			tmp.add("");
			tmp.addAll(originalDocLines);
			originalDocLines = tmp;

			// Validate the patch by trying to apply it (in memory)
			List<String> patchedDocLines = new ArrayList<>();
			ApplyPatchResult applyPatchResult = attemptApplyPatch(originalDocLines, patch, patchedDocLines);

			if (applyPatchResult.isSuccess()) {
				// Hack: The difflib code expects 0-based line numbers, while AI models (and
				// e.g. the diff -u tool) tend to use 1-based line numbers.
				// So we add a blank line at the start of the originalDocLines, apply the patch,
				// and then remove the blank line again.
				if (StringUtils.isBlank(originalDocLines.get(0)) && StringUtils.isBlank(patchedDocLines.get(0))) {
					originalDocLines.remove(0);
					patchedDocLines.remove(0);
				}

				TextFileChange change = new TextFileChange("Patch", file);
				change.setEdit(new ReplaceEdit(0, document.getLength(), String.join(lineDelimiter, patchedDocLines)));
				mstfcForFile.addChange(change);

				// NEW: Generate preview using the MultiStateTextFileChange
				String previewContentString;
				List<String> previewLines;
				try {
					IDocument previewDocument = mstfcForFile.getPreviewDocument(new NullProgressMonitor());
					previewContentString = previewDocument.get();
					previewLines = Arrays.asList(previewContentString.split("\\R", -1));
				} catch (CoreException e) {
					Log.logError("Failed to generate preview using MultiStateTextFileChange for " + fileName + ": "
							+ e.getMessage(), e);
					return ApplyPatchResult.failure("Error generating preview for " + fileName + ": " + e.getMessage());
				}

				// The current result is only a dummy.
				// MODIFIED: Call generateChangedLinesReport with originalDocLines and
				// previewLines
				String changedLinesReport = generateChangedLinesReport(fileName, originalDocLines, previewLines,
						lineDelimiter);

				applyPatchResult = ApplyPatchResult
						.success("The patch was successfully validated. A MultiStateTextFileChange has been prepared.\n"
								+ changedLinesReport);

				// MODIFIED: Use the counter and manage the map of pendingFileChanges
				if (isNewMstfc) {
					pendingFileChanges.put(fullPath, mstfcForFile);
					Log.logInfo("Created and queued MultiStateTextFileChange for file: " + fullPath);
				} else {
					// mstfcForFile was retrieved and modified in place, already in map.
					Log.logInfo("Added changes to existing MultiStateTextFileChange for file: " + fullPath);
				}
			}
			return applyPatchResult;
		} catch (Exception e) { // Catch any other unexpected errors
			Log.logError("Unexpected error processing patch for file " + fileName + ": " + e.getMessage(), e);
			return ApplyPatchResult.failure("Unexpected error processing patch: " + e.getMessage());
		} finally {
			resourceAccess.disconnectAllDocuments(tempDocMap);
		}
	}

	private String generateChangedLinesReport(String fileName, List<String> originalDocLines,
			List<String> patchedDocLines, String lineDelimiter) {
		Patch<String> effectiveChange = DiffUtils.diff(originalDocLines, patchedDocLines);

		List<AbstractDelta<String>> deltas = new java.util.ArrayList<>(effectiveChange.getDeltas());

		String prefix = "Here are the affected portions of the file after the patch is applied:\n";
		if (deltas.isEmpty()) {
			return prefix;
		}

		deltas.sort(java.util.Comparator.comparingInt(d -> d.getTarget().getPosition()));

		List<int[]> initialHunks = new java.util.ArrayList<>();
		for (AbstractDelta<String> delta : deltas) {
			Chunk<String> target = delta.getTarget();
			int contextSize = (int) Math.max(3, target.getLines().size() / 10); // Original context logic
			int targetStartInPatched = target.getPosition(); // 0-indexed
			int targetEndInPatched = targetStartInPatched + target.getLines().size();

			int hunkStart0idx = Math.max(0, targetStartInPatched - contextSize);
			int hunkEnd0idxExclusive = Math.min(patchedDocLines.size(), targetEndInPatched + contextSize);

			if (hunkStart0idx < hunkEnd0idxExclusive) { // Ensure hunk has content
				initialHunks.add(new int[] { hunkStart0idx, hunkEnd0idxExclusive });
			}
		}

		if (initialHunks.isEmpty()) {
			return prefix;
		}

		List<int[]> mergedHunks = new java.util.ArrayList<>();
		mergedHunks.add(initialHunks.get(0));

		for (int i = 1; i < initialHunks.size(); i++) {
			int[] currentHunk = initialHunks.get(i);
			int[] lastMergedHunk = mergedHunks.get(mergedHunks.size() - 1);

			if (currentHunk[0] <= lastMergedHunk[1]) { // Current hunk overlaps or is adjacent to the last merged hunk
				lastMergedHunk[1] = Math.max(lastMergedHunk[1], currentHunk[1]); // Extend the last merged hunk
			} else {
				mergedHunks.add(currentHunk); // Add as a new distinct hunk
			}
		}

		List<MessageContext> messageContexts = new java.util.ArrayList<>();
		for (int[] hunk : mergedHunks) {
			int start0idx = hunk[0];
			int end0idxExclusive = hunk[1];

			List<String> linesWithContext = patchedDocLines.subList(start0idx, end0idxExclusive);
			int messageStartLine = start0idx + 1; // Convert 0-indexed to 1-indexed for MessageContext

			// Assuming MessageContext constructor: (fileName, startLine_1idx_inclusive,
			// endLine_1idx_exclusive, content)
			// endLine_1idx_exclusive = end0idxExclusive + 1
			MessageContext mc = new MessageContext(fileName, messageStartLine, end0idxExclusive + 1,
					String.join(lineDelimiter, linesWithContext));
			messageContexts.add(mc);
		}

		String changes = messageContexts.stream().map(mc -> mc.compile(true)) // mc.compile(true) generates the
				// formatted hunk string
				.collect(java.util.stream.Collectors.joining("\n")); // Join hunks with a newline
		return prefix + changes;
	}

	private ApplyPatchResult attemptApplyPatch(List<String> originalDocLines, Patch<String> patch,
			List<String> patchedDocLines) {
		PatchMethod[] patchMethods = new PatchMethod[] { () -> patch.applyTo(originalDocLines),
				() -> patch.applyFuzzy(originalDocLines, 1), () -> patch.applyFuzzy(originalDocLines, 3),
				() -> patch.applyFuzzy(originalDocLines, 10), () -> patch.applyFuzzy(originalDocLines, 50),
				() -> patch.applyFuzzy(originalDocLines, 100) };
		for (int i = 0; i < patchMethods.length; i++) {
			PatchMethod patchMethod = patchMethods[i];
			try {
				patchedDocLines.addAll(patchMethod.apply());
				return ApplyPatchResult.success("dummy");
			} catch (IndexOutOfBoundsException e) {
				if (i < patchMethods.length - 1) {
					continue;
				} else {
					Log.logError("Patch validation failed: unified diff format expected.", e);
					return ApplyPatchResult.failure("Patch validation failed: unified diff format expected.  \n"
							+ "This usually happens if the `@@` line has trailing text, e.g. `@@ -139,4 +139,10 @@ public static void`.");
				}
			} catch (PatchFailedException e) {
				if (i < patchMethods.length - 1) {
					continue;
				} else {
					Log.logError("Patch validation failed for file : " + e.getMessage(), e);
					return ApplyPatchResult.failure("Patch cannot be applied: " + e.getMessage());
				}
			}
		}
		return ApplyPatchResult.failure("Patch cannot be applied: no more retries.");
	}

	// MODIFIED: Updated field name
	public void clearChanges() {
		int count = pendingFileChanges.size();
		pendingFileChanges.clear();
		Log.logInfo("Cleared " + count + " pending changes.");
	}

	// MODIFIED: Updated field name
	public int getPendingChangeCount() {
		return pendingFileChanges.size();
	}

	public void applyPendingChanges() {
		// MODIFIED: Updated field name
		if (pendingFileChanges.isEmpty()) {
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
		// MODIFIED: Simplified logic to use pendingFileChanges directly
		if (pendingFileChanges.isEmpty()) {
			Log.logInfo("No MultiStateTextFileChanges to apply.");
			return;
		}

		CompositeChange rootChange = new CompositeChange("Apply Patched Changes");

		// NEW LOGIC: Iterate over the prepared MultiStateTextFileChange objects
		for (MultiStateTextFileChange mstfc : pendingFileChanges.values()) {
			rootChange.add(mstfc);
		}

		// The rest of the method (checking rootChange.getChildren(), creating
		// Refactoring,
		// and Display.asyncExec) remains largely the same.

		if (rootChange.getChildren().length == 0) { // This getChildren() is on CompositeChange, which is correct.
			Log.logInfo("No valid patch changes (MSTFCs) could be prepared for refactoring.");
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
				return rootChange;
			}
		};

		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					Log.logError("Cannot apply patch changes: No active workbench window.");
					// It's important to clear changes even if the wizard doesn't run
					pendingFileChanges.clear(); // Use the new field name
					return;
				}
				RefactoringWizard wizard = new RefactoringWizard(refactoring,
						RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
					@Override
					protected void addUserInputPages() {
						/* None needed */ }
				};
				RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
				operation.run(window.getShell(), "Preview Patched Code Changes");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Log.logError("Patch refactoring wizard interrupted: " + e.getMessage(), e);
			} catch (Exception e) {
				Log.logError("Failed to open or run patch refactoring wizard: " + e.getMessage(), e);
			} finally {
				pendingFileChanges.clear(); // Clear after wizard is handled or if an error occurs
			}
		});
	}

	private String typeOf(TextEdit edit) {
		String type;
		if (edit instanceof InsertEdit) {
			type = "Insert";
		} else if (edit instanceof DeleteEdit) {
			type = "Deletion";
		} else {
			type = "Replacement";
		}
		return type;
	}

	/**
	 * Creates a ReplaceEdit for a given ChangeOperation derived from a patch delta.
	 * 
	 * @param lineDelimiter
	 */
	private TextEdit createEditForOperation(IDocument document, AbstractDelta<String> delta, String lineDelimiter)
			throws BadLocationException, IllegalArgumentException {
		int opStartLine1Based = delta.getSource().getPosition() + 1;
		// This is (opStartLine1Based - 1) for pure inserts
		int opEndLine1Based = delta.getSource().getPosition() + delta.getSource().getLines().size();
		String replacementText = String.join(lineDelimiter, delta.getTarget().getLines());

		// Handle pure insert case (where opEndLine1Based < opStartLine1Based)
		if (DeltaType.INSERT.equals(delta.getType())) {
			int insertLine0Based = opStartLine1Based - 1;

			replacementText = replacementText + lineDelimiter;

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
			// For inserts, the replacement text should not include the line delimiter if
			// it's already part of the document structure
			// However, if inserting at EOF, or into an empty document, it might need one.
			// The current logic in createChangeOperations passes the raw delta target
			// lines.
			// If the delta target lines already include the delimiter, this is fine.
			// If not, and it's a new line, it might need op.getLineDelimiter() appended.
			// For now, keeping it as is, assuming replacementText from delta is correct.
			return new InsertEdit(offset, replacementText);
		} else if (DeltaType.DELETE.equals(delta.getType())) {
			// This is a pure deletion
			int startLine0Based = opStartLine1Based - 1;
			int endLine0Based = opEndLine1Based - 1;

			if (opStartLine1Based < 1 || opEndLine1Based > document.getNumberOfLines()) { // Adjusted condition for
																							// deletion
				throw new BadLocationException(
						"Invalid line number for deletion: " + opStartLine1Based + ":" + opEndLine1Based + ".");
			}

			IRegion startInfo = document.getLineInformation(startLine0Based);
			int offset = startInfo.getOffset();
			int length;

			// Calculate length to include the line delimiter of the last deleted line
			// unless it's the very last line of the document.
			if (endLine0Based < document.getNumberOfLines() - 1) {
				// If not the last line, delete up to the start of the next line
				IRegion nextLineInfo = document.getLineInformation(endLine0Based + 1);
				length = nextLineInfo.getOffset() - offset;
			} else {
				// If it's the last line(s) of the document, delete up to the end of the last
				// line
				IRegion endInfo = document.getLineInformation(endLine0Based);
				length = (endInfo.getOffset() + endInfo.getLength()) - offset;
			}

			return new DeleteEdit(offset, length);
		} else {
			// Modification (replacement)
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
			// For replacement, original text spans from start of startLine to end of
			// endLine
			int length = (endLineInfo.getOffset() + endLineInfo.getLength()) - offset;

			return new ReplaceEdit(offset, length, replacementText);
		}
	}

	private static interface PatchMethod {
		List<String> apply() throws PatchFailedException;
	}
}
