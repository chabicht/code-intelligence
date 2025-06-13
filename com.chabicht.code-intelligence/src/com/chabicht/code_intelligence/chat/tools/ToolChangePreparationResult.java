package com.chabicht.code_intelligence.chat.tools;

import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.text.edits.TextEdit;

/**
 * A standard result object for tools that prepare TextEdits for an existing file.
 * This object is stateless and used to transfer prepared changes to the FunctionCallSession.
 */
public class ToolChangePreparationResult {
    private final boolean success;
    private final String message;
    private final IFile file; // The file that was processed
    private final List<TextEdit> edits; // The list of prepared edits
    private final String diffPreview; // A preview of this specific change

    // Private constructor to enforce usage of static factory methods
    private ToolChangePreparationResult(boolean success, String message, IFile file, List<TextEdit> edits, String diffPreview) {
        this.success = success;
        this.message = message;
        this.file = file;
        this.edits = edits;
        this.diffPreview = diffPreview;
    }

    /**
     * Creates a result object for a successful preparation.
     */
    public static ToolChangePreparationResult success(String message, IFile file, List<TextEdit> edits, String diffPreview) {
        return new ToolChangePreparationResult(true, message, file, edits, diffPreview);
    }

    /**
     * Creates a result object for a failed preparation.
     */
    public static ToolChangePreparationResult failure(String message) {
        return new ToolChangePreparationResult(false, message, null, null, null);
    }

    // Standard Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public IFile getFile() { return file; }
    public List<TextEdit> getEdits() { return edits; }
    public String getDiffPreview() { return diffPreview; }
}