package com.chabicht.code_intelligence.chat.tools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChangeGroup;
import org.eclipse.ltk.core.refactoring.resource.DeleteResourceChange;

import com.chabicht.code_intelligence.util.Log;

public class CreateFileTool {

	public CreateFileTool() {
	}

	public static class CreateFilePreparationResult {
		private final boolean success;
		private final String message;
		private final Change change; // The prepared CreateFileChange

		private CreateFilePreparationResult(boolean success, String message, Change change) {
			this.success = success;
			this.message = message;
			this.change = change;
		}

		public static CreateFilePreparationResult success(String message, Change change) {
			return new CreateFilePreparationResult(true, message, change);
		}

		public static CreateFilePreparationResult failure(String message) {
			return new CreateFilePreparationResult(false, message, null);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public Change getChange() {
			return change;
		}
	}

	/**
	 * Creates a new file with the specified path and content. This method relies on
	 * an underlying method in IResourceAccess, for example:
	 * {@code public CreateFileResult createFileInWorkspace(String filePath, String content);}
	 * This underlying method is responsible for: 1. Checking if the file at
	 * {@code filePath} already exists. If so, return failure. 2. Ensuring parent
	 * directories exist (creating them if necessary). 3. Creating the new file and
	 * writing {@code content} to it. 4. Returning a {@code CreateFileResult}
	 * indicating success or failure.
	 *
	 * @param filePath The path for the new file.
	 * @param content  The content for the new file.
	 * @return A {@code CreateFileResult} indicating the outcome.
	 */
	public CreateFilePreparationResult prepareCreateFileChange(String filePath, String content) {
		if (filePath == null || filePath.trim().isEmpty()) {
			return CreateFilePreparationResult.failure("File path cannot be null or empty.");
		}
		if (content == null) {
			return CreateFilePreparationResult
					.failure("Content cannot be null. Provide an empty string for an empty file.");
		}

		try {
			IPath path = new Path(filePath);
			if (path.isAbsolute()) {
				path = path.makeRelative();
			}
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			if (file.exists()) {
				return CreateFilePreparationResult.failure("File already exists: " + filePath);
			}

			CustomCreateFileChange change = new CustomCreateFileChange(file.getFullPath(), content, "UTF-8");

			Log.logInfo("Prepared CustomCreateFileChange for: " + filePath);
			return CreateFilePreparationResult
					.success("File creation for '" + filePath + "' has been queued for review.", change);
		} catch (Exception e) {
			Log.logError("Error preparing CreateFileChange for " + filePath, e);
			return CreateFilePreparationResult
					.failure("Error preparing file creation for " + filePath + ": " + e.getMessage());
		}
	}

	public static class CustomCreateFileChange extends TextEditBasedChange {
		private final IPath filePath;
		private final String content;
		private final String encoding;

		public CustomCreateFileChange(IPath filePath, String content, String encoding) {
			super("Create file: " + filePath.lastSegment());
			this.filePath = filePath;
			this.content = content;
			this.encoding = encoding;

			String fileExtension = filePath.getFileExtension();
			if (fileExtension != null) {
				setTextType(fileExtension);
			}
		}

		@Override
		public Object getModifiedElement() {
			return ResourcesPlugin.getWorkspace().getRoot().getFile(filePath);
		}

		@Override
		public void initializeValidationData(IProgressMonitor pm) {
			// Nothing to initialize
		}

		@Override
		public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
			IFile file = (IFile) getModifiedElement();
			if (file.exists()) {
				return RefactoringStatus.createFatalErrorStatus("File already exists: " + filePath.toString());
			}
			return new RefactoringStatus();
		}

		@Override
		public Change perform(IProgressMonitor pm) throws CoreException {
			final IFile file = (IFile) getModifiedElement();
			pm.beginTask("Creating file...", 2);
			try {
				ensurePathExists(file.getParent(), pm);
				InputStream stream = new ByteArrayInputStream(content.getBytes(encoding));
				file.create(stream, true, SubMonitor.convert(pm));

				// The undo change is to delete the file that was just created.
				return new DeleteResourceChange(file.getFullPath(), true);
			} catch (java.io.UnsupportedEncodingException e) {
				throw new CoreException(new Status(Status.ERROR, "com.chabicht.code_intelligence",
						"Unsupported encoding: " + encoding, e));
			} finally {
				pm.done();
			}
		}

		private void ensurePathExists(IContainer container, IProgressMonitor pm) throws CoreException {
			if (container == null || container.exists() || !(container instanceof IFolder)) {
				return;
			}
			ensurePathExists(container.getParent(), pm);
			((IFolder) container).create(true, true, SubMonitor.convert(pm));
		}

		@Override
		public void dispose() {
			// Nothing to dispose
		}

		@Override
		public String getCurrentContent(IProgressMonitor pm) throws CoreException {
			return ""; // File doesn't exist yet.
		}

		@Override
		public String getCurrentContent(IRegion region, boolean expandRegionToFullLine, int surroundingLines,
				IProgressMonitor pm) throws CoreException {
			return "";
		}

		@Override
		public String getPreviewContent(IProgressMonitor pm) throws CoreException {
			return this.content;
		}

		@Override
		public String getPreviewContent(TextEditBasedChangeGroup[] changeGroups, IRegion region,
				boolean expandRegionToFullLine, int surroundingLines, IProgressMonitor pm) throws CoreException {
			return getPreviewContent(pm);
		}
	}
}
