package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.ReplaceEdit;

import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.util.Log;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
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
	public ToolChangePreparationResult preparePatchChange(String fileName, String patchString) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return ToolChangePreparationResult.failure("File name cannot be null or empty");
		}
		if (patchString == null || patchString.trim().isEmpty()) {
			return ToolChangePreparationResult.failure("Patch string cannot be null or empty");
		}

		IFile file = resourceAccess.findFileByNameBestEffort(fileName);
		if (file == null) {
			return ToolChangePreparationResult.failure("File not found: " + fileName);
		}

		Map<IFile, IDocument> tempDocMap = new HashMap<>();
		try {
			IDocument document = resourceAccess.getDocumentAndConnect(file, tempDocMap);
			if (document == null) {
				return ToolChangePreparationResult.failure("Could not get document for file: " + fileName);
			}

			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			List<String> originalDocLines = Arrays.asList(document.get().split("\\R", -1));
			List<String> patchLines = Arrays.asList(patchString.split("\\R", -1));

			Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
			if (patch.getDeltas().isEmpty()) {
				return ToolChangePreparationResult.failure(
						"The parser returned an empty patch. This is probably due to the patch_content not being a valid unified diff.");
			}

			// 1-based line number hack for difflib
			List<String> tempOriginal = new ArrayList<>(originalDocLines);
			tempOriginal.add(0, "");

			List<String> patchedDocLinesAttempt = new ArrayList<>();
			ApplyPatchResult validationResult = attemptApplyPatch(tempOriginal, patch, patchedDocLinesAttempt);

			if (validationResult.isSuccess()) {
				// Remove the prepended blank line
				if (!patchedDocLinesAttempt.isEmpty() && StringUtils.isBlank(patchedDocLinesAttempt.get(0))) {
					patchedDocLinesAttempt.remove(0);
				}

				String patchedContent = String.join(lineDelimiter, patchedDocLinesAttempt);
				ReplaceEdit wholeDocumentReplaceEdit = new ReplaceEdit(0, document.getLength(), patchedContent);

				String changedLinesReport = generateChangedLinesReport(fileName, originalDocLines,
						patchedDocLinesAttempt, lineDelimiter);

				return ToolChangePreparationResult.success(
						"Patch validated and change prepared.\n" + changedLinesReport, file,
						java.util.Collections.singletonList(wholeDocumentReplaceEdit), changedLinesReport // For a
																											// patch,
																											// the
																											// report
																											// itself is
																											// the best
																											// "preview"
				);
			} else {
				return ToolChangePreparationResult.failure("Patch validation failed: " + validationResult.getMessage());
			}
		} catch (Exception e) {
			Log.logError("Unexpected error preparing patch for " + fileName, e);
			return ToolChangePreparationResult.failure("Unexpected error preparing patch: " + e.getMessage());
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

	private static interface PatchMethod {
		List<String> apply() throws PatchFailedException;
	}
}
