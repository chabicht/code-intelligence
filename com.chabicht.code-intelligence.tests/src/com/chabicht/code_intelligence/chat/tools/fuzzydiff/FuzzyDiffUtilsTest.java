package com.chabicht.code_intelligence.chat.tools.fuzzydiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;

/**
 * Characterization tests for the unified diff parser built on top of
 * java-diff-utils. They pin down delta positions and chunk contents so that a
 * java-diff-utils upgrade cannot silently change how patches are interpreted.
 */
public class FuzzyDiffUtilsTest {

	private static List<String> lines(String diff) {
		return Arrays.asList(diff.split("\n", -1));
	}

	private static List<String> textOf(Chunk<FuzzyLine> chunk) {
		return chunk.getLines().stream().map(FuzzyLine::toString).collect(Collectors.toList());
	}

	@Test
	public void singleHunkIsParsed() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -2,3 +2,3 @@\n" //
				+ " one\n" //
				+ "-two\n" //
				+ "+TWO\n" //
				+ " three"));

		assertEquals(1, patch.getDeltas().size());
		AbstractDelta<FuzzyLine> delta = patch.getDeltas().get(0);
		assertEquals(1, delta.getSource().getPosition());
		assertEquals(1, delta.getTarget().getPosition());
		assertEquals(Arrays.asList("one", "two", "three"), textOf(delta.getSource()));
		assertEquals(Arrays.asList("one", "TWO", "three"), textOf(delta.getTarget()));
		// Changed line numbers are counted from the hunk start (header line number
		// minus one) plus the running index of the line within the hunk.
		assertEquals(Arrays.asList(3), delta.getSource().getChangePosition());
		assertEquals(Arrays.asList(3), delta.getTarget().getChangePosition());
	}

	@Test
	public void multipleHunksProduceMultipleDeltas() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -1,2 +1,2 @@\n" //
				+ "-alpha\n" //
				+ "+ALPHA\n" //
				+ " beta\n" //
				+ "@@ -10,2 +10,2 @@\n" //
				+ " gamma\n" //
				+ "-delta\n" //
				+ "+DELTA"));

		assertEquals(2, patch.getDeltas().size());
		assertEquals(0, patch.getDeltas().get(0).getSource().getPosition());
		assertEquals(9, patch.getDeltas().get(1).getSource().getPosition());
		assertEquals(Arrays.asList("gamma", "DELTA"), textOf(patch.getDeltas().get(1).getTarget()));
	}

	@Test
	public void addOnlyHunk() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -1,1 +1,2 @@\n" //
				+ " keep\n" //
				+ "+added"));

		assertEquals(1, patch.getDeltas().size());
		AbstractDelta<FuzzyLine> delta = patch.getDeltas().get(0);
		assertEquals(Arrays.asList("keep"), textOf(delta.getSource()));
		assertEquals(Arrays.asList("keep", "added"), textOf(delta.getTarget()));
	}

	@Test
	public void deleteOnlyHunk() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -1,2 +1,1 @@\n" //
				+ " keep\n" //
				+ "-gone"));

		assertEquals(1, patch.getDeltas().size());
		AbstractDelta<FuzzyLine> delta = patch.getDeltas().get(0);
		assertEquals(Arrays.asList("keep", "gone"), textOf(delta.getSource()));
		assertEquals(Arrays.asList("keep"), textOf(delta.getTarget()));
	}

	@Test
	public void hunkHeaderWithoutLineCounts() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -5 +5 @@\n" //
				+ "-old\n" //
				+ "+new"));

		assertEquals(1, patch.getDeltas().size());
		assertEquals(4, patch.getDeltas().get(0).getSource().getPosition());
		assertEquals(4, patch.getDeltas().get(0).getTarget().getPosition());
	}

	@Test
	public void hunkHeaderWithTrailingContextIsAccepted() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -3,2 +3,2 @@ public static void main(String[] args) {\n" //
				+ "-old\n" //
				+ "+new"));

		assertEquals(1, patch.getDeltas().size());
		assertEquals(2, patch.getDeltas().get(0).getSource().getPosition());
	}

	@Test
	public void preludeBeforePlusPlusPlusIsSkipped() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("diff --git a/File.java b/File.java\n" //
				+ "index 1234567..89abcde 100644\n" //
				+ "@@ -1,1 +1,1 @@ this header is still in the prelude\n" //
				+ "--- a/File.java\n" //
				+ "+++ b/File.java\n" //
				+ "@@ -1,1 +1,1 @@\n" //
				+ "-old\n" //
				+ "+new"));

		assertEquals(1, patch.getDeltas().size());
		assertEquals(Arrays.asList("old"), textOf(patch.getDeltas().get(0).getSource()));
	}

	@Test
	public void textWithoutAnyHunkYieldsEmptyPatch() {
		Patch<FuzzyLine> patch = FuzzyDiffUtils.parseUnifiedDiff(lines("this is not a diff at all"));
		assertTrue(patch.getDeltas().isEmpty());
	}

	@Test
	public void fuzzyLineComparesIgnoringSurroundingWhitespaceAndCase() {
		assertEquals(new FuzzyLine("foo"), new FuzzyLine("  FOO  "));
		// Indented lines must compare equal to themselves - see FuzzyLine.equals.
		assertEquals(new FuzzyLine("\tint value = 1;"), new FuzzyLine("\tint value = 1;"));
		assertEquals(new FuzzyLine("\tint value = 1;").hashCode(), new FuzzyLine("  int VALUE = 1;  ").hashCode());
		assertNotEquals(new FuzzyLine("foo"), new FuzzyLine("bar"));
		assertTrue(new FuzzyLine("foo").compareTo(new FuzzyLine("foo")) == 0);
	}
}
