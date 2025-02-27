package com.chabicht.code_intelligence.model;

import java.time.Instant;
import java.util.UUID;

public class ChatHistoryEntry {
	private UUID id;
	private String title;
	private Instant createdAt;
	private Instant updatedAt;
	private ChatConversation conversation;
    
    public ChatHistoryEntry() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public ChatHistoryEntry(ChatConversation conversation) {
        this();
        this.conversation = conversation;
        // Generate a title from the first user message
        if (conversation != null && !conversation.getMessages().isEmpty()) {
            for (ChatConversation.ChatMessage msg : conversation.getMessages()) {
                if (msg.getRole() == ChatConversation.Role.USER) {
                    String content = msg.getContent();
                    this.title = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                    break;
                }
            }
        }
        if (this.title == null) {
            this.title = "Chat from " + createdAt;
        }
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ChatConversation getConversation() {
        return conversation;
    }

    public void setConversation(ChatConversation conversation) {
        this.conversation = conversation;
    }
    
    public void updateFromConversation(ChatConversation conversation) {
        this.conversation = conversation;
        this.updatedAt = Instant.now();
    }
    
    public int getMessageCount() {
        return conversation != null ? conversation.getMessages().size() : 0;
    }
}
