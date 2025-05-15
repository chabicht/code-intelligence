package com.chabicht.code_intelligence.chat.tools;

/**
 * Search for a needle text in a haystack based on the Smith-Waterman algorithm.
 */
public class SmithWatermanSearcher {

    /**
     * Configuration for the Smith-Waterman search process.
     */
    public static class Config {
        private int matchScore = 2;
        private int mismatchPenalty = -1; // Should be negative
        private int gapPenalty = -1;      // Should be negative

        public Config() {}

        public Config setMatchScore(int score) {
            this.matchScore = score;
            return this;
        }

        public Config setMismatchPenalty(int penalty) {
            this.mismatchPenalty = penalty;
            return this;
        }

        public Config setGapPenalty(int penalty) {
            this.gapPenalty = penalty;
            return this;
        }
    }

    private final Config config;

    /**
     * Creates a new SmithWatermanSearcher with default configuration.
     */
    public SmithWatermanSearcher() {
        this.config = new Config();
    }

    /**
     * Creates a new SmithWatermanSearcher with custom configuration.
     */
    public SmithWatermanSearcher(Config config) {
        this.config = config;
    }

    /**
     * Finds the region in `textToSearchIn` that best matches the `patternToSearch`
     * using the Smith-Waterman algorithm.
     * Considers whitespace normalization similar to ChunkSearcher.
     *
     * @param patternToSearch The pattern text.
     * @param textToSearchIn  The text to search within.
     * @return An array [startIndex, endIndex] of the matching region in `textToSearchIn`
     *         (original coordinates), or null if no meaningful match is found.
     *         Indices are [inclusiveStart, exclusiveEnd].
     */
    public int[] findMatchingRegion(String patternToSearch, String textToSearchIn) {
        if (patternToSearch == null || textToSearchIn == null) {
            return null;
        }

        // Normalize pattern (trim after collapsing spaces)
        String normPattern = patternToSearch.replaceAll("\\s+", " ").trim();
        // Normalize text (collapse spaces, but DO NOT trim overall, to preserve original mapping context)
        String normText = textToSearchIn.replaceAll("\\s+", " ");

        if (normPattern.isEmpty()) {
            // An empty pattern cannot be meaningfully searched by Smith-Waterman in this context.
            // ChunkSearcher's findOffsetsByPattern throws if originalText.trim().isEmpty().
            // If originalText was not empty but normPattern is (e.g. "   "), this is the place to catch it.
            return null; 
        }
        if (normText.isEmpty() && !normPattern.isEmpty()) {
            return null;
        }
        if (normText.isEmpty() && normPattern.isEmpty()) { // both became empty
             // map [0,0] from normText to textToSearchIn
            int start = mapS1ToOriginalOffset(textToSearchIn, 0);
            return new int[]{start, start};
        }


        int n = normPattern.length();
        int m = normText.length();

        int[][] dp = new int[n + 1][m + 1];
        // dp[i][j] will be the score of the best alignment ending at normPattern[i-1] and normText[j-1]

        int maxScore = 0;
        int max_i = 0;
        int max_j = 0;

        // Fill DP table
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int charScore = (normPattern.charAt(i - 1) == normText.charAt(j - 1))
                        ? config.matchScore
                        : config.mismatchPenalty;
                
                int diagonalScore = dp[i - 1][j - 1] + charScore;
                int upScore = dp[i - 1][j] + config.gapPenalty; // Gap in normText (deletion from normPattern)
                int leftScore = dp[i][j - 1] + config.gapPenalty; // Gap in normPattern (insertion into normPattern)

                dp[i][j] = Math.max(0, Math.max(diagonalScore, Math.max(upScore, leftScore)));

                if (dp[i][j] > maxScore) {
                    maxScore = dp[i][j];
                    max_i = i; // End position in normPattern (1-based)
                    max_j = j; // End position in normText (1-based)
                }
            }
        }

        if (maxScore == 0) {
            // No positive-scoring alignment found
            return null;
        }

        // Backtrack to find the start of the alignment in normText
        int current_i = max_i;
        int current_j = max_j;
        int normStart_j = max_j; // This will be updated to the actual start

        while (current_i > 0 && current_j > 0 && dp[current_i][current_j] > 0) {
            int score = dp[current_i][current_j];
            
            int charScore = (normPattern.charAt(current_i - 1) == normText.charAt(current_j - 1))
                            ? config.matchScore
                            : config.mismatchPenalty;

            if (score == dp[current_i - 1][current_j - 1] + charScore) {
                current_i--;
                current_j--;
            } else if (score == dp[current_i][current_j - 1] + config.gapPenalty) {
                current_j--;
            } else { // score == dp[current_i - 1][j] + config.gapPenalty
                current_i--;
            }
            // Store the j index from text before it's decremented if this is the start
            // The loop continues as long as score > 0. When it stops, current_j is one step *before* the start.
            // So, the actual start index (0-based) in normText is current_j (after loop, before this comment)
        }
        // After loop, current_j is the 0-based start index in normText if match started from dp[...][current_j+1]
        // Or, if the path went straight to a 0, current_j is where the alignment effectively started.
        normStart_j = current_j; // This is the 0-based inclusive start index in normText

        // The end index in normText (0-based, exclusive) is max_j
        int normEnd_j = max_j;

        // Map normalized indices back to original textToSearchIn coordinates
        int finalStart = mapS1ToOriginalOffset(textToSearchIn, normStart_j);
        int finalEnd = mapS1ToOriginalOffset(textToSearchIn, normEnd_j);
        
        // Ensure start is not after end, which can happen if mapping is tricky or match is zero-length at end of whitespace
        if (finalStart > finalEnd && normStart_j == normEnd_j) { // typically for zero-length matches
            finalEnd = finalStart;
        } else if (finalStart > finalEnd) {
            // This would be unusual and might indicate an issue or a very strange mapping result
            // For safety, perhaps return null or log an error.
            // However, mapS1ToOriginalOffset should be monotonic.
            // Let's assume for now it's correct.
        }


        return new int[]{finalStart, finalEnd};
    }

    /**
     * Maps an offset from a string with collapsed whitespace (s1) back to an offset
     * in the original text. s1 is effectively originalText.replaceAll("\\s+", " ").
     * (Copied from ChunkSearcher for self-containment, consider making it a shared utility)
     *
     * @param originalText The original text.
     * @param s1Offset     The offset in the s1 string (normalized string).
     * @return The corresponding offset in the originalText.
     */
    private static int mapS1ToOriginalOffset(String originalText, int s1Offset) {
        if (s1Offset == 0) {
            // Optimization: If originalText starts with whitespace, s1 (normText) might have its content
            // starting at index 0, but this 0 in s1 corresponds to the first non-whitespace char in originalText.
            // However, the current ChunkSearcher's mapS1ToOriginalOffset returns 0 for s1Offset 0.
            // Let's stick to that behavior.
            return 0;
        }

        int originalIdx = 0;
        int currentS1Offset = 0;
        boolean s1IsEmpty = true; 
        for(int i=0; i < originalText.length(); ++i) {
            if(!Character.isWhitespace(originalText.charAt(i))) {
                s1IsEmpty = false;
                break;
            }
        }
        if(s1IsEmpty && s1Offset > 0) { // originalText is all whitespace
             return originalText.length(); // map to the end
        }


        while (originalIdx < originalText.length() && currentS1Offset < s1Offset) {
            char currentChar = originalText.charAt(originalIdx);
            if (Character.isWhitespace(currentChar)) {
                // Consume the current whitespace character
                originalIdx++;
                // If the *next* char in originalText is also whitespace, this block continues.
                // The s1 string has only one space for any such block.
                // We increment currentS1Offset only when we transition from whitespace to non-whitespace
                // or from non-whitespace to whitespace (which becomes one space in s1),
                // or at the end of a whitespace block if it's followed by end-of-string.

                // Check if the previous char in s1 would have been a space due to this whitespace
                if (originalIdx > 0) { // currentS1Offset is based on s1, which has single spaces
                     // A block of whitespace in originalText corresponds to a single space in s1,
                     // unless it's leading/trailing whitespace that gets trimmed from the pattern
                     // (but not from normText).
                     // Let's refine: consume all whitespace in originalText, then increment s1 offset.
                    char prevOriginalChar = (originalIdx > 1) ? originalText.charAt(originalIdx - 2) : ' '; // Hypothetical char before this whitespace block started
                    
                    // Consume the entire block of whitespace in originalText
                    while (originalIdx < originalText.length() && Character.isWhitespace(originalText.charAt(originalIdx))) {
                        originalIdx++;
                    }
                    // This block corresponds to a single space in s1 if it's not leading (for s1)
                    // or if s1 itself is not empty.
                    // The critical part is that `normText` (s1) is `textToSearchIn.replaceAll("\\s+", " ")`
                    // so it *can* start/end with a single space if `textToSearchIn` did.
                    currentS1Offset++; 
                } else { // Whitespace at the very beginning of originalText
                     while (originalIdx < originalText.length() && Character.isWhitespace(originalText.charAt(originalIdx))) {
                        originalIdx++;
                    }
                    currentS1Offset++; // This leading whitespace block becomes one space in s1 (or empty if s1 is trimmed, but normText is not)
                }
            } else {
                // Non-whitespace character
                originalIdx++;
                currentS1Offset++;
            }
        }
        // If s1Offset points beyond the characters represented by non-whitespace sequences in originalText
        // (e.g., s1Offset refers to a trailing space in s1 that came from trailing whitespace in originalText),
        // originalIdx should be at the end of originalText or at the start of the final whitespace block.
        // The loop condition `currentS1Offset < s1Offset` ensures we count up to the desired s1 position.
        // `originalIdx` will then be the corresponding position in `originalText`.
        
        // If s1Offset is for a position within a whitespace block in originalText,
        // originalIdx might be at the end of that block.
        // Example: original="a  b", s1="a b". s1Offset=1 (points to 'a'). currentS1Offset becomes 1, originalIdx becomes 1. Returns 1.
        //          s1Offset=2 (points to ' '). currentS1Offset becomes 2, originalIdx becomes 3 (after '  '). Returns 3.
        // This is slightly different from ChunkSearcher's mapS1ToOriginalOffset. Let's use its exact copy.
        return mapS1ToOriginalOffset_ChunkSearcherImpl(originalText, s1Offset);
    }
    
    // Exact copy from ChunkSearcher
	private static int mapS1ToOriginalOffset_ChunkSearcherImpl(String originalText, int s1Offset) {
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

}
