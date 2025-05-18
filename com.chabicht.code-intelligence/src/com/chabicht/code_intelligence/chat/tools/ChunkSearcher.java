package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A language-agnostic text pattern matcher that uses chunking and fuzzy matching
 * to locate text patterns with tolerance for formatting differences.
 */
public class ChunkSearcher {
    
    /**
     * Configuration for the chunk search process.
     */
    public static class Config {
        private int minChunkSize = 20;
        private int maxChunkSize = 500;
        private float similarityThreshold = 0.7f; // 70% similarity required
        private int slidingWindowStep = 5;
        
        public Config() {}
        
        public Config setMinChunkSize(int size) {
            this.minChunkSize = size;
            return this;
        }
        
        public Config setMaxChunkSize(int size) {
            this.maxChunkSize = size;
            return this;
        }
        
        public Config setSimilarityThreshold(float threshold) {
            this.similarityThreshold = threshold;
            return this;
        }
        
        public Config setSlidingWindowStep(int step) {
            this.slidingWindowStep = step;
            return this;
        }
    }
    
    private final Config config;
    
    /**
     * Creates a new ChunkSearcher with default configuration.
     */
    public ChunkSearcher() {
        this.config = new Config();
    }
    
    /**
     * Creates a new ChunkSearcher with custom configuration.
     */
    public ChunkSearcher(Config config) {
        this.config = config;
    }
    
    /**
     * Finds the region in searchText that best matches the originalText.
     * 
     * @param originalText The text pattern to find
     * @param searchText The text to search within
     * @return An array [startIndex, endIndex] of the matching region, or null if no match found
     */
    public int[] findMatchingRegion(String originalText, String searchText) {
        // Split original text into chunks
        List<String> chunks = splitIntoChunks(originalText);
        
        if (chunks.isEmpty()) {
            return null;
        }
        
        // Find positions of chunks in search text
        Map<Integer, int[]> chunkPositions = new HashMap<>(); // chunk index -> [start, end]
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            int[] position = findChunkPosition(chunk, searchText);
            
            if (position != null) {
                chunkPositions.put(i, position);
            }
        }
        
        // Determine overall bounds from found chunks
        if (!chunkPositions.isEmpty()) {
            // Require a minimum percentage of chunks to be found
            double foundRatio = (double) chunkPositions.size() / chunks.size();
            if (foundRatio < 0.3) { // At least 30% of chunks should be found
                return null;
            }
            
            int minStart = Integer.MAX_VALUE;
            int maxEnd = Integer.MIN_VALUE;
            
            for (int[] position : chunkPositions.values()) {
                minStart = Math.min(minStart, position[0]);
                maxEnd = Math.max(maxEnd, position[1]);
            }
            
            return new int[] { minStart, maxEnd };
        }
        
        return null;
    }
    
    /**
     * Splits the text into meaningful chunks for matching.
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        // First try to split by natural boundaries
        String[] paragraphs = text.split("\\n\\s*\\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }
            
            // If paragraph is too large, split it further
            if (paragraph.length() > config.maxChunkSize) {
                // Split by statement boundaries
                String[] statements = paragraph.split("(?<=\\;)|(?<=\\})|(?<=\\{)");
                for (String statement : statements) {
                    if (!statement.trim().isEmpty() && statement.trim().length() >= config.minChunkSize) {
                        chunks.add(statement.trim());
                    }
                }
            } else if (paragraph.trim().length() >= config.minChunkSize) {
                chunks.add(paragraph.trim());
            }
        }
        
        // If we have very few chunks, try to split more aggressively
        if (chunks.size() < 3 && text.length() > config.maxChunkSize) {
            chunks.clear();
            
            // Split by line and group into reasonable chunks
            String[] lines = text.split("\\n");
            StringBuilder currentChunk = new StringBuilder();
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    currentChunk.append(line).append("\n");
                    
                    // Every few lines, create a new chunk
                    if (currentChunk.length() > config.minChunkSize * 2) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk = new StringBuilder();
                    }
                }
            }
            
            // Add final chunk if not empty
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
            }
        }
        
        return chunks;
    }
    
    /**
     * Finds the position of a chunk within the search text.
     */
    private int[] findChunkPosition(String chunk, String searchText) {
        // Normalize texts for better matching
        String normalizedChunk = normalizeForMatching(chunk);
        String normalizedSearch = normalizeForMatching(searchText);
        
        // First try an exact match (after normalization)
        int exactIndex = normalizedSearch.indexOf(normalizedChunk);
        if (exactIndex != -1) {
            return new int[] { exactIndex, exactIndex + normalizedChunk.length() };
        }
        
        // If exact match fails, try fuzzy matching
        return findFuzzyChunkMatch(normalizedChunk, normalizedSearch);
    }
    
    /**
     * Normalizes text for better matching by removing excess whitespace.
     */
    private String normalizeForMatching(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * Attempts to find a fuzzy match for the chunk within the search text.
     */
    private int[] findFuzzyChunkMatch(String chunk, String searchText) {
        if (chunk.length() > searchText.length()) {
            return null;
        }
        
        int bestScore = Integer.MIN_VALUE;
        int bestStart = -1;
        
        // Try sliding windows of different sizes around the expected size
        for (int size = Math.max(chunk.length() - 10, 5); 
             size <= Math.min(chunk.length() + 10, searchText.length()); 
             size++) {
            
            for (int i = 0; i <= searchText.length() - size; i += config.slidingWindowStep) {
                String window = searchText.substring(i, i + size);
                int score = calculateSimilarity(chunk, window);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestStart = i;
                }
            }
        }
        
        // Only accept match if similarity is above threshold
        float requiredScore = chunk.length() * config.similarityThreshold;
        if (bestScore >= requiredScore) {
            return new int[] { bestStart, bestStart + chunk.length() };
        }
        
        return null;
    }
    
    /**
     * Calculates similarity between two strings using Longest Common Subsequence.
     */
    private int calculateSimilarity(String s1, String s2) {
        // For very long strings, use a space-efficient approach
        if (s1.length() > 1000 || s2.length() > 1000) {
            return approximateSimilarity(s1, s2);
        }
        
        // Calculate LCS using dynamic programming
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i-1) == s2.charAt(j-1)) {
                    dp[i][j] = dp[i-1][j-1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Approximate similarity measure for very long strings to avoid memory issues.
     */
    private int approximateSimilarity(String s1, String s2) {
        // Use character frequency comparison as an approximation
        int[] freq1 = new int[128]; // ASCII
        int[] freq2 = new int[128];
        
        for (char c : s1.toCharArray()) {
            if (c < 128) freq1[c]++;
        }
        
        for (char c : s2.toCharArray()) {
            if (c < 128) freq2[c]++;
        }
        
        int similarity = 0;
        for (int i = 0; i < 128; i++) {
            similarity += Math.min(freq1[i], freq2[i]);
        }
        
        return similarity;
    }
}
