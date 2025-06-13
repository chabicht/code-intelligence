package com.chabicht.code_intelligence.chat.tools;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
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
	public CreateFileResult createFileInWorkspace(String filePath, String content) {
		if (files.containsKey(filePath)) {
			return CreateFileResult.failure("File already exists: " + filePath, filePath);
		}
		// Simulate file creation for testing purposes
		files.put(filePath, content);
		return new CreateFileResult(true, "File created successfully (test): " + filePath, filePath);
	}

	@Override
	public IFileHandle findFileHandleByName(String fileName) {
		// Check if file exists in our test data
		if (files.containsKey(fileName)) {
			// For testing, create a test file and wrap it in RealFileHandle
			TestFile testFile = new TestFile(fileName, files.get(fileName));
			return new RealFileHandle(testFile);
		}
		return null;
	}

	@Override
	public IDocument getDocumentForHandle(IFileHandle handle, Map<IFileHandle, IDocument> documentMap) {
		if (handle == null) {
			throw new IllegalArgumentException("File handle cannot be null");
		}

		if (handle.isVirtual()) {
			// Handle virtual files - get content directly
			VirtualFileHandle virtualHandle = (VirtualFileHandle) handle;
			IDocument doc = new Document(virtualHandle.getContent());
			documentMap.put(handle, doc);
			return doc;
		} else {
			// Handle real files - delegate to existing logic
			String fileName = handle.getName();
			if (files.containsKey(fileName)) {
				IDocument doc = new TestDocument(fileName, files.get(fileName));
				documentMap.put(handle, doc);
				return doc;
			}
			return null;
		}
	}
}
