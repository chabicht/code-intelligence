package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;

import com.chabicht.code_intelligence.Activator;

public class FindFilesTool {

    private final IResourceAccess resourceAccess;

    public FindFilesTool(IResourceAccess resourceAccess) {
        this.resourceAccess = resourceAccess;
    }

    public FindFilesResult findFiles(String regexPattern, List<String> projectNames, boolean isCaseSensitive) {
        List<String> foundFiles = new ArrayList<>();
        try {
            int flags = isCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            Pattern pattern = Pattern.compile(regexPattern, flags);
            
            IProject[] projectsToSearch;

            if (projectNames == null || projectNames.isEmpty()) {
                projectsToSearch = resourceAccess.getProjects();
            } else {
                projectsToSearch = Arrays.stream(resourceAccess.getProjects())
                        .filter(p -> projectNames.contains(p.getName()))
                        .toArray(IProject[]::new);
            }

            for (IProject project : projectsToSearch) {
                if (project.isOpen()) {
                    project.accept(new IResourceVisitor() {
                        @Override
                        public boolean visit(IResource resource) throws CoreException {
                            if (resource.getType() == IResource.FILE) {
                                String fullPath = resource.getFullPath().toString();
								if (pattern.matcher(fullPath).find()) {
                                    foundFiles.add(fullPath);
                                }
                            }
                            return true; // Continue visiting children
                        }
                    });
                }
            }
            return new FindFilesResult(true, "Search completed. Found " + foundFiles.size() + " files.", foundFiles);

        } catch (PatternSyntaxException e) {
            Activator.logError("Invalid regex pattern in find_files: " + regexPattern, e);
            return new FindFilesResult(false, "Error: Invalid regular expression syntax: " + e.getMessage(), null);
        } catch (CoreException e) {
            Activator.logError("Error traversing workspace in find_files", e);
            return new FindFilesResult(false, "Error: A problem occurred while searching the workspace: " + e.getMessage(), null);
        }
    }

    // Inner class for structured results, following the pattern of other tools.
    public static class FindFilesResult {
        private final boolean success;
        private final String message;
        private final List<String> filePaths;

        public FindFilesResult(boolean success, String message, List<String> filePaths) {
            this.success = success;
            this.message = message;
            this.filePaths = filePaths != null ? filePaths : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<String> getFilePaths() { return filePaths; }
    }
}