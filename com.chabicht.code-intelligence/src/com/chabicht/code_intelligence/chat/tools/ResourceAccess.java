package com.chabicht.code_intelligence.chat.tools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.chabicht.code_intelligence.util.Log;

public class ResourceAccess implements IResourceAccess {

	/**
	 * Finds the IFile resource corresponding to the given file name. Provides basic
	 * handling for ambiguity (multiple files with the same name).
	 *
	 * @param fileName The simple name of the file (e.g., "MyClass.java") or a full
	 *                 path (e.g., "/ProjectName/src/com/example/MyClass.java").
	 * @return The IFile if found uniquely, or a best guess, or null.
	 */
	public IFile findFileByNameBestEffort(String fileName) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		final String originalFileName = fileName; // Preserve the original input file name

		// Handle a/ and b/ prefixes commonly found in GIT diffs
		if (fileName.startsWith("a/") || fileName.startsWith("b/")) {
			fileName = fileName.substring(2);
		}

		if (fileName.contains("/")) {
			// Try to find file directly by path
			IFile file = root.getFile(new Path(fileName));
			if (file.exists()) {
				return file;
			}
			// If direct lookup fails, extract just the filename and fall back to search
			String simpleFileName = new Path(fileName).lastSegment();
			Log.logInfo("File path not found: " + fileName + ". Searching by simple name: " + simpleFileName);
			fileName = simpleFileName;
		}

		final List<IFile> foundFiles = new ArrayList<>();

		final String finalFilename = fileName;
		// Search for the file by its simple name throughout the workspace.
		// This populates the foundFiles list, which was previously always empty
		// at this stage of the code if the file wasn't found by direct path.
		// This search is performed if fileName is a simple name, or if it was
		// a path that wasn't found directly (lines 46-49 reduce it to a simple name).
		try {
			root.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource.getType() == IResource.FILE && resource.getName().equals(finalFilename)) {
						foundFiles.add((IFile) resource);
					}
					return true; // Continue visiting other resources
				}
			});
		} catch (CoreException e) {
			Log.logError("Error while searching for file by name: " + finalFilename, e);
			// If an error occurs during the search, foundFiles might be empty or incomplete.
			// The subsequent logic is designed to handle this (e.g., by reporting "No file found").
		}

		if (foundFiles.size() == 1) {
			return foundFiles.get(0); // Unique match found
		} else if (foundFiles.isEmpty()) {
			Log.logInfo("No file found with name: " + finalFilename);
			return null; // No file found
		} else {
			// Ambiguous case: multiple files found.
			// Use the path of the candidates to find the best match.
			// The file with the longest matching streak of parent directories wins.
			// 'fileName' is the original search string (potentially with path).
			// 'finalFilename' is the simple name used in the visitor if 'fileName' was a
			// path.
			Log.logInfo("Multiple files found with simple name: " + finalFilename
					+ ". Disambiguating using original search path: " + originalFileName);

			IFile bestMatchFile = null;
			int maxMatchDepth = -1;

			// Path segments from the original search string (originalFileName)
			String[] searchPathSegments = new Path(originalFileName).segments();

			for (IFile candidateFile : foundFiles) {
				IPath candidateIPath = candidateFile.getFullPath(); // e.g., /project/src/com/example/File.java
				String[] candidatePathSegments = candidateIPath.segments(); // e.g., ["project", "src", "com",
																			// "example", "File.java"]

				int currentMatchDepth = 0;
				int searchIdx = searchPathSegments.length - 1;
				int candidateIdx = candidatePathSegments.length - 1;

				// Compare segments from the end (filename, then parent, then grandparent, etc.)
				while (searchIdx >= 0 && candidateIdx >= 0
						&& searchPathSegments[searchIdx].equals(candidatePathSegments[candidateIdx])) {
					currentMatchDepth++;
					searchIdx--;
					candidateIdx--;
				}

				if (currentMatchDepth > maxMatchDepth) {
					maxMatchDepth = currentMatchDepth;
					bestMatchFile = candidateFile;
				}
			}

			if (bestMatchFile != null) {
				Log.logInfo("Disambiguated match for '" + originalFileName + "': " + bestMatchFile.getFullPath()
						+ " (match depth: " + maxMatchDepth + ")");
				return bestMatchFile;
			} else {
				// Fallback if disambiguation didn't identify a clear best match (should be rare
				// if foundFiles is not empty)
				Log.logWarn("Could not effectively disambiguate multiple files for '" + originalFileName
						+ "' using path matching (maxMatchDepth=" + maxMatchDepth + "). Returning the first found: "
						+ foundFiles.get(0).getFullPath());
				return foundFiles.get(0);
			}
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

	@Override
	public CreateFileResult createFileInWorkspace(String filePath, String content) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath path = new Path(filePath);
		IFile file = root.getFile(path);

		if (file.exists()) {
			return CreateFileResult.failure("File already exists: " + filePath, filePath);
		}

		// Ensure parent folders exist
		IContainer parent = file.getParent();
		if (parent != null && !parent.exists() && parent.getType() == IResource.FOLDER) {
			try {
				((org.eclipse.core.resources.IFolder) parent).create(true, true, new NullProgressMonitor());
			} catch (CoreException e) {
				Log.logError("Failed to create parent directory for " + filePath + ": " + e.getMessage(), e);
				return CreateFileResult.failure(
						"Failed to create parent directory: " + e.getMessage(), filePath);
			}
		}

		InputStream source = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		try {
			file.create(source, true, new NullProgressMonitor());
			return new CreateFileResult(true, "File created successfully: " + filePath, filePath);
		} catch (CoreException e) {
			Log.logError("Failed to create file " + filePath + ": " + e.getMessage(), e);
			return CreateFileResult.failure("Failed to create file: " + e.getMessage(), filePath);
		} finally {
			try {
				source.close();
			} catch (java.io.IOException e) {
				Log.logWarn("Error closing input stream for " + filePath, e);
		}
	}
}

	@Override
	public IFileHandle findFileHandleByName(String fileName) {
		// For now, only support real files - BufferedResourceAccess will add virtual support
		IFile file = findFileByNameBestEffort(fileName);
		return file != null ? new RealFileHandle(file) : null;
	}

	@Override
	public IDocument getDocumentForHandle(IFileHandle handle, Map<IFileHandle, IDocument> documentMap) {
		if (handle == null) {
			throw new IllegalArgumentException("File handle cannot be null");
		}

		if (handle.isVirtual()) {
			// Handle virtual files
			VirtualFileHandle virtualHandle = (VirtualFileHandle) handle;
			IDocument doc = new Document(virtualHandle.getContent());
			documentMap.put(handle, doc);
			return doc;
		} else {
			// Handle real files
			RealFileHandle realHandle = (RealFileHandle) handle;
			IFile file = realHandle.getFile();

			// Need to convert the map type due to generics
			Map<IFile, IDocument> fileDocumentMap = new HashMap<>();
			IDocument doc = getDocumentAndConnect(file, fileDocumentMap);

			// Transfer to the handle map
			if (doc != null) {
				documentMap.put(handle, doc);
				// Also maintain the file mapping for disconnection later
				for (Map.Entry<IFile, IDocument> entry : fileDocumentMap.entrySet()) {
					// Store with the handle as key for consistency
					documentMap.put(new RealFileHandle(entry.getKey()), entry.getValue());
				}
			}

			return doc;
		}
	}

	@Override
	public IProject[] getProjects() {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		return root.getProjects();
	}
}
