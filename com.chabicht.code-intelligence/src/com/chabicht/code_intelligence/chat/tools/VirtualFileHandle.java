package com.chabicht.code_intelligence.chat.tools;

import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * Implementation of IFileHandle for virtual files that don't exist yet.
 * These represent files pending creation with content stored in memory.
 */
public class VirtualFileHandle implements IFileHandle {
    
    private final String fullPath;
    private final String content;
    private final IPath path;
    private final String name;
    private final String extension;
    
    /**
     * Creates a new VirtualFileHandle with the given path and content.
     * 
     * @param fullPath the full workspace-relative path (e.g., "/Project/src/File.java")
     * @param content the file content (must not be null, use empty string for empty file)
     * @throws IllegalArgumentException if fullPath is null/empty or content is null
     */
    public VirtualFileHandle(String fullPath, String content) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Full path cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null (use empty string instead)");
        }
        
        this.fullPath = fullPath;
        this.content = content;
        this.path = new Path(fullPath);
        
        // Extract name from path
        this.name = path.lastSegment();
        
        // Extract extension
        String ext = path.getFileExtension();
        this.extension = ext != null ? ext : "";
    }
    
    @Override
    public boolean exists() {
        // Virtual files don't exist in the workspace yet
        return false;
    }
    
    @Override
    public String getFullPath() {
        return fullPath;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getFileExtension() {
        return extension;
    }
    
    @Override
    public IPath getPath() {
        return path;
    }
    
    @Override
    public boolean isVirtual() {
        // This is always a virtual file
        return true;
    }
    
    @Override
    public long getLength() {
        // Calculate byte length of the content
        return content.getBytes(StandardCharsets.UTF_8).length;
    }
    
    @Override
    public IFile getFile() {
        // Virtual files have no underlying IFile
        return null;
    }
    
    /**
     * Gets the content of this virtual file.
     * 
     * @return the file content
     */
    public String getContent() {
        return content;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VirtualFileHandle that = (VirtualFileHandle) obj;
        return fullPath.equals(that.fullPath);
    }
    
    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
    
    @Override
    public String toString() {
        return "VirtualFileHandle[" + fullPath + ", " + getLength() + " bytes]";
    }
}