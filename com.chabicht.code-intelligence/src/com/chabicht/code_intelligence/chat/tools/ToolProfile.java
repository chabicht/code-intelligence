package com.chabicht.code_intelligence.chat.tools;

import java.util.Set;

public enum ToolProfile {
    READ_ONLY("Read Only", Set.of("read")),
    READ_WRITE("Read/Write", Set.of("read", "write")),
    ALL("All Tools", null);

    private final String displayName;
    private final Set<String> allowedTags; // null = no tag filtering, all tools pass

    ToolProfile(String displayName, Set<String> allowedTags) {
        this.displayName = displayName;
        this.allowedTags = allowedTags;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getAllowedTags() {
        return allowedTags;
    }

    /**
     * Returns true if a tool with the given tags is allowed by this profile.
     * A tool is allowed if at least one of its tags is in the profile's allowed set.
     * If allowedTags is null (i.e., ALL profile), every tool passes.
     * If the tool has no tags, it is always allowed (backwards compatibility).
     */
    public boolean allowsTool(Set<String> toolTags) {
        if (allowedTags == null) {
            return true;
        }
        if (toolTags == null || toolTags.isEmpty()) {
            return true;
        }
        for (String tag : toolTags) {
            if (allowedTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
