package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;

import com.chabicht.code_intelligence.util.Log;

public class ResourceAccess implements IResourceAccess {

	/**
	 * Finds the IFile resource corresponding to the given file name. Provides basic
	 * handling for ambiguity (multiple files with the same name).
	 *
	 * @param fileName The simple name of the file (e.g., "MyClass.java").
	 * @return The IFile if found uniquely, or a best guess, or null.
	 */
	public IFile findFileByNameBestEffort(String fileName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final List<IFile> foundFiles = new ArrayList<>();

		try {
			root.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource.getType() == IResource.FILE && resource.getName().equals(fileName)) {
						foundFiles.add((IFile) resource);
						// Optimization: If you only ever want the *first* match, uncomment the next
						// line
						// return false;
					}
					// Continue searching in subfolders regardless
					return true;
				}
			});
		} catch (CoreException e) {
			Log.logError("Error searching for file: " + fileName, e);
			return null; // Return null on error during search
		}

		// Evaluate search results
		if (foundFiles.size() == 1) {
			return foundFiles.get(0); // Unique match found
		} else if (foundFiles.isEmpty()) {
			Log.logInfo("No file found with name: " + fileName);
			return null; // No file found
		} else {
			// Ambiguous case: multiple files found
			// Log a warning and return the first one found as a best effort.
			// Consider making this behavior configurable or throwing an error.
			Log.logInfo("Multiple files found with name: " + fileName + ". Using the first one: "
					+ foundFiles.get(0).getFullPath());
			// You might want to try finding the file in the active editor first here
			// IFile fileFromEditor = findFileInActiveEditor(fileName);
			// if (fileFromEditor != null) return fileFromEditor;
			return foundFiles.get(0); // Return the first match as a fallback
		}
	}

	/**
	 * Gets the IDocument for a file, managing the connection via
	 * TextFileBufferManager. Stores the connected document in the provided map.
	 *
	 * @param file        The file to get the document for.
	 * @param documentMap A map to store the connected document and its buffer.
	 * @return The IDocument, or null if connection fails.
	 */
	@Override
	public IDocument getDocumentAndConnect(IFile file, Map<IFile, IDocument> documentMap) {
		if (documentMap.containsKey(file)) {
			return documentMap.get(file);
		}
	
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		IPath path = file.getFullPath();
		IDocument document = null;
		try {
			// Connect to the file buffer
			bufferManager.connect(path, LocationKind.IFILE, new NullProgressMonitor());
			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
			if (textFileBuffer != null) {
				document = textFileBuffer.getDocument();
								documentMap.put(file, document); // Store for later disconnection
			} else {
				Log.logError("Could not get text file buffer for: " + file.getName());
			}
					} catch (CoreException e) {
			Log.logError("Failed to connect or get document for " + file.getName() + ": " + e.getMessage(), e);
			// Ensure disconnection if connection partially succeeded but failed later
			try {
				bufferManager.disconnect(path, LocationKind.IFILE, new NullProgressMonitor());
						} catch (CoreException disconnectEx) {
				Log.logError("Error during buffer disconnect cleanup for " + file.getName(), disconnectEx);
			}
			return null; // Return null if document couldn't be obtained
		}
		return document;
	}


	/**
	 * Disconnects all documents managed in the provided map.
	 *
	 * @param documentMap The map containing files and their connected documents.
	 */
	public void disconnectAllDocuments(Map<IFile, IDocument> documentMap) {
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		for (IFile file : documentMap.keySet()) {
			try {
				bufferManager.disconnect(file.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException e) {
				Log.logError("Failed to disconnect document for " + file.getName() + ": " + e.getMessage(), e);
			}
		}
		documentMap.clear(); // Clear the map after disconnecting
	}

}
