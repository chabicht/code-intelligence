package com.chabicht.code_intelligence.chat.tools;

import com.chabicht.code_intelligence.util.Log; // Assuming Log is accessible

public class CreateFileTool {

    private final IResourceAccess resourceAccess;

    public CreateFileTool(IResourceAccess resourceAccess) {
        this.resourceAccess = resourceAccess;
    }

    public static class CreateFileResult {
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

    /**
     * Creates a new file with the specified path and content.
     * This method relies on an underlying method in IResourceAccess, for example:
     * {@code public CreateFileResult createFileInWorkspace(String filePath, String content);}
     * This underlying method is responsible for:
     * 1. Checking if the file at {@code filePath} already exists. If so, return failure.
     * 2. Ensuring parent directories exist (creating them if necessary).
     * 3. Creating the new file and writing {@code content} to it.
     * 4. Returning a {@code CreateFileResult} indicating success or failure.
     *
     * @param filePath The path for the new file.
     * @param content The content for the new file.
     * @return A {@code CreateFileResult} indicating the outcome.
     */
    public CreateFileResult createFile(String filePath, String content) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return CreateFileResult.failure("File path cannot be null or empty.");
        }
        // Content can be an empty string, but the parameter itself should not be null.
        if (content == null) {
            return CreateFileResult.failure("Content cannot be null. Provide an empty string for an empty file.", filePath);
        }

        // Assumption: IResourceAccess interface is updated with the following method:
        // public CreateFileResult createFileInWorkspace(String filePath, String content);
        // This method would encapsulate the logic of checking existence, creating directories,
        // creating the file, and writing content, then returning an appropriate CreateFileResult.
        try {
            return resourceAccess.createFileInWorkspace(filePath, content);
        } catch (Exception e) {
            // This catch block is a fallback if createFileInWorkspace doesn't handle all its exceptions
            // and convert them to CreateFileResult. Ideally, createFileInWorkspace should be robust.
            Log.logError("Unexpected error during createFile operation for " + filePath + ": " + e.getMessage(), e);
            return CreateFileResult.failure("Unexpected error creating file " + filePath + ": " + e.getMessage(), filePath);
        }
    }
}
