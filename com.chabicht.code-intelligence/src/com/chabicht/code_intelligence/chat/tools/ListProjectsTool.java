package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.chabicht.code_intelligence.Activator;
import com.google.gson.annotations.SerializedName;

public class ListProjectsTool {

    private final IResourceAccess resourceAccess;

    public ListProjectsTool(IResourceAccess resourceAccess) {
        this.resourceAccess = resourceAccess;
    }

    public ListProjectsResult listProjects() {
        try {
            IProject[] projects = resourceAccess.getProjects();
            List<ProjectInfo> projectInfos = new ArrayList<>();
            for (IProject project : projects) {
                projectInfos.add(new ProjectInfo(project.getName(), project.isOpen()));
            }
            return new ListProjectsResult(true, "Successfully retrieved " + projectInfos.size() + " projects.", projectInfos);
        } catch (Exception e) {
            Activator.logError("Error listing projects", e);
            return new ListProjectsResult(false, "An error occurred while listing projects: " + e.getMessage(), null);
        }
    }

    // Inner class for structured results
    public static class ListProjectsResult {
        private final boolean success;
        private final String message;
        private final List<ProjectInfo> projects;

        public ListProjectsResult(boolean success, String message, List<ProjectInfo> projects) {
            this.success = success;
            this.message = message;
            this.projects = projects != null ? projects : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<ProjectInfo> getProjects() { return projects; }
    }

    // Inner class to hold project information for GSON serialization
    public static class ProjectInfo {
        // Use snake_case for direct GSON serialization to match model expectations
        @SerializedName("project_name")
        private final String projectName;
        @SerializedName("is_open")
        private final boolean isOpen;

        public ProjectInfo(String projectName, boolean isOpen) {
            this.projectName = projectName;
            this.isOpen = isOpen;
        }

        // Getters for internal use
        public String getProjectName() { return projectName; }
        public boolean isOpen() { return isOpen; }
    }
}
