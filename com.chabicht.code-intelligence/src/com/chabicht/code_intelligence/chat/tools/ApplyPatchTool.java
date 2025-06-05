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
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.util.GsonUtil; // If you still want to log JSON
import com.chabicht.code_intelligence.util.Log; // Your Log utility
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
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
		private final String lineDelimiter;

		public ChangeOperation(String fileName, String lineDelimiter, int startLine, int endLine,
				String originalText,
				String replacement) {
			this.fileName = fileName;
			this.startLine = startLine;
			this.endLine = endLine;
			this.originalText = originalText; // Text from the patch's original hunk
			this.replacement = replacement; // Text from the patch's revised hunk
			this.lineDelimiter = lineDelimiter;
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

		public String getLineDelimiter() {
			return lineDelimiter;
		}
	}

	public static class ApplyPatchResult {
		private final boolean success;
		private final String message;
		private final List<ChangeOperation> operations; // List of operations created

		private ApplyPatchResult(boolean success, String message,
				List<ChangeOperation> operations) {
			this.success = success;
			this.message = message;
			this.operations = operations != null ? operations : new ArrayList<>();
		}

		public static ApplyPatchResult success(String message,
				List<ChangeOperation> operations) {
			return new ApplyPatchResult(true, message, operations);
		}

		public static ApplyPatchResult failure(String message) {
			return new ApplyPatchResult(false, message, null);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
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

				// The current result is only a dummy.
				String changedLinesReport = generateChangedLinesReport(fileName, originalDocLines, patchedDocLines,
						lineDelimiter);
				applyPatchResult = ApplyPatchResult.success(
						"The patch was successfully validated and a change operation was queued to apply the patch after user review.\n"
								+ changedLinesReport,
						pendingChanges);

				List<ChangeOperation> ops = createChangeOperations(document, fileName, originalDocLines,
						patchedDocLines,
						lineDelimiter);
				newOperations.addAll(ops);

				pendingChanges.addAll(newOperations);
				Log.logInfo("Validated and added " + newOperations.size() + " change operations from patch for file: "
						+ fileName);
			}
			return applyPatchResult;
		} catch (Exception e) { // Catch any other unexpected errors
			Log.logError("Unexpected error processing patch for file " + fileName + ": " + e.getMessage(), e);
			return ApplyPatchResult.failure("Unexpected error processing patch: " + e.getMessage());
		} finally {
			resourceAccess.disconnectAllDocuments(tempDocMap);
		}
	}

	private List<ChangeOperation> createChangeOperations(IDocument doc, String fileName, List<String> originalDocLines,
			List<String> patchedDocLines, String lineDelimiter) {
		ArrayList<ChangeOperation> res = new ArrayList<>();

		Patch<String> patch = DiffUtils.diff(originalDocLines, patchedDocLines);
		for (AbstractDelta<String> delta : patch.getDeltas()) {
			int startLine = delta.getSource().getPosition() + 1;
			int endLine = startLine + delta.getSource().size() - 1;
			ChangeOperation op = new ChangeOperation(fileName, lineDelimiter, startLine, endLine,
					String.join(lineDelimiter, delta.getSource().getLines()),
					String.join(lineDelimiter, delta.getTarget().getLines()));
			res.add(op);
		}

		return res;
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
				() -> patch.applyFuzzy(originalDocLines, 1),
				() -> patch.applyFuzzy(originalDocLines, 3),
				() -> patch.applyFuzzy(originalDocLines, 10), () -> patch.applyFuzzy(originalDocLines, 50),
				() -> patch.applyFuzzy(originalDocLines, 100) };
		for (int i = 0; i < patchMethods.length; i++) {
			PatchMethod patchMethod = patchMethods[i];
			try {
				patchedDocLines.addAll(patchMethod.apply());
				return ApplyPatchResult.success("dummy", null);
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

		CompositeChange rootChange = new CompositeChange("Apply Patched Changes");
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

				MultiStateTextFileChange multiStateTextFileChange = new MultiStateTextFileChange(
						"Patched changes in " + fileName, file);
				IDocument doc = multiStateTextFileChange.getCurrentDocument(new NullProgressMonitor());
				for (ChangeOperation op : fileChanges) {
					try {
						TextEdit edit = createEditForOperation(document, op);
						String type = edit instanceof InsertEdit ? "Insert" : "Replacement";

						TextFileChange fileChange = new TextFileChange(
								String.format("%s at Line %s-%s", type, op.getStartLine(), op.getEndLine()), file);
						fileChange.setEdit(edit);

						multiStateTextFileChange.addChange(fileChange);
					} catch (BadLocationException | IllegalArgumentException | MalformedTreeException e) {
						Gson gson = GsonUtil.createGson();
						String json = gson.toJson(op);
						Log.logError("Failed to create edit from patch operation for " + fileName + " (op lines "
								+ op.getStartLine() + "-" + op.getEndLine() + "): " + e.getMessage() + "\nJSON Op:\n"
								+ json, e);
					}
				}
				rootChange.add(multiStateTextFileChange);
			}

			if (rootChange.getChildren().length == 0) {
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
					return rootChange;
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
	private TextEdit createEditForOperation(IDocument document, ChangeOperation op)
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
			return new InsertEdit(offset, replacementText + op.getLineDelimiter());
		} else if (op.getReplacement().length() == 0) {
			int startLine0Based = opStartLine1Based - 1;
			int endLine0Based = opEndLine1Based - 1;

			if (opStartLine1Based < 1 || opEndLine1Based > document.getNumberOfLines() + 1) {
				throw new BadLocationException(
						"Invalid line number for deletion: " + opStartLine1Based + ":" + opEndLine1Based + ".");
			}

			// Delete whole lines except end is on the last line.
			IRegion startInfo = document.getLineInformation(startLine0Based);
			int length;
			if (endLine0Based < document.getNumberOfLines() - 1) {
				IRegion endInfo = document.getLineInformation(endLine0Based + 1);
				length = endInfo.getOffset() - startInfo.getOffset();
			} else {
				IRegion endInfo = document.getLineInformation(endLine0Based);
				length = endInfo.getOffset() - startInfo.getOffset() + endInfo.getLength();
			}

			int offset = startInfo.getOffset();
			return new DeleteEdit(offset, length);
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

	private static interface PatchMethod {
		List<String> apply() throws PatchFailedException;
	}
}
