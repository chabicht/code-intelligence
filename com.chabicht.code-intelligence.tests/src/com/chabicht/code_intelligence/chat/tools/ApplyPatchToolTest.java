package com.chabicht.code_intelligence.chat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for {@link ApplyPatchTool}, which drives
 * java-diff-utils to validate and apply unified diffs. Uses the in-memory
 * {@link TestResourceAccess} so the tests run headless.
 */
public class ApplyPatchToolTest {

	private static final String FILE = "notes.txt";

	private static final String ORIGINAL = "alpha\nbeta\ngamma\ndelta\nepsilon\n";

	private static ApplyPatchTool tool(String content) {
		return new ApplyPatchTool(new TestResourceAccess(Map.of(FILE, content)));
	}

	/** Applies the prepared edits to a copy of the original content. */
	private static String applied(String original, ToolChangePreparationResult result) throws Exception {
		IDocument doc = new Document(original);
		for (TextEdit edit : result.getEdits()) {
			edit.apply(doc, TextEdit.NONE);
		}
		return doc.get();
	}

	@Test
	public void patchWithContextIsAppliedExactly() throws Exception {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,3 +1,3 @@\n" //
				+ " alpha\n" //
				+ "-beta\n" //
				+ "+BETA\n" //
				+ " gamma\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals("alpha\nBETA\ngamma\ndelta\nepsilon\n", applied(ORIGINAL, result));
	}

	@Test
	public void patchWithSeveralHunksIsApplied() throws Exception {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,2 +1,2 @@\n" //
				+ "-alpha\n" //
				+ "+ALPHA\n" //
				+ " beta\n" //
				+ "@@ -4,2 +4,2 @@\n" //
				+ " delta\n" //
				+ "-epsilon\n" //
				+ "+EPSILON\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals("ALPHA\nbeta\ngamma\ndelta\nEPSILON\n", applied(ORIGINAL, result));
	}

	@Test
	public void contextLinesAreMatchedIgnoringCaseAndSurroundingWhitespace() throws Exception {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,3 +1,3 @@\n" //
				+ "   ALPHA  \n" //
				+ "-beta\n" //
				+ "+BETA\n" //
				+ " gamma\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals("alpha\nBETA\ngamma\ndelta\nepsilon\n", applied(ORIGINAL, result));
	}

	@Test
	public void hunkHeaderWithTrailingContextTextIsTolerated() throws Exception {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,3 +1,3 @@ some trailing text\n" //
				+ " alpha\n" //
				+ "-beta\n" //
				+ "+BETA\n" //
				+ " gamma\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals("alpha\nBETA\ngamma\ndelta\nepsilon\n", applied(ORIGINAL, result));
	}

	@Test
	public void successReportListsTheAffectedLines() {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,3 +1,3 @@\n" //
				+ " alpha\n" //
				+ "-beta\n" //
				+ "+BETA\n" //
				+ " gamma\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		String report = result.getDiffPreview();
		assertTrue(report.startsWith("Here are the affected portions of the file after the patch is applied:"), report);
		assertTrue(report.contains(FILE), report);
		assertTrue(report.contains("BETA"), report);
	}

	@Test
	public void contextThatDoesNotMatchIsRejected() {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,2 +1,2 @@\n" //
				+ " completely different context line\n" //
				+ "-another line that is not there\n" //
				+ "+replacement\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().startsWith("Patch validation failed:"), result.getMessage());
	}

	@Test
	public void patchWithoutHunkHeaderIsRejected() {
		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, "just some prose, no diff at all");

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("empty patch"), result.getMessage());
	}

	@Test
	public void unknownFileIsRejected() {
		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange("Missing.txt",
				"--- a/x\n+++ b/x\n@@ -1,1 +1,1 @@\n-a\n+b");

		assertFalse(result.isSuccess());
		assertEquals("File not found: Missing.txt", result.getMessage());
	}

	@Test
	public void blankArgumentsAreRejected() {
		ApplyPatchTool tool = tool(ORIGINAL);
		assertFalse(tool.preparePatchChange("", "@@ -1,1 +1,1 @@\n-a\n+b").isSuccess());
		assertFalse(tool.preparePatchChange(FILE, "  ").isSuccess());
	}

	@Test
	public void preparedEditReplacesTheWholeDocument() {
		String patch = "--- a/notes.txt\n" //
				+ "+++ b/notes.txt\n" //
				+ "@@ -1,3 +1,3 @@\n" //
				+ " alpha\n" //
				+ "-beta\n" //
				+ "+BETA\n" //
				+ " gamma\n";

		ToolChangePreparationResult result = tool(ORIGINAL).preparePatchChange(FILE, patch);

		assertTrue(result.isSuccess(), result.getMessage());
		List<TextEdit> edits = result.getEdits();
		assertEquals(1, edits.size());
		assertEquals(0, edits.get(0).getOffset());
		assertEquals(ORIGINAL.length(), edits.get(0).getLength());
	}

	/**
	 * Regression guard: fuzzy line comparison has to ignore indentation on both
	 * sides. When it did not, no chunk of an indented source file could be verified
	 * and the tool reported success while leaving the content unchanged.
	 */
	@Test
	public void indentedSourceFileIsPatched() throws Exception {
		String indented = "class S {\n\tint v = 1;\n\tvoid r() {\n\t\tp(v);\n\t}\n}\n";
		String patch = "--- a/Indented.java\n" //
				+ "+++ b/Indented.java\n" //
				+ "@@ -1,3 +1,3 @@\n" //
				+ " class S {\n" //
				+ "-\tint v = 1;\n" //
				+ "+\tint v = 42;\n" //
				+ " \tvoid r() {\n";

		ApplyPatchTool tool = new ApplyPatchTool(new TestResourceAccess(Map.of("Indented.java", indented)));
		ToolChangePreparationResult result = tool.preparePatchChange("Indented.java", patch);

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals("class S {\n\tint v = 42;\n\tvoid r() {\n\t\tp(v);\n\t}\n}\n", applied(indented, result));
	}

	/**
	 * The replacement text keeps the indentation from the patch, not from the
	 * matched line, even though matching itself is whitespace insensitive.
	 */
	@Test
	public void replacementKeepsTheIndentationFromThePatch() throws Exception {
		String indented = "class S {\n\tint v = 1;\n\tvoid r() {\n\t\tp(v);\n\t}\n}\n";
		String patch = "--- a/Indented.java\n" //
				+ "+++ b/Indented.java\n" //
				+ "@@ -3,3 +3,3 @@\n" //
				+ " \tvoid r() {\n" //
				+ "-\t\tp(v);\n" //
				+ "+\t\tprint(v);\n" //
				+ " \t}\n";

		ApplyPatchTool tool = new ApplyPatchTool(new TestResourceAccess(Map.of("Indented.java", indented)));
		ToolChangePreparationResult result = tool.preparePatchChange("Indented.java", patch);

		assertTrue(result.isSuccess(), result.getMessage());
		String patched = applied(indented, result);
		assertTrue(patched.contains("\n\t\tprint(v);\n"), patched);
	}
}
