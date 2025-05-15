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
		private int maxSearchTextLength = 5000; // Max length for full search
		private float preFilterThreshold = 0.6f; // Threshold for pre-filtering
        
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

		public Config setMaxSearchTextLength(int length) {
			this.maxSearchTextLength = length;
			return this;
		}

		public Config setPreFilterThreshold(float threshold) {
			this.preFilterThreshold = threshold;
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
	 * Finds the region in searchText that best matches the originalText. For large
	 * texts, uses a sectioned approach for better performance.
	 * 
	 * @param originalText The text pattern to find
	 * @param searchText   The text to search within
	 * @return An array [startIndex, endIndex] of the matching region, or null if no
	 *         match found
	 */
	public int[] findMatchingRegion(String originalText, String searchText) {
		// For very large search texts, extract representative sections
		if (searchText.length() > config.maxSearchTextLength) {
			return findMatchingRegionInLargeText(originalText, searchText);
		}

		return findMatchingRegionImpl(originalText, searchText);
	}

	/**
	 * Optimized search for very large texts by searching in representative
	 * sections.
	 */
	private int[] findMatchingRegionInLargeText(String originalText, String searchText) {
		// Take the first, middle and last portions of the text
		int sectionSize = config.maxSearchTextLength / 3;

		// First section
		String firstSection = searchText.substring(0, sectionSize);
		int[] match = findMatchingRegionImpl(originalText, firstSection);
		if (match != null) {
			return match;
		}

		// Middle section
		int middleStart = (searchText.length() - sectionSize) / 2;
		String middleSection = searchText.substring(middleStart, middleStart + sectionSize);
		match = findMatchingRegionImpl(originalText, middleSection);
		if (match != null) {
			return new int[] { middleStart + match[0], middleStart + match[1] };
		}

		// Last section
		int lastStart = searchText.length() - sectionSize;
		String lastSection = searchText.substring(lastStart);
		match = findMatchingRegionImpl(originalText, lastSection);
		if (match != null) {
			return new int[] { lastStart + match[0], lastStart + match[1] };
		}

		// Try bigger chunks with gaps if nothing found
		int biggerSectionSize = config.maxSearchTextLength / 2;

		// First half
		String firstHalf = searchText.substring(0, biggerSectionSize);
		match = findMatchingRegionImpl(originalText, firstHalf);
		if (match != null) {
			return match;
		}

		// Last half
		String lastHalf = searchText.substring(searchText.length() - biggerSectionSize);
		match = findMatchingRegionImpl(originalText, lastHalf);
		if (match != null) {
			return new int[] { searchText.length() - biggerSectionSize + match[0],
					searchText.length() - biggerSectionSize + match[1] };
		}

		return null;
	}

	/**
	 * Main implementation to find matching region in normal-sized text.
	 */
	private int[] findMatchingRegionImpl(String originalText, String searchText) {
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
		if (chunks.size() < 3 && text.length() > config.minChunkSize) {
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
		String normalizedChunk = chunk.replaceAll("\\s+", " ").trim();
		String normalizedSearch = searchText.replaceAll("\\s+", " "); // No trim so we don't need to re-calculate the
																		// string below for s1.

		// First try an exact match (after normalization)
		int exactIndex = normalizedSearch.indexOf(normalizedChunk);
		if (exactIndex != -1) {
			// Map positions from normalizedSearch back to original searchText
			if (normalizedChunk.isEmpty()) { // Handle empty chunk match
				// An empty chunk matches an empty region.
				// Find where the empty normalizedSearch would be in original searchText.
				// If searchText is all whitespace, normalizedSearch is empty.
				// The "location" of this empty match can be tricky.
				// Let's place it at the start of where content would be, or end if all
				// whitespace.
				int i = 0;
				while (i < searchText.length() && Character.isWhitespace(searchText.charAt(i))) {
					i++;
				}
				// If searchText was " ", i=2. Match is [2,2].
				// If searchText was "a b", i=0. Match is [0,0].
				// This behavior aligns with "" matching at a specific point.
				return new int[] { i, i };
			}

			String s1 = normalizedSearch;
			int s1LeadingSpaceCount = 0;
			while (s1LeadingSpaceCount < s1.length() && Character.isWhitespace(s1.charAt(s1LeadingSpaceCount))) {
				s1LeadingSpaceCount++;
			}

			int matchStartInS1 = exactIndex + s1LeadingSpaceCount;
			int matchEndInS1 = exactIndex + normalizedChunk.length() + s1LeadingSpaceCount;

			int originalStart = mapS1ToOriginalOffset(searchText, matchStartInS1);
			int originalEnd = mapS1ToOriginalOffset(searchText, matchEndInS1);

			return new int[] { originalStart, originalEnd };
		}

		// If exact match fails, try fuzzy matching
		return findFuzzyChunkMatch(normalizedChunk, normalizedSearch); // Assuming fuzzy match handles indices correctly
																		// or is out of scope
	}

	/**
	 * Maps an offset from a string with collapsed whitespace (s1) back to an offset
	 * in the original text. s1 is effectively originalText.replaceAll("\\s+", " ").
	 * 
	 * @param originalText The original text.
	 * @param s1Offset     The offset in the s1 string.
	 * @return The corresponding offset in the originalText.
	 */
	private int mapS1ToOriginalOffset(String originalText, int s1Offset) {
		int originalIdx = 0;
		int currentS1Offset = 0;

		if (s1Offset == 0) {
			return 0;
		}

		while (originalIdx < originalText.length() && currentS1Offset < s1Offset) {
			char currentChar = originalText.charAt(originalIdx);
			if (Character.isWhitespace(currentChar)) {
				// Consume the entire block of whitespace in originalText
				originalIdx++;
				while (originalIdx < originalText.length()
						&& Character.isWhitespace(originalText.charAt(originalIdx))) {
					originalIdx++;
				}
				// This block corresponds to a single space in s1 (or contributes to trimming)
				currentS1Offset++;
			} else {
				// Non-whitespace character
				originalIdx++;
				currentS1Offset++;
			}
		}
		return originalIdx;
	}

	/**
	 * Attempts to find a fuzzy match for the chunk within the search text. Uses a
	 * two-phase approach with pre-filtering for performance.
	 */
	private int[] findFuzzyChunkMatch(String chunk, String searchText) {
		if (chunk.length() > searchText.length()) {
			return null;
		}

		int bestScore = Integer.MIN_VALUE;
		int bestStart = -1;

		// Phase 1: Coarse search with pre-filtering
		int coarseStep = Math.max(20, searchText.length() / 100);
		List<Integer> promisingPositions = new ArrayList<>();

		for (int size = Math.max(chunk.length() - 10, 5); size <= Math.min(chunk.length() + 10,
				searchText.length()); size++) {

			for (int i = 0; i <= searchText.length() - size; i += coarseStep) {
				// Skip regions that start with whitespace
				if (i < searchText.length() && Character.isWhitespace(searchText.charAt(i))
						&& (i + 1 < searchText.length() && Character.isWhitespace(searchText.charAt(i + 1)))) {
					continue;
				}

				String window = searchText.substring(i, Math.min(i + size, searchText.length()));

				// Quick pre-check using character frequency
				if (!isPotentialMatch(chunk, window)) {
					continue;
				}

				int score = calculateSimilarity(chunk, window);

				if (score > bestScore * 0.8) { // 80% of best score found so far
					promisingPositions.add(i);
				}

				if (score > bestScore) {
					bestScore = score;
					bestStart = i;
				}
			}
		}

		// Phase 2: Fine-tune search around promising positions
		for (int pos : promisingPositions) {
			int start = Math.max(0, pos - coarseStep / 2);
			int end = Math.min(searchText.length() - chunk.length(), pos + coarseStep / 2);

			for (int i = start; i <= end; i += config.slidingWindowStep) {
				if (i + chunk.length() > searchText.length()) {
					continue; // Skip if window would go beyond the text
				}

				String window = searchText.substring(i, i + chunk.length());
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
	 * Quick pre-filtering check to eliminate obviously non-matching windows.
	 */
	private boolean isPotentialMatch(String s1, String s2) {
		// Quick check using character frequency
		int[] freq1 = new int[26]; // just lowercase letters for speed
		int[] freq2 = new int[26];

		int count1 = 0, count2 = 0;

		for (char c : s1.toLowerCase().toCharArray()) {
			if (c >= 'a' && c <= 'z') {
				freq1[c - 'a']++;
				count1++;
			}
		}

		for (char c : s2.toLowerCase().toCharArray()) {
			if (c >= 'a' && c <= 'z') {
				freq2[c - 'a']++;
				count2++;
			}
		}

		// If character counts are too different, quick reject
		if (Math.abs(count1 - count2) > count1 * 0.3) {
			return false;
		}

		// Calculate frequency similarity
		double similarity = 0;
		for (int i = 0; i < 26; i++) {
			similarity += Math.min(freq1[i], freq2[i]);
		}

		return similarity / Math.max(1, Math.max(count1, count2)) >= config.preFilterThreshold;
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
