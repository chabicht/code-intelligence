package com.chabicht.code_intelligence.chat.tools;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

/**
 * A lightweight abstraction for file handles that supports both real Eclipse IFile
 * instances and virtual files that exist only in memory (pending creation).
 * 
 * This interface provides only the minimal set of methods needed by the AI tools,
 * avoiding the complexity of implementing the full IFile interface.
 */
public interface IFileHandle {
    
    /**
     * Checks if this file exists in the workspace.
     * 
     * @return true for existing real files, false for virtual files or non-existent files
     */
    boolean exists();
    
    /**
     * Gets the full workspace-relative path of this file.
     * 
     * @return the full path as a string, e.g., "/ProjectName/src/com/example/MyClass.java"
     */
    String getFullPath();
    
    /**
     * Gets the simple name of this file including the extension.
     * 
     * @return the file name, e.g., "MyClass.java"
     */
    String getName();
    
    /**
     * Gets the file extension without the leading dot.
     * 
     * @return the extension, e.g., "java", or null if no extension
     */
    String getFileExtension();
    
    /**
     * Gets the Eclipse IPath representation of this file's path.
     * 
     * @return the IPath object
     */
    IPath getPath();
    
    /**
     * Determines if this is a virtual file (pending creation).
     * 
     * @return true if virtual, false if backed by a real IFile
     */
    boolean isVirtual();
    
    /**
     * Gets the size of the file in bytes.
     * 
     * @return file size in bytes, or 0 if virtual or cannot be determined
     */
    long getLength();
    
    /**
     * Gets the underlying IFile if this is a real file handle.
     * 
     * @return the IFile, or null if this is a virtual file
     */
    IFile getFile();
}