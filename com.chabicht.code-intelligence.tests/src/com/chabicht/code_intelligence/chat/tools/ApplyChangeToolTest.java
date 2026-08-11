package com.chabicht.code_intelligence.chat.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApplyChangeTool}. The diff preview it produces goes through
 * java-diff-utils, so these tests double as a regression guard for that
 * library.
 */
public class ApplyChangeToolTest {

	private static final String FILE = "Sample.java";

	private static final String ORIGINAL = "public class Sample {\n" //
			+ "\tint value = 1;\n" //
			+ "\n" //
			+ "\tvoid run() {\n" //
			+ "\t\tSystem.out.println(value);\n" //
			+ "\t}\n" //
			+ "}";

	private static ApplyChangeTool tool() {
		return new ApplyChangeTool(new TestResourceAccess(Map.of(FILE, ORIGINAL)));
	}

	private static String applied(ToolChangePreparationResult result) throws Exception {
		IDocument doc = new Document(ORIGINAL);
		for (TextEdit edit : result.getEdits()) {
			edit.apply(doc, TextEdit.NONE);
		}
		return doc.get();
	}

	@Test
	public void exactMatchIsReplaced() throws Exception {
		ToolChangePreparationResult result = tool().prepareChange(FILE, "l2:2", "\tint value = 1;",
				"\tint value = 42;");

		assertTrue(result.isSuccess(), result.getMessage());
		assertEquals(ORIGINAL.replace("value = 1;", "value = 42;"), applied(result));
	}

	@Test
	public void multiLineReplacement() throws Exception {
		ToolChangePreparationResult result = tool().prepareChange(FILE, "l4:6",
				"\tvoid run() {\n\t\tSystem.out.println(value);\n\t}", "\tvoid run() {\n\t\t// nothing\n\t}");

		assertTrue(result.isSuccess(), result.getMessage());
		String changed = applied(result);
		assertTrue(changed.contains("// nothing"), changed);
		assertFalse(changed.contains("System.out.println"), changed);
	}

	@Test
	public void successResultCarriesAUnifiedDiffPreview() {
		ToolChangePreparationResult result = tool().prepareChange(FILE, "l2:2", "\tint value = 1;",
				"\tint value = 42;");

		assertTrue(result.isSuccess(), result.getMessage());
		String preview = result.getDiffPreview();
		assertTrue(preview.contains("-\tint value = 1;"), preview);
		assertTrue(preview.contains("+\tint value = 42;"), preview);
	}

	@Test
	public void originalTextThatIsNotPresentIsRejected() {
		ToolChangePreparationResult result = tool().prepareChange(FILE, "l2:2", "this line does not exist anywhere",
				"whatever");

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().startsWith("Failed to locate or prepare change:"), result.getMessage());
	}

	@Test
	public void invalidLocationIsRejected() {
		ToolChangePreparationResult result = tool().prepareChange(FILE, "x1:2", "\tint value = 1;", "x");

		assertFalse(result.isSuccess());
		assertEquals("Invalid location string: x1:2", result.getMessage());
	}

	@Test
	public void unknownFileIsRejected() {
		ToolChangePreparationResult result = tool().prepareChange("Missing.java", "l1:1", "a", "b");

		assertFalse(result.isSuccess());
		assertEquals("File not found: Missing.java", result.getMessage());
	}

	@Test
	public void blankArgumentsAreRejected() {
		assertFalse(tool().prepareChange("", "l1:1", "a", "b").isSuccess());
		assertFalse(tool().prepareChange(FILE, "", "a", "b").isSuccess());
	}

	@Test
	public void generateDiffPreviewProducesUnifiedDiff() {
		IFile file = new TestFile(FILE, ORIGINAL);
		String preview = tool().generateDiffPreview("alpha\nbeta\ngamma", "alpha\nBETA\ngamma", file, 1);

		assertTrue(preview.contains("--- /test/" + FILE), preview);
		assertTrue(preview.contains("+++ /test/" + FILE), preview);
		assertTrue(preview.contains("@@ -1,3 +1,3 @@"), preview);
		assertTrue(preview.contains("-beta"), preview);
		assertTrue(preview.contains("+BETA"), preview);
		assertTrue(preview.contains(" alpha"), preview);
	}

	@Test
	public void generateDiffPreviewOffsetsHunkHeadersByStartLine() {
		IFile file = new TestFile(FILE, ORIGINAL);
		String preview = tool().generateDiffPreview("alpha\nbeta\ngamma", "alpha\nBETA\ngamma", file, 100);

		assertTrue(preview.contains("@@ -100,3 +100,3 @@"), preview);
	}

	@Test
	public void generateDiffPreviewOfIdenticalTextHasNoChangeLines() {
		IFile file = new TestFile(FILE, ORIGINAL);
		String preview = tool().generateDiffPreview("same\ntext", "same\ntext", file, 1);

		assertFalse(preview.contains("@@"), preview);
	}
}
