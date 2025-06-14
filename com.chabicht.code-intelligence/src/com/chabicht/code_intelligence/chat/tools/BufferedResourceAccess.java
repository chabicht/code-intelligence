package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import com.chabicht.code_intelligence.util.Log;

/**
 * A buffering layer for IResourceAccess that applies pending changes in-memory
 * before returning results. This allows AI tools to see the effects of pending
 * changes before they are actually applied.
 */
public class BufferedResourceAccess implements IResourceAccess {

	private final IResourceAccess delegate;
	private final FunctionCallSession session;

	// Cache for documents with pending changes applied
	private final Map<String, IDocument> documentCache = new HashMap<>();

	// Cache for virtual file handles
	private final Map<String, VirtualFileHandle> virtualFileCache = new HashMap<>();

	/**
	 * Creates a new BufferedResourceAccess.
	 * 
	 * @param delegate The underlying IResourceAccess to delegate to
	 * @param session  The FunctionCallSession to get pending changes from
	 */
	public BufferedResourceAccess(IResourceAccess delegate, FunctionCallSession session) {
		if (delegate == null) {
			throw new IllegalArgumentException("Delegate cannot be null");
		}
		if (session == null) {
			throw new IllegalArgumentException("Session cannot be null");
		}
		this.delegate = delegate;
		this.session = session;
	}

	/**
	 * Clears all internal caches. Should be called when pending changes are applied
	 * or discarded.
	 */
	public void clearCaches() {
		documentCache.clear();
		virtualFileCache.clear();
		Log.logInfo("BufferedResourceAccess: Cleared all caches");
	}

	@Override
	public IFile findFileByNameBestEffort(String fileName) {
		// Check if this matches any pending create file
		String virtualPath = findVirtualFilePath(fileName);
		if (virtualPath != null) {
			// IFile can't represent virtual files, so return null
			// Callers should use findFileHandleByName instead
			Log.logInfo("BufferedResourceAccess: File '" + fileName
					+ "' is virtual, returning null from findFileByNameBestEffort");
			return null;
		}

		// Delegate to real implementation
		return delegate.findFileByNameBestEffort(fileName);
	}

	@Override
	public IFileHandle findFileHandleByName(String fileName) {
		// First check virtual file cache
		VirtualFileHandle cached = virtualFileCache.get(fileName);
		if (cached != null) {
			Log.logInfo("BufferedResourceAccess: Found '" + fileName + "' in virtual cache");
			return cached;
		}

		// Then check pending create files
		String virtualPath = findVirtualFilePath(fileName);
		if (virtualPath != null) {
			Change change = session.getPendingCreateFileChanges().get(virtualPath);
			if (change != null) {
				String content = extractContentFromChange(change);
				VirtualFileHandle handle = new VirtualFileHandle(virtualPath, content);
				virtualFileCache.put(virtualPath, handle);
				virtualFileCache.put(fileName, handle); // Also cache by simple name
				Log.logInfo("BufferedResourceAccess: Created virtual handle for '" + virtualPath + "'");
				return handle;
			}
		}

		// Finally check real files
		IFile realFile = delegate.findFileByNameBestEffort(fileName);
		return realFile != null ? new RealFileHandle(realFile) : null;
	}

	@Override
	public IDocument getDocumentAndConnect(IFile file, Map<IFile, IDocument> documentMap) {
		if (file == null) {
			return null;
		}

		String filePath = file.getFullPath().toString();

		// Check cache first
		IDocument cached = documentCache.get(filePath);
		if (cached != null) {
			Log.logInfo("BufferedResourceAccess: Using cached document for '" + filePath + "'");
			documentMap.put(file, cached);
			return cached;
		}

		// Get real document from delegate
		IDocument realDoc = delegate.getDocumentAndConnect(file, documentMap);
		if (realDoc == null) {
			return null;
		}

		// Apply pending changes
		IDocument modifiedDoc = applyPendingChanges(filePath, realDoc);

		// Cache if modified
		if (modifiedDoc != realDoc) {
			Log.logInfo("BufferedResourceAccess: Applied pending changes to '" + filePath + "'");
			documentCache.put(filePath, modifiedDoc);
			// Update the document map with modified document
			documentMap.put(file, modifiedDoc);
		}

		return modifiedDoc;
	}

	@Override
	public void disconnectAllDocuments(Map<IFile, IDocument> documentMap) {
		// Remove cached entries for files being disconnected
		for (IFile file : documentMap.keySet()) {
			String filePath = file.getFullPath().toString();
			documentCache.remove(filePath);
		}

		// Delegate to real implementation
		delegate.disconnectAllDocuments(documentMap);
	}

	@Override
	public CreateFileResult createFileInWorkspace(String filePath, String content) {
		// Check if file already exists virtually
		if (session.getPendingCreateFileChanges().containsKey(filePath)) {
			return CreateFileResult.failure("File already exists in pending changes: " + filePath, filePath);
		}

		// Delegate to real implementation
		return delegate.createFileInWorkspace(filePath, content);
	}

	@Override
	public IDocument getDocumentForHandle(IFileHandle handle, Map<IFileHandle, IDocument> documentMap) {
		if (handle == null) {
			throw new IllegalArgumentException("File handle cannot be null");
		}

		if (handle.isVirtual()) {
			// Handle virtual files
			VirtualFileHandle virtualHandle = (VirtualFileHandle) handle;
			org.eclipse.jface.text.Document doc = new org.eclipse.jface.text.Document(virtualHandle.getContent());
			documentMap.put(handle, doc);
			Log.logInfo(
					"BufferedResourceAccess: Created document for virtual file '" + virtualHandle.getFullPath() + "'");
			return doc;
		} else {
			// Handle real files
			RealFileHandle realHandle = (RealFileHandle) handle;
			Map<IFile, IDocument> fileDocMap = new HashMap<>();
			IDocument doc = getDocumentAndConnect(realHandle.getFile(), fileDocMap);

			// Transfer to handle map
			if (doc != null) {
				documentMap.put(handle, doc);
			}

			return doc;
		}
	}

	/**
	 * Finds a virtual file path that matches the given file name.
	 * 
	 * @param fileName The file name to search for (can be simple name or full path)
	 * @return The full virtual file path, or null if not found
	 */
	private String findVirtualFilePath(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return null;
		}

		Map<String, Change> pendingCreates = session.getPendingCreateFileChanges();

		// First try exact match
		if (pendingCreates.containsKey(fileName)) {
			return fileName;
		}

		// Then try matching by file name
		for (String virtualPath : pendingCreates.keySet()) {
			if (pathMatchesFileName(virtualPath, fileName)) {
				return virtualPath;
			}
		}

		return null;
	}

	/**
	 * Extracts content from a Change object (for CreateFileChange).
	 * 
	 * @param change The Change object
	 * @return The content string, or empty string if extraction fails
	 */
	private String extractContentFromChange(Change change) {
		if (change == null) {
			return "";
		}

		// First try direct access if it's our CustomCreateFileChange
		if (change instanceof com.chabicht.code_intelligence.chat.tools.CreateFileTool.CustomCreateFileChange) {
			com.chabicht.code_intelligence.chat.tools.CreateFileTool.CustomCreateFileChange createChange = (com.chabicht.code_intelligence.chat.tools.CreateFileTool.CustomCreateFileChange) change;
			return createChange.getContent();
		}

		// Fallback to reflection for other change types
		try {
			java.lang.reflect.Field contentField = change.getClass().getDeclaredField("content");
			contentField.setAccessible(true);
			Object content = contentField.get(change);
			if (content instanceof String) {
				return (String) content;
			}
		} catch (NoSuchFieldException e) {
			Log.logWarn("BufferedResourceAccess: No 'content' field found in " + change.getClass().getName());
		} catch (Exception e) {
			Log.logError("BufferedResourceAccess: Failed to extract content from change", e);
		}

		Log.logWarn("BufferedResourceAccess: Could not extract content from " + change.getClass().getName());
		return "";
	}

	/**
	 * Applies pending changes to a document.
	 * 
	 * @param filePath    The file path
	 * @param originalDoc The original document
	 * @return A new document with changes applied, or the original if no changes
	 */
	private IDocument applyPendingChanges(String filePath, IDocument originalDoc) {
		List<TextFileChange> changes = session.getPendingTextFileChanges()
				.get(filePath);
		if (changes == null || changes.isEmpty()) {
			return originalDoc;
		}

		try {
			return applyChangesToDocument(originalDoc, changes);
		} catch (Exception e) {
			Log.logError("BufferedResourceAccess: Failed to apply pending changes to '" + filePath + "'", e);
			return originalDoc;
		}
	}

	static IDocument applyChangesToDocument(IDocument originalDoc, List<TextFileChange> changes)
			throws BadLocationException {
		// Sort by position descending
		changes = new ArrayList<>(changes);
		changes.sort((o1, o2) -> Integer.compare(o2.getEdit().getOffset(), o1.getEdit().getOffset()));

		Document workingDoc = new Document(originalDoc.get());
		for (TextFileChange change : changes) {
			change.getEdit().apply(workingDoc);
		}
		return workingDoc;
	}

	/**
	 * Checks if a path matches a file name. Examples: - "/Project/src/Test.java"
	 * matches "Test.java" - "/Project/src/Test.java" matches
	 * "/Project/src/Test.java" - "/Project/src/Test.java" matches "src/Test.java"
	 * 
	 * @param path     The full path
	 * @param fileName The file name (can be simple or partial path)
	 * @return true if matches
	 */
	private boolean pathMatchesFileName(String path, String fileName) {
		if (path == null || fileName == null) {
			return false;
		}

		// Normalize paths (handle different separators)
		path = path.replace('\\', '/');
		fileName = fileName.replace('\\', '/');

		// Exact match
		if (path.equals(fileName)) {
			return true;
		}

		// End match (e.g., "/Project/src/Test.java" matches "Test.java" or
		// "src/Test.java")
		if (path.endsWith("/" + fileName)) {
			return true;
		}

		// Simple name match
		String pathFileName = path.substring(path.lastIndexOf('/') + 1);
		String searchFileName = fileName.substring(fileName.lastIndexOf('/') + 1);
		return pathFileName.equals(searchFileName);
	}

	@Override
	public IProject[] getProjects() {
		return delegate.getProjects();
	}
}
