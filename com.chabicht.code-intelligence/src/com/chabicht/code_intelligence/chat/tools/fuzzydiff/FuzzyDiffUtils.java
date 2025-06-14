package com.chabicht.code_intelligence.chat.tools.fuzzydiff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;

/**
 * Utility class, similar to {@link UnifiedDiffUtils} but for strings that are
 * compared in a fuzzy way.
 */
public class FuzzyDiffUtils {
	private static final Pattern UNIFIED_DIFF_CHUNK_REGEXP = Pattern
			.compile("^\\s*@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@.*$");

	/**
	 * Parse the given text in unified format and creates the list of deltas for it.
	 *
	 * @param diff the text in unified format
	 * @return the patch with deltas.
	 */
	public static Patch<FuzzyLine> parseUnifiedDiff(List<String> diff) {
		boolean inPrelude = true;
		List<String[]> rawChunk = new ArrayList<>();
		Patch<FuzzyLine> patch = new Patch<>();

		int old_ln = 0;
		int new_ln = 0;
		String tag;
		String rest;
		for (String line : diff) {
			// Skip leading lines until after we've seen one starting with '+++'
			if (inPrelude) {
				if (line.startsWith("+++")) {
					inPrelude = false;
				}
				continue;
			}
			Matcher m = UNIFIED_DIFF_CHUNK_REGEXP.matcher(line);
			if (m.find()) {
				// Process the lines in the previous chunk
				processLinesInPrevChunk(rawChunk, patch, old_ln, new_ln);
				// Parse the @@ header
				old_ln = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
				new_ln = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));

				if (old_ln == 0) {
					old_ln = 1;
				}
				if (new_ln == 0) {
					new_ln = 1;
				}
			} else {
				if (line.length() > 0) {
					tag = line.substring(0, 1);
					rest = line.substring(1);
					if (" ".equals(tag) || "+".equals(tag) || "-".equals(tag)) {
						rawChunk.add(new String[] { tag, rest });
					}
				} else {
					rawChunk.add(new String[] { " ", "" });
				}
			}
		}

		// Process the lines in the last chunk
		processLinesInPrevChunk(rawChunk, patch, old_ln, new_ln);

		return patch;
	}

	private static void processLinesInPrevChunk(List<String[]> rawChunk, Patch<FuzzyLine> patch, int old_ln,
			int new_ln) {
		String tag;
		String rest;
		if (!rawChunk.isEmpty()) {
			List<FuzzyLine> oldChunkLines = new ArrayList<>();
			List<FuzzyLine> newChunkLines = new ArrayList<>();

			List<Integer> removePosition = new ArrayList<>();
			List<Integer> addPosition = new ArrayList<>();
			int removeNum = 0;
			int addNum = 0;
			for (String[] raw_line : rawChunk) {
				tag = raw_line[0];
				rest = raw_line[1];
				if (" ".equals(tag) || "-".equals(tag)) {
					removeNum++;
					oldChunkLines.add(new FuzzyLine(rest));
					if ("-".equals(tag)) {
						removePosition.add(old_ln - 1 + removeNum);
					}
				}
				if (" ".equals(tag) || "+".equals(tag)) {
					addNum++;
					newChunkLines.add(new FuzzyLine(rest));
					if ("+".equals(tag)) {
						addPosition.add(new_ln - 1 + addNum);
					}
				}
			}
			patch.addDelta(new ChangeDelta<>(new Chunk<>(old_ln - 1, oldChunkLines, removePosition),
					new Chunk<>(new_ln - 1, newChunkLines, addPosition)));
			rawChunk.clear();
		}
	}
}
