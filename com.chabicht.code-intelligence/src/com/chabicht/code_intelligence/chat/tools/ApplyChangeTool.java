package com.chabicht.code_intelligence.chat.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.patch.ApplyPatchOperation;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.Activator;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils; // Import needed
import com.github.difflib.patch.Patch;

public class ApplyChangeTool {
	public void invoke(String fileName, String location, String originalText, String replacement) {
		// 1. Find the target file resource
		IFile resource = findFileByNameBestEffort(fileName);
		if (resource == null) {
			Activator.logError("Could not find a unique file resource for name: " + fileName);
			// Consider throwing an exception or providing user feedback
			return;
		}

		try {
			// 2. Read original file content
			List<String> originalLines = readLines(resource);

			// 3. Parse location and construct revised content
			// Currently supports only line format "l<start>:<end>"
			int startLine, endLine;
			if (location.startsWith("l")) {
				String[] parts = location.substring(1).split(":");
				if (parts.length != 2) {
					throw new IllegalArgumentException("Invalid line location format: " + location);
				}
				startLine = Integer.parseInt(parts[0]); // 1-based
				endLine = Integer.parseInt(parts[1]); // 1-based
			} else if (location.startsWith("c")) {
				// TODO: Implement character offset to line mapping if needed
				Activator.logError("Character offset location not yet supported: " + location);
				throw new UnsupportedOperationException("Character offset location not yet supported: " + location);
			} else {
				throw new IllegalArgumentException("Unknown location format: " + location);
			}

			// Convert to 0-based index
			int startIdx = startLine - 1;
			int endIdx = endLine - 1;

			if (startIdx < 0 || endIdx >= originalLines.size() || startIdx > endIdx) {
				throw new IndexOutOfBoundsException("Location [" + location + "] is outside the bounds of the file "
						+ fileName + " (lines: " + originalLines.size() + ")");
			}

			// Verify originalText matches the content at the location (optional but
			// recommended)
			// String actualOriginal = String.join("\n", originalLines.subList(startIdx,
			// endIdx + 1));
			// if (!actualOriginal.equals(originalText)) {
			// Activator.logWarn("Original text provided does not match file content at
			// location " + location);
			// // Decide how to handle mismatch: proceed, abort, ask user?
			// }

			List<String> revisedLines = new ArrayList<>();
			// Add lines before the change
			revisedLines.addAll(originalLines.subList(0, startIdx));
			// Add the replacement lines (split replacement text by newline)
			// Use "\\R" to split by any standard newline sequence
			revisedLines.addAll(Arrays.asList(replacement.split("\\R")));
			// Add lines after the change
			revisedLines.addAll(originalLines.subList(endIdx + 1, originalLines.size()));

			// 4. Generate Patch object
			Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);

			// 5. Generate Unified Diff text (use 3 lines of context)
			List<String> unifiedDiffLines = UnifiedDiffUtils.generateUnifiedDiff(resource.getName(), // Original file
																										// name
					resource.getName(), // Revised file name (can be same)
					originalLines, patch, 3); // Context lines

			if (unifiedDiffLines.isEmpty() && !originalLines.equals(revisedLines)) {
				// Handle cases where diff lib might return empty for certain changes
				// or if the only changes are whitespace/newlines depending on diff algorithm.
				// For now, log a warning. A more robust solution might be needed.
				Activator.logInfo("Generated unified diff is empty, but content has changed for " + fileName);
				// You might need to manually construct a minimal diff header if
				// ApplyPatchOperation requires it
				// even for seemingly empty diffs if the content did change.
				// Example minimal header (adjust line numbers if possible):
				// unifiedDiffLines.add("--- " + resource.getName());
				// unifiedDiffLines.add("+++ " + resource.getName());
				// unifiedDiffLines.add("@@ -1," + originalLines.size() + " +1," +
				// revisedLines.size() + " @@");
				// return; // Or proceed cautiously
			} else if (unifiedDiffLines.isEmpty() && originalLines.equals(revisedLines)) {
				Activator.logInfo("No changes detected for file " + fileName);
				return; // No need to apply patch
			}

			String unifiedDiffContent = String.join("\n", unifiedDiffLines);

			// 6. Create IStorage for the patch
			IStorage patchStorage = new InMemoryStorage("change.patch", unifiedDiffContent);

			// 7. Instantiate ApplyPatchOperation
			// Ensure this runs on the UI thread
			Display.getDefault().asyncExec(() -> {
				IWorkbenchPart activePart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
						.getActivePart();
				// Use a default CompareConfiguration for now
				CompareConfiguration config = new CompareConfiguration();
				// You could customize config labels here if needed, e.g.:
				// config.setLeftLabel("Original Content");
				// config.setRightLabel("Proposed Changes");

				ApplyPatchOperation operation = new ApplyPatchOperation(activePart, patchStorage, resource, config);

				// 8. Execute the operation (opens the Apply Patch wizard)
				operation.openWizard(); // or operation.run()
			});

		} catch (CoreException | IOException | IllegalArgumentException | IndexOutOfBoundsException
				| UnsupportedOperationException e) {
			Activator.logError("Failed to prepare or apply patch for " + fileName + ": " + e.getMessage(), e);
			// Provide feedback to the user (e.g., dialog)
		}
	}

	// Helper method to read file lines
	private List<String> readLines(IFile file) throws CoreException, IOException {
		List<String> lines = new ArrayList<>();
		try (InputStream contentStream = file.getContents();
				BufferedReader reader = new BufferedReader(new InputStreamReader(contentStream, file.getCharset()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	// findFileByNameBestEffort method remains the same
	private IFile findFileByNameBestEffort(String fileName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final List<IFile> foundFiles = new ArrayList<>();

		try {
			root.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource.getType() == IResource.FILE && resource.getName().equals(fileName)) {
						foundFiles.add((IFile) resource);
						// Optional: Stop searching after first match if desired
						// return false;
					}
					// Continue searching in subfolders
					return true;
				}
			});
		} catch (CoreException e) {
			Activator.logError("Error searching for file: " + fileName, e);
			return null;
		}

		// Check if exactly one file was found
		if (foundFiles.size() == 1) {
			return foundFiles.get(0);
		} else if (foundFiles.isEmpty()) {
			Activator.logInfo("No file found with name: " + fileName);
			return null;
		} else {
			// Handle multiple files found (e.g., log warning, return first, or null)
			Activator.logInfo("Multiple files found with name: " + fileName + ". Using the first one found: "
					+ foundFiles.get(0).getFullPath());
			return foundFiles.get(0); // Or return null if ambiguity is unacceptable
		}
	}

	private static class InMemoryStorage extends PlatformObject implements IStorage {
		private final String name;
		private final String content;
		private final byte[] contentBytes;

		public InMemoryStorage(String name, String content) {
			this.name = name;
			this.content = content;
			// Ensure consistent encoding, UTF-8 is standard for patches
			this.contentBytes = content.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public InputStream getContents() throws CoreException {
			return new ByteArrayInputStream(contentBytes);
		}

		@Override
		public IPath getFullPath() {
			// Not applicable for in-memory storage
			return null;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public boolean isReadOnly() {
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getAdapter(Class<T> adapter) {
			// Basic implementation, might need refinement depending on usage
			return super.getAdapter(adapter);
		}

		public String getContent() {
			return content;
		}
	}

}
