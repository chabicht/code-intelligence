package com.chabicht.code_intelligence.chat.tools;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;

public interface IResourceAccess {
	/**
	 * Finds the IFile resource corresponding to the given file name. Provides basic
	 * handling for ambiguity (multiple files with the same name).
	 *
	 * @param fileName The simple name of the file (e.g., "MyClass.java").
	 * @return The IFile if found uniquely, or a best guess, or null.
	 */
	IFile findFileByNameBestEffort(String fileName);

	/**
	 * Gets the IDocument for a file, managing the connection via
	 * TextFileBufferManager. Stores the connected document in the provided map.
	 *
	 * @param file        The file to get the document for.
	 * @param documentMap A map to store the connected document and its buffer.
	 * @return The IDocument, or null if connection fails.
	 */
	IDocument getDocumentAndConnect(IFile file, Map<IFile, IDocument> documentMap);

	/**
	 * Disconnects all documents managed in the provided map.
	 *
	 * @param documentMap The map containing files and their connected documents.
	 */
	void disconnectAllDocuments(Map<IFile, IDocument> documentMap);

	/**
	 * Attempts to create a new file at the specified path with the given content.
	 *
	 * @param filePath The complete path (relative to workspace root) for the new file.
	 * @param content  The content to write into the new file.
	 * @return A {@link CreateFileTool.CreateFileResult} indicating success or failure.
	 */
	CreateFileResult createFileInWorkspace(String filePath, String content);

	class CreateFileResult {
		private final boolean success;
		private final String message;
		private final String filePath;

		public CreateFileResult(boolean success, String message, String filePath) {
			this.success = success;
			this.message = message;
			this.filePath = filePath;
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public String getFilePath() {
			return filePath;
		}

		public static CreateFileResult failure(String message) {
			return new CreateFileResult(false, message, null);
		}

		public static CreateFileResult failure(String message, String filePath) {
			return new CreateFileResult(false, message, filePath);
		}
	}
}
