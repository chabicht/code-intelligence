package com.chabicht.code_intelligence.chat.tools;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

/**
 * Implementation of IFileHandle that wraps a real Eclipse IFile.
 * All methods delegate to the underlying IFile instance.
 */
public class RealFileHandle implements IFileHandle {
    
    private final IFile file;
    
    /**
     * Creates a new RealFileHandle wrapping the given IFile.
     * 
     * @param file the Eclipse IFile to wrap (must not be null)
     * @throws IllegalArgumentException if file is null
     */
    public RealFileHandle(IFile file) {
        if (file == null) {
            throw new IllegalArgumentException("IFile cannot be null");
        }
        this.file = file;
    }
    
    @Override
    public boolean exists() {
        return file.exists();
    }
    
    @Override
    public String getFullPath() {
        return file.getFullPath().toString();
    }
    
    @Override
    public String getName() {
        return file.getName();
    }
    
    @Override
    public String getFileExtension() {
        // IFile.getFileExtension() can return null
        String extension = file.getFileExtension();
        return extension != null ? extension : "";
    }
    
    @Override
    public IPath getPath() {
        return file.getFullPath();
    }
    
    @Override
    public boolean isVirtual() {
        // Real files are never virtual
        return false;
    }
    
    @Override
    public long getLength() {
        try {
            // Try to get the actual file length from the file system
            if (file.exists() && file.getLocation() != null) {
                java.io.File javaFile = file.getLocation().toFile();
                if (javaFile != null && javaFile.exists()) {
                    return javaFile.length();
                }
            }
        } catch (Exception e) {
            // Log but don't throw - fall back to 0
            com.chabicht.code_intelligence.util.Log.logWarn(
                "Failed to get file length for: " + file.getFullPath(), e);
        }
        return 0;
    }
    
    @Override
    public IFile getFile() {
        return file;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RealFileHandle that = (RealFileHandle) obj;
        return file.equals(that.file);
    }
    
    @Override
    public int hashCode() {
        return file.hashCode();
    }
    
    @Override
    public String toString() {
        return "RealFileHandle[" + getFullPath() + "]";
    }
}