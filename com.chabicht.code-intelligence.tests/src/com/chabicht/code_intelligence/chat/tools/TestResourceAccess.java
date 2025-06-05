package com.chabicht.code_intelligence.chat.tools;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;

public class TestResourceAccess implements IResourceAccess {

	private Map<String, String> files;

	public TestResourceAccess(Map<String, String> files) {
		this.files = files;
	}

	@Override
	public IFile findFileByNameBestEffort(String fileName) {
		if (files.containsKey(fileName)) {
			return new TestFile(fileName, files.get(fileName));
		}

		return null;
	}

	@Override
	public IDocument getDocumentAndConnect(IFile file, Map<IFile, IDocument> documentMap) {
		String fileName = file.getName();
		if (files.containsKey(fileName)) {
			return new TestDocument(fileName, files.get(fileName));
		}

		return null;
	}

	@Override
	public void disconnectAllDocuments(Map<IFile, IDocument> documentMap) {
	}

	@Override
	public CreateFileTool.CreateFileResult createFileInWorkspace(String filePath, String content) {
		if (files.containsKey(filePath)) {
			return CreateFileTool.CreateFileResult.failure("File already exists: " + filePath, filePath);
		}
		// Simulate file creation for testing purposes
		files.put(filePath, content);
		return new CreateFileTool.CreateFileResult(true, "File created successfully (test): " + filePath, filePath);
	}
}
