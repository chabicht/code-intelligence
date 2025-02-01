package com.chabicht.code_intelligence.completion;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.changelistener.LastEditsDocumentListener;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.CompletionPrompt;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class CodeIntelligenceCompletionProposalComputer implements IJavaCompletionProposalComputer {

	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("^(\\h*)");

	private Image completionIcon;

	public CodeIntelligenceCompletionProposalComputer() {
		ImageDescriptor imageDescriptor = ImageDescriptor.createFromFile(getClass(), "/icons/completion.png");
		completionIcon = imageDescriptor.createImage();
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext invocationContext,
			IProgressMonitor progressMonitor) {
		boolean debugPromptLoggingEnabled = isDebugPromptLoggingEnabled();
		StringBuilder debugPromptSB = new StringBuilder();

		try {
			System.out.println("CodeIntelligenceCompletionProposalComputer.computeCompletionProp(" + invocationContext
					+ "," + progressMonitor + ")");

			IDocument doc = invocationContext.getDocument();
			ITextSelection textSelection = invocationContext.getTextSelection();

			int selectionStartOffset = textSelection.getOffset();
			int selectionEndOffset = textSelection.getOffset() + textSelection.getLength();

			int startLine = textSelection.getStartLine();
			int endLine = textSelection.getEndLine();

			int ctxBeforeStartOffset = doc.getLineOffset(Math.max(0, startLine - 10));
			int selectedLinesStartOffset = doc.getLineOffset(doc.getLineOfOffset(textSelection.getOffset()));
			int selectedLinesEndOffset = doc
					.getLineOffset(doc.getLineOfOffset(textSelection.getOffset() + textSelection.getLength()) + 1);
			int ctxAfterEndOffset = doc.getLineOffset(Math.min(doc.getNumberOfLines() - 1, endLine + 10));

			int cursorOffset = invocationContext.getInvocationOffset();
			int lineOfCursor = doc.getLineOfOffset(cursorOffset);
			int lineOfCursorOffset = doc.getLineOffset(lineOfCursor);
			String currentLine = doc.get(selectedLinesStartOffset, selectedLinesEndOffset - selectedLinesStartOffset);

			boolean selectionEmpty = selectionStartOffset == selectionEndOffset;
			boolean cursorBeforeSelection = cursorOffset <= selectionStartOffset;
			boolean cursorInSelection = cursorOffset > selectionStartOffset && cursorOffset < selectionEndOffset;
			String contextStringWithTags = null;
			if (selectionEmpty) {
				String startToCursor = doc.get(ctxBeforeStartOffset, cursorOffset - ctxBeforeStartOffset);
				String cursorToEnd = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
				contextStringWithTags = startToCursor + "<<<cursor>>>" + cursorToEnd;
			} else if (cursorBeforeSelection) {
				String startToCursor = doc.get(ctxBeforeStartOffset, cursorOffset - ctxBeforeStartOffset);
				String cursorToSelection = doc.get(cursorOffset, selectionStartOffset - cursorOffset);
				String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
				String rest = doc.get(selectionEndOffset, ctxAfterEndOffset - selectionEndOffset);
				contextStringWithTags = startToCursor + "<<<cursor>>>" + cursorToSelection + "<<<selection_start>>>"
						+ selection + "<<<selection_end>>>" + rest;
			} else if (cursorInSelection) {
				String startToSelection = doc.get(ctxBeforeStartOffset, selectionStartOffset - ctxBeforeStartOffset);
				String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
				String selectionToCursor = doc.get(selectionEndOffset, cursorOffset - selectionEndOffset);
				String rest = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
				contextStringWithTags = startToSelection + "<<<selection_start>>>" + selection + "<<<selection_end>>>"
						+ selectionToCursor + "<<<cursor>>>" + rest;
			} else {
				String startToSelection = doc.get(ctxBeforeStartOffset, selectionStartOffset - ctxBeforeStartOffset);
				String selection = doc.get(selectionStartOffset, selectionEndOffset - selectionStartOffset);
				String selectionToCursor = doc.get(selectionEndOffset, cursorOffset - selectionEndOffset);
				String rest = doc.get(cursorOffset, ctxAfterEndOffset - cursorOffset);
				contextStringWithTags = startToSelection + "<<<selection_start>>>" + selection + "<<<selection_end>>>"
						+ selectionToCursor + "<<<cursor>>>" + rest;
			}

			String lastEdits = createLastEdits();

			CompletionPrompt completionPrompt = new CompletionPrompt(0f,
			// @formatter:off
					"""
					Complete the code beginning at the <<<cursor>>> position.
					A selection may be present, indicated by <<<selection_start>>> and <<<selection_end>>> markers.
					A completion always starts at the <<<cursor>>> marker, but it may span more than one line.

					Example 1:
					```
					public class Main {
					  public static void main(String[] args) {
					    String name = "John";
					    System.ou<<<cursor>>>
					  }
					}
					```
					Completion:
					```
					    System.out.println(name);
					```

					Example 2:
					```
					var model = configuration.getSelectedModel().orElseThrow();

					HttpClient client = HttpClient.newBuilder()
					                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
					                                  .build();

					String requestBody = getRequestBody(prompt, model);
					HttpRequest request = HttpRequest.newBuil<<<cursor>>>
					logger.info("Sending request to ChatGPT.\n\n" + requestBody);

					try
					{
					        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

					        if (response.statusCode() != 200)

					```
					Completion:
					```
					HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
					    .timeout( Duration.ofSeconds( configuration.getRequestTimoutSeconds() ) )
					    .version(HttpClient.Version.HTTP_1_1)
					                .header("Authorization", "Bearer " + model.apiKey())
					                .header("Accept", "text/event-stream")
					                .header("Content-Type", "application/json")
					                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
					                .build();
					```

					Example 3:
					```
					layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
					layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
					layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);

					Composite composite = new Composite(parent, SWT.NONE);
					composite.setLayout(layout);
					composite.setLayoutData(<<<selection_start>>>new GridData(GridData.FILL_BOTH)<<<cursor>>><<<selection_end>>>);
					applyDialogFont(composite);

					Label lblName = new Label(composite, SWT.NONE);
					lblName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
					lblName.setText("Name:");
					```
					Completion:
					```
					GridData layoutData = new GridData(GridData.FILL_BOTH);
					layoutData.widthHint = 300;
					composite.setLayoutData(layoutData);
					```

					Here is a list of the most recent edits made by the user:
					%s

					Important details:
					- This is Java 17 code.
					- Do not repeat the context in your answer.
					- Include the current line until the <<<cursor>>> marker in your answer.
					- Focus on relevant variables and methods from the context provided.
					- If the code can be completed logically in 1-10 lines, do so; otherwise, finalize the snippet where it makes sense.
					- If the context before the current line ends with a comment, implement what the comment intends to do.
					- Use the provided last edits by the user to guess what might be an appropriate completion here.
					- Output only the completion snippet (no extra explanations, no markdown, not the whole program again).

					Now do this for this code:
					```
					%s
					```
					Completion:
					""",
					// @formatter:on
					new Object[] { lastEdits, contextStringWithTags });

			if (debugPromptLoggingEnabled) {
				debugPromptSB.append("Prompt \"" + StringUtils.trim(currentLine) + "\"");
				debugPromptSB.append("\n===================================================\n");
				debugPromptSB.append(completionPrompt.compile()).append("\n");
				debugPromptSB.append("===================================================\n");
			}

			CompletionResult completionResult = ConnectionFactory.forCompletions().complete(completionPrompt);

			if (debugPromptLoggingEnabled) {
				debugPromptSB.append("Completion:\n").append("===================================================\n");
				debugPromptSB.append(completionResult.getCompletion()).append("\n");
				debugPromptSB.append("===================================================\n");
			}

			String completion = completionResult.getCompletion();

			CodeIntelligenceCompletionProposal res = new CodeIntelligenceCompletionProposal(completion,
					lineOfCursorOffset, cursorOffset - lineOfCursorOffset, completionIcon,
					completionResult.getCaption(), 10000);

			return List.of(res);
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		} finally {
			if (debugPromptLoggingEnabled) {
				Activator.logInfo(debugPromptSB.toString());
			}
		}
	}

	private boolean isDebugPromptLoggingEnabled() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS);
	}

	private String createLastEdits() {
		StringBuilder sb = new StringBuilder();
		for (String edit : LastEditsDocumentListener.getInstance().getLastEdits()) {
			sb.append("```\n").append(edit).append("```\n\n");
		}
		return sb.toString();
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext invocationContext,
			IProgressMonitor progressMonitor) {
		System.out.println("CodeIntelligenceCompletionProposalComputer.computeContextInformation(" + invocationContext
				+ "," + progressMonitor + ")");
		return Collections.emptyList();
	}

	@Override
	public String getErrorMessage() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.getErrorMessage()");
		return null;
	}

	@Override
	public void sessionEnded() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.sessionEnded()");
	}

	@Override
	public void sessionStarted() {
		System.out.println("CodeIntelligenceCompletionProposalComputer.sessionStarted()");
	}

}
