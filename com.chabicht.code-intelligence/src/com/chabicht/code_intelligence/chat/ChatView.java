package com.chabicht.code_intelligence.chat;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.core.databinding.observable.list.IListChangeListener;
import org.eclipse.core.databinding.observable.list.ListDiffEntry;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.Tuple;
import com.chabicht.code_intelligence.apiclient.AiModelConnection;
import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.RangeType;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.ChatHistoryEntry;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.code_intelligence.util.CodeUtil;
import com.chabicht.code_intelligence.util.MarkdownUtil;
import com.chabicht.code_intelligence.util.ModelUtil;
import com.chabicht.code_intelligence.util.ThemeUtil;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ChatView extends ViewPart {
	private static final Pattern PATTERN_THINK_START = Pattern.compile("<think>|<\\|begin_of_thought\\|>|<thought>");
	private static final Pattern PATTERN_THINK_END = Pattern.compile("<[/]think>|<\\|end_of_thought\\|>|</thought>");
	private static final Pattern PATTERN_TAGS_TO_REMOVE = Pattern
			.compile("<\\|begin_of_solution\\|>|<\\|end_of_solution\\|>");

	private static final int MIN_UPPER_HEIGHT = 90;
	private static final int MIN_LOWER_HEIGHT = 130;
	private static final int BUTTON_SIZE = 40;
	private static final int ATTACHMENT_COMP_HEIGHT = 30;

	private static final WritableList<MessageContext> externallyAddedContext = new WritableList<>();

	private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

	private final ChatSettings settings = new ChatSettings();

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private ChatComponent chat;

	private TextViewer tvUserInput;
	private IDocument userInput;

	private Font buttonSymbolFont;
	private Image paperclipImage;
	private String paperclipBase64;

	private ChatConversation conversation;

	private Parser markdownParser = MarkdownUtil.createParser();
	private HtmlRenderer markdownRenderer = MarkdownUtil.createRenderer();

	private Button btnSend;
	private AiModelConnection connection;

	private Composite cmpAttachments;
	private Button btnSettings;
	private Button btnHistory;

	private FunctionCallSession functionCallSession = new FunctionCallSession();

	private ChatListener chatListener = new ChatListener() {

		@Override
		public void onMessageUpdated(ChatMessage message) {
			String messageHtml = messageContentToHtml(message);
			Display.getDefault().asyncExec(() -> {
				chat.updateMessage(message.getId(), messageHtml);
			});
		}

		@Override
		public void onMessageAdded(ChatMessage message, boolean updating) {
			Display.getDefault().asyncExec(() -> {
				StringBuilder attachments = new StringBuilder();
				if (!message.getContext().isEmpty()) {
					String attachmentIcon = getAttachmentIconHtml();
					for (MessageContext ctx : message.getContext()) {
						attachments.append(String.format(
								"<span id=\"%s\" class=\"attachment-container\">"
										+ "<span class=\"attachment-icon\">%s</span>"
										+ "<span class=\"tooltip\">%s</span>" + "</span>",
								ctx.getUuid(), attachmentIcon, ctx.getLabel()));
					}
				}
				String messageHtml = messageContentToHtml(message);
				String combinedHtml = messageHtml + "\n" + attachments.toString();

				if (Role.SYSTEM.equals(message.getRole())) {
					combinedHtml = "<details><summary>System Prompt</summary>" + combinedHtml + "</details>";
				}

				final String finalHtml = combinedHtml;
				Display.getDefault().asyncExec(() -> {
					chat.addMessage(message.getId(), message.getRole().name().toLowerCase(), finalHtml, updating);
				});
			});
		}

		@Override
		public void onFunctionCall(ChatMessage message) {
			functionCallSession.handleFunctionCall(message);
			onMessageUpdated(message);
		}

		private String getAttachmentIconHtml() {
			String dataUrl = "data:image/png;base64," + paperclipBase64;
			return String.format("<img src=\"%s\" style=\"width: 15px; height: 25px;\"/>", dataUrl);
		}

		private String messageContentToHtml(ChatMessage message) {
			MessageContentWithReasoning thoughtsAndMessage = splitThoughtsFromMessage(message);

			String thinkHtml = "";
			if (StringUtils.isNotBlank(thoughtsAndMessage.getThoughts())) {
				thinkHtml = String.format("<details%s><summary>%s</summary><blockquote>%s</blockquote></details>",
						thoughtsAndMessage.isEndOfReasoningReached() ? "" : " open",
						thoughtsAndMessage.isEndOfReasoningReached() ? "Thoughts" : "Thinking...",
						markdownRenderer.render(markdownParser.parse(thoughtsAndMessage.getThoughts())));
			}
			
			String functionCallHtml="";
			if(message.getFunctionCall().isPresent()) {
				FunctionCall call = message.getFunctionCall().get();
				FunctionResult result = message.getFunctionResult()
						.orElse(new FunctionResult(call.getId(), call.getFunctionName(), null));
				String argsCodeBlock = "```json\n" + call.getArgsJson() + "\n```";
				String resultCodeBlock = "```json\n" + result.getResultJson() + "\n```";
				functionCallHtml = String.format(
						"<details><summary>Function call: %s</summary>" //
								+ "<b>Args:</b><blockquote>%s</blockquote>" //
								+ "<b>Result:</b><blockquote>%s</blockquote>" //
						+ "</details>",
						call.getFunctionName(), markdownRenderer.render(markdownParser.parse(argsCodeBlock)),
						markdownRenderer.render(markdownParser.parse(resultCodeBlock)));
			}
			
			String messageHtml = markdownRenderer.render(markdownParser.parse(thoughtsAndMessage.getMessage()));
			String combinedHtml = thinkHtml + messageHtml + functionCallHtml;
			return combinedHtml;
		}

		@Override
		public void onChatResponseFinished(ChatMessage message) {
			Display.getDefault().asyncExec(() -> {
				if (message.getFunctionResult().isEmpty()) {
					// Set text to "▶️"
					btnSend.setText("\u25B6");

					connection = null;

					addConversationToHistory();

					if (isDebugPromptLoggingEnabled()) {
						Activator.logInfo(conversation.toString());
					}

					// Apply pending changes after all function calls are done.
					if (functionCallSession.hasPendingChanges()) {
						functionCallSession.applyPendingChanges();
					}
				} else {
					sendFunctionResult(message);
				}

				Display.getDefault().asyncExec(() -> {
					chat.markMessageFinished(message.getId());
				});
			});
		}
	};

	private void sendFunctionResult(ChatMessage message) {
		if (connection.isChatPending()) {
			connection.abortChat();
		}

		Display.getDefault().asyncExec(() -> {
			chat.markMessageFinished(message.getId());
		});

		connection.chat(conversation, settings.getMaxResponseTokens());
	}

	private static class MessageContentWithReasoning {
		private final String thoughts;
		private final String message;
		private final boolean endOfReasoningReached;

		public MessageContentWithReasoning(String thoughts, String message, boolean endOfReasoningReached) {
			this.thoughts = thoughts;
			this.message = message;
			this.endOfReasoningReached = endOfReasoningReached;
		}

		public String getThoughts() {
			return thoughts;
		}

		public String getMessage() {
			return message;
		}

		public boolean isEndOfReasoningReached() {
			return endOfReasoningReached;
		}
	};

	private void addConversationToHistory() {
		addCaptionForConversationInBackgroundAndAddToHistory();
	}

	private void addCaptionForConversationInBackgroundAndAddToHistory() {
		if (StringUtils.isBlank(conversation.getCaption())) {
			executorService.submit(() -> { // Use a thread pool
				try {
					String combinedMessages = conversation.getMessages().stream().map(ChatMessage::getContent)
							.collect(Collectors.joining("\n"));

					String caption = ConnectionFactory.forCompletions().caption(combinedMessages);

					// Replace all line breaks in the caption, then replace all multiple
					// white spaces to " ".
					caption = caption.replace("\n", " ").replaceAll("\\s+", " ");

					conversation.setCaption(caption);

					// Update the chat history *after* the caption is set. Crucially, this must
					// happen *after* the setCaption call, and still within the background thread.
					Activator.getDefault().addOrUpdateChatHistory(conversation);

				} catch (Exception e) {
					Activator.logError(e.getMessage(), e);
					// Consider adding more specific error handling here. For example, you might
					// want to set a default caption, or display an error message to the user.
				}
			});
		} else {
			// If there is already caption, update the history.
			Activator.getDefault().addOrUpdateChatHistory(conversation);
		}
	}

	private boolean isDebugPromptLoggingEnabled() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS);
	}

	public ChatView() {
		buttonSymbolFont = resources.create(JFaceResources.getDefaultFontDescriptor().setHeight(18));

		paperclipImage = resources.create(ImageDescriptor.createFromFile(this.getClass(),
				String.format("/icons/paperclip_%s.png", ThemeUtil.isDarkTheme() ? "dark" : "light")));
		createPaperclipBase64();
	}

	@Override
	public void createPartControl(Composite parent) {
		// Use FormLayout for the main container
		FormLayout formLayout = new FormLayout();
		formLayout.marginWidth = 0;
		formLayout.marginHeight = 0;
		formLayout.spacing = 0;

		final Composite outer = new Composite(parent, SWT.NONE);
		outer.setLayout(formLayout);
		outer.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create()); // Use parent's layout data if
																						// needed

		// --- Upper part: Chat and buttons ---
		Composite upperComposite = new Composite(outer, SWT.NONE);
		// Use GridLayout for the upper part's internal layout
		GridLayout gl_upperComposite = new GridLayout(2, false);
		gl_upperComposite.marginHeight = 0;
		gl_upperComposite.marginWidth = 0; // Add marginWidth 0
		upperComposite.setLayout(gl_upperComposite);

		chat = new ChatComponent(upperComposite, SWT.BORDER);
		GridData gd_chat = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 2);
		chat.setLayoutData(gd_chat);

		Button btnClear = new Button(upperComposite, SWT.NONE);
		btnClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clearChat();
			}
		});
		GridData gd_btnClear = new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1); // Fill horizontally
		gd_btnClear.heightHint = BUTTON_SIZE;
		gd_btnClear.widthHint = BUTTON_SIZE;
		btnClear.setLayoutData(gd_btnClear);
		btnClear.setToolTipText("Clear conversation");
		btnClear.setText("\uD83E\uDDF9"); // Broom 🧹
		btnClear.setFont(buttonSymbolFont);

		btnHistory = new Button(upperComposite, SWT.NONE);
		btnHistory.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				List<ChatHistoryEntry> chatHistory = Activator.getDefault().loadChatHistory();
				ChatHistoryDialog dlg = new ChatHistoryDialog(getSite().getShell(), chatHistory);
				if (dlg.open() == Dialog.OK) {
					ChatHistoryEntry entry = dlg.getSelectedEntry();
					if (entry != null) {
						ChatConversation c = entry.getConversation();
						if (dlg.getResultMode() == ChatHistoryDialog.ResultMode.REUSE_AS_NEW) {
							c.setConversationId(null);
							c.setCaption(null);
						}
						replaceChat(c);
					}
				}
			}
		});
		GridData gd_btnHistory = new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1); // Fill horizontally
		gd_btnHistory.widthHint = BUTTON_SIZE;
		gd_btnHistory.heightHint = BUTTON_SIZE;
		btnHistory.setLayoutData(gd_btnHistory);
		btnHistory.setText("\uD83D\uDCDC"); // Scroll 📜
		btnHistory.setToolTipText("Recent Conversations");
		btnHistory.setFont(buttonSymbolFont);

		// --- Sash ---
		final Sash sash = new Sash(outer, SWT.HORIZONTAL);

		// --- Lower part: Input and buttons ---
		Composite lowerComposite = new Composite(outer, SWT.NONE);
		// Use GridLayout for the lower part's internal layout
		GridLayout gl_lowerComposite = new GridLayout(2, false);
		gl_lowerComposite.marginBottom = 5;
		gl_lowerComposite.marginHeight = 0;
		gl_lowerComposite.marginWidth = 0; // Add marginWidth 0
		lowerComposite.setLayout(gl_lowerComposite);

		cmpAttachments = new Composite(lowerComposite, SWT.NONE);
		GridData gd_cmpAttachments = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		gd_cmpAttachments.heightHint = ATTACHMENT_COMP_HEIGHT;
		cmpAttachments.setLayoutData(gd_cmpAttachments);
		RowLayout layoutCmpAttachments = new RowLayout(SWT.HORIZONTAL);
		layoutCmpAttachments.marginTop = 0;
		layoutCmpAttachments.marginBottom = 0;
		layoutCmpAttachments.center = true; // Center items vertically
		cmpAttachments.setLayout(layoutCmpAttachments);
		new Label(lowerComposite, SWT.NONE); // empty label for the second column

		tvUserInput = new TextViewer(lowerComposite, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI | SWT.WRAP); // Added
		GridData gridDataTvUserInput = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 2); // Span 1 column, 2 rows
		tvUserInput.getTextWidget().setLayoutData(gridDataTvUserInput);

		btnSettings = new Button(lowerComposite, SWT.NONE);
		btnSettings.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ChatSettingsDialog dlg = new ChatSettingsDialog(Display.getCurrent().getActiveShell(), settings);
				if (dlg.open() == Dialog.OK) {
					try {
						BeanUtils.copyProperties(settings, dlg.getSettings());
					} catch (IllegalAccessException | InvocationTargetException ex) {
						Activator.logError(ex.getMessage(), ex);
					}
				}
			}
		});
		GridData gd_btnSettings = new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1);
		gd_btnSettings.widthHint = BUTTON_SIZE;
		gd_btnSettings.heightHint = BUTTON_SIZE;
		btnSettings.setLayoutData(gd_btnSettings);
		btnSettings.setText("\u2699"); // Gear ⚙️
		btnSettings.setToolTipText("Settings");
		btnSettings.setFont(buttonSymbolFont);

		// Send button (moved below settings button)
		btnSend = new Button(lowerComposite, SWT.NONE);
		btnSend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				sendMessageOrAbortChat();
			}
		});
		// Place send button bottom-right of its cell
		GridData gd_btnSend = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1);
		gd_btnSend.heightHint = BUTTON_SIZE;
		gd_btnSend.widthHint = BUTTON_SIZE;
		btnSend.setLayoutData(gd_btnSend);
		btnSend.setToolTipText("Send message (Ctrl + Enter)");
		btnSend.setText("\u25B6"); // Play ▶️
		btnSend.setFont(buttonSymbolFont);

		// --- Configure FormLayout Attachments ---

		FormData fd_upperComposite = new FormData();
		fd_upperComposite.top = new FormAttachment(0, 0); // Attach to top of outer
		fd_upperComposite.left = new FormAttachment(0, 0); // Attach to left of outer
		fd_upperComposite.right = new FormAttachment(100, 0); // Attach to right of outer
		fd_upperComposite.bottom = new FormAttachment(sash, 0, SWT.TOP); // Attach bottom to sash top
		upperComposite.setLayoutData(fd_upperComposite);

		FormData fd_lowerComposite = new FormData();
		fd_lowerComposite.top = new FormAttachment(sash, 0, SWT.BOTTOM); // Attach top to sash bottom
		fd_lowerComposite.left = new FormAttachment(0, 0); // Attach to left of outer
		fd_lowerComposite.right = new FormAttachment(100, 0); // Attach to right of outer
		fd_lowerComposite.bottom = new FormAttachment(100, 0); // Attach to bottom of outer
		lowerComposite.setLayoutData(fd_lowerComposite);

		// Initial position of the sash: Start at 70% down (example)
		final FormData fd_sash = new FormData();
		fd_sash.top = new FormAttachment(70); // Example: Start at 70% down
		fd_sash.left = new FormAttachment(0, 0);
		fd_sash.right = new FormAttachment(100, 0);
		sash.setLayoutData(fd_sash);

		// --- Sash Listener for Resizing ---
		sash.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				// The event.y is the proposed new top coordinate of the sash within the outer
				// composite.
				int outerHeight = outer.getClientArea().height;
				int sashHeight = getSashHeight(sash);
				int proposedTop = event.y;
				int newSashTop = calculateConstrainedSashTop(proposedTop, outerHeight, sashHeight);
				applySashPositionUpdate(sash, outer, newSashTop, false); // Apply immediately for drag
			}
		});

		// --- Outer Composite Resize Listener ---
		outer.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				// Recalculate sash position to prioritize keeping the lower composite
				// at least MIN_LOWER_HEIGHT, letting the upper composite resize,
				// while still respecting MIN_UPPER_HEIGHT.
				int outerHeight = outer.getClientArea().height;
				int sashHeight = getSashHeight(sash);

				// Calculate the target top position for the sash to keep the lower part at its
				// minimum height
				int targetSashTop = outerHeight - MIN_LOWER_HEIGHT - sashHeight;

				// Determine the new sash top position, constrained by min/max heights.
				int newSashTop = calculateConstrainedSashTop(targetSashTop, outerHeight, sashHeight);

				// Apply the update, using asyncExec to avoid potential layout loops during
				// resize storm.
				applySashPositionUpdate(sash, outer, newSashTop, true);
			}
		});

		// --- Initialization ---
		init();
		initUserInputControl();
		initListeners();

		conversation = createNewChatConversation();
		conversation.addListener(chatListener);

		// Initial layout trigger after controls are created
		Display.getCurrent().asyncExec(() -> {
			if (!outer.isDisposed()) {
				// Trigger the resize listener logic to set the initial sash position correctly
				// based on the new priority (lower height fixed, upper resizes).
				outer.layout(true, true);
			}
		});
	}

	/**
	 * Calculates a reasonable height for the sash, providing a default if needed.
	 * 
	 * @param sash The sash control.
	 * @return The sash height.
	 */
	private int getSashHeight(Sash sash) {
		int sashHeight = sash.getSize().y;
		// Ensure sashHeight is reasonable if not yet sized
		if (sashHeight <= 0) {
			sashHeight = 5; // Default sash height estimate
		}
		return sashHeight;
	}

	/**
	 * Calculates the constrained top position for the sash based on minimum
	 * heights.
	 *
	 * @param proposedTop The desired top position (e.g., from dragging or
	 *                    calculation).
	 * @param outerHeight The current height of the container holding the sash and
	 *                    composites.
	 * @param sashHeight  The height of the sash itself.
	 * @return The calculated top position, clamped within the allowed bounds.
	 */
	private int calculateConstrainedSashTop(int proposedTop, int outerHeight, int sashHeight) {
		// Calculate constraints based on outer height and minimum pane heights
		int minSashTop = MIN_UPPER_HEIGHT;
		int maxSashTop = outerHeight - MIN_LOWER_HEIGHT - sashHeight;

		// Clamp the proposed position
		int newSashTop = Math.max(minSashTop, Math.min(proposedTop, maxSashTop));

		// Ensure the calculated position is valid even in extreme cases (very small
		// outerHeight)
		// If min heights conflict (MIN_UPPER + MIN_LOWER + sash > outerHeight),
		// prioritize MIN_UPPER.
		if (newSashTop > maxSashTop) {
			newSashTop = minSashTop;
		}
		// Final clamp to handle potential negative results if mins are very large
		newSashTop = Math.max(minSashTop, Math.min(newSashTop, maxSashTop));

		return newSashTop;
	}

	/**
	 * Applies the calculated sash position update if it has changed.
	 *
	 * @param sash       The sash control.
	 * @param outer      The containing composite that needs re-layout.
	 * @param newSashTop The new top position (offset from the top) for the sash.
	 * @param useAsync   Whether to perform the layout update asynchronously
	 *                   (recommended for resize events).
	 */
	private void applySashPositionUpdate(Sash sash, Composite outer, int newSashTop, boolean useAsync) {
		if (sash == null || sash.isDisposed() || outer == null || outer.isDisposed()) {
			return; // Don't proceed if controls are disposed
		}

		FormData currentSashData = (FormData) sash.getLayoutData();
		if (currentSashData == null || currentSashData.top == null) {
			// Layout data might not be fully initialized yet, skip adjustment
			// Or, if it's null, create a new one (though it should exist)
			if (currentSashData == null) {
				currentSashData = new FormData();
				sash.setLayoutData(currentSashData);
			}
			// Set initial attachment relative to top
			currentSashData.top = new FormAttachment(0, newSashTop);
			currentSashData.left = new FormAttachment(0, 0); // Ensure left/right are set
			currentSashData.right = new FormAttachment(100, 0);
			performLayout(outer, useAsync);
			return;
		}

		// Get the current sash top offset if it's already set relative to the top
		// (numerator == 0)
		int currentSashTopOffset = -1;
		if (currentSashData.top.numerator == 0) {
			currentSashTopOffset = currentSashData.top.offset;
		}

		// Only update layout if the calculated position is different from the current
		// one,
		// or if the current attachment isn't relative to the top (needs fixing).
		if (newSashTop != currentSashTopOffset || currentSashData.top.numerator != 0) {
			// Update sash position using FormAttachment relative to top (0)
			currentSashData.top = new FormAttachment(0, newSashTop);
			// Re-layout the outer composite to apply the change
			performLayout(outer, useAsync);
		}
	}

	/**
	 * Performs the layout operation, optionally using asyncExec.
	 * 
	 * @param control  The control to layout.
	 * @param useAsync True to use asyncExec, false otherwise.
	 */
	private void performLayout(final Composite control, boolean useAsync) {
		if (control == null || control.isDisposed()) {
			return;
		}
		if (useAsync) {
			Display.getCurrent().asyncExec(() -> {
				if (!control.isDisposed()) {
					control.layout(true, true);
				}
			});
		} else {
			control.layout(true, true);
		}
	}

	private void initUserInputControl() {
		userInput = new Document();
		tvUserInput.setDocument(userInput);

		TextViewerUndoManager undoManager = new TextViewerUndoManager(50);
		tvUserInput.setUndoManager(undoManager);
		undoManager.connect(tvUserInput);

		tvUserInput.getTextWidget().addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if ((e.stateMask & SWT.CTRL) != 0) {
					switch (e.keyCode) {
					case SWT.CR:
					case SWT.KEYPAD_CR:
						sendMessageOrAbortChat();
						e.doit = false;
						break;
					case 'z':
					case 'Z':
						if (undoManager.undoable()) {
							undoManager.undo();
						}
						e.doit = false;
						break;
					case 'y':
					case 'Y':
						if (undoManager.redoable()) {
							undoManager.redo();
						}
						e.doit = false;
						break;
					}
				}
			}
		});
	}

	private void init() {
		String defaultModel = Activator.getDefault().getPreferenceStore()
				.getString(PreferenceConstants.CHAT_MODEL_NAME);
		settings.setModel(defaultModel);

		if (StringUtils.isNotBlank(defaultModel)) {
			Optional<Tuple<String, String>> modelOpt = ModelUtil.getProviderModelTuple(defaultModel);
			modelOpt.ifPresent(m -> {
				String connectionName = m.getFirst();
				String modelId = m.getSecond();
				Activator.getDefault().loadPromptTemplates().stream()
						.filter(pt -> PromptType.CHAT.equals(pt.getType()) && pt.isUseByDefault()
								&& pt.isApplicable(connectionName, modelId))
						.findFirst().ifPresent(settings::setPromptTemplate);
			});
		}
	}

	private void initListeners() {
		Activator.getDefault().addPropertyChangeListener("configuration", e -> {
			// Reset settings, e.g. the chat model.
			init();
		});
		IListChangeListener<? super MessageContext> listChangeListener = e -> {
			for (ListDiffEntry<? extends MessageContext> diff : e.diff.getDifferences()) {
				MessageContext ctx = diff.getElement();
				if (diff.isAddition()) {
					Label l = new Label(cmpAttachments, SWT.NONE);
					l.setToolTipText(ctx.getLabel());
					l.setData(ctx);
					l.setImage(paperclipImage);
					l.setLayoutData(new RowData(15, 25));

					l.addMenuDetectListener(event -> {
						// Context menu for the message context labels
						Menu contextMenu = new Menu(cmpAttachments.getShell(), SWT.POP_UP);
						MenuItem deleteItem = new MenuItem(contextMenu, SWT.NONE);
						deleteItem.setText("Delete");
						deleteItem.addListener(SWT.Selection, evt -> {
							if (evt.widget == null && evt.widget.isDisposed()) {
								return;
							}

							if (evt.widget.getData() instanceof MessageContext context) {
								externallyAddedContext.remove(context);
								cmpAttachments.layout();
							}
						});

						deleteItem.setData(l.getData());
						contextMenu.setLocation(event.x, event.y);
						contextMenu.setVisible(true);
					});

					l.addMouseListener(MouseListener.mouseDoubleClickAdapter(ev -> {
						MessageContextDialog dlg = new MessageContextDialog(getSite().getShell(), ctx);
						dlg.open();
					}));

					cmpAttachments.layout();
				} else {
					removeAttachmentLabel(ctx);
				}
			}
		};
		externallyAddedContext.addListChangeListener(listChangeListener);

		// Add context menu listener to the attachment composite itself
		cmpAttachments.addMenuDetectListener(event -> {
			Menu contextMenu = new Menu(cmpAttachments.getShell(), SWT.POP_UP);
			MenuItem clearAllItem = new MenuItem(contextMenu, SWT.NONE);
			clearAllItem.setText("Clear All");
			clearAllItem.addListener(SWT.Selection, evt -> {
				if (!externallyAddedContext.isEmpty()) {
					externallyAddedContext.clear();
				}
			});
			// Disable if list is already empty
			clearAllItem.setEnabled(!externallyAddedContext.isEmpty());

			contextMenu.setLocation(event.x, event.y);
			contextMenu.setVisible(true);
		});

		chat.addProgressListener(ProgressListener.completedAdapter(event -> {
			final Browser bChat = chat.getBrowser();
			final BrowserFunction function = new OnClickFunction(bChat, "elementClicked");
			bChat.execute(ONCLICK_LISTENER);
//			bChat.addLocationListener(new LocationAdapter() {
//				@Override
//				public void changed(LocationEvent event) {
//					bChat.removeLocationListener(this);
//					function.dispose();
//				}
//			});
		}));

		// Add this LocationListener to intercept link clicks
		chat.addLocationListener(new LocationAdapter() {
			@Override
			public void changing(LocationEvent event) {
				String url = event.location;
				if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
					// Prevent navigation within the embedded browser
					event.doit = false;

					// Open the link in the default system browser
					if (MessageDialog.openConfirm(Display.getCurrent().getActiveShell(), "Open Hyperlink?", String
							.format("Do you want to open the link to %s in the system's default browser?", url))) {
						Program.launch(url);
					}
				}
			}
		});
	}

	private void removeAttachmentLabel(MessageContext ctx) {
		if (cmpAttachments != null && !cmpAttachments.isDisposed() && cmpAttachments.getChildren() != null) {
			Control[] children = cmpAttachments.getChildren();
			for (int i = children.length - 1; i >= 0; i--) {
				Control child = children[i];
				if (child.getData() == ctx) {
					child.dispose();
				}
			}
		}
	}

	@Override
	public void setFocus() {
		if (tvUserInput != null && tvUserInput.getTextWidget() != null && !tvUserInput.getTextWidget().isDisposed()) {
			tvUserInput.getTextWidget().setFocus();
		}
	}

	private void sendMessageOrAbortChat() {
		if (connection == null) {
			connection = ConnectionFactory.forChat(settings.getModel());
		}
		if (connection.isChatPending()) {
			connection.abortChat();

			chat.markAllMessagesFinished();

			// Set text to "▶️"
			btnSend.setText("\u25B6");

			addConversationToHistory();

			connection = null;
		} else {
			ChatMessage chatMessage = new ChatMessage(Role.USER, userInput.get());

			String consoleSelection = ConsolePageParticipant.getSelectedText();
			if (StringUtils.isNotBlank(consoleSelection)) {
				Point selectionRange = Optional.ofNullable(ConsolePageParticipant.getSelectionRange())
						.orElse(new Point(0, 0));
				String consoleName = Optional.ofNullable(ConsolePageParticipant.getConsoleName()).orElse("Console Log");
				externallyAddedContext.add(new MessageContext(consoleName, RangeType.OFFSET, selectionRange.x,
						selectionRange.x + selectionRange.y, consoleSelection));
			}

			externallyAddedContext.forEach(ctx -> addContextToMessageIfNotDuplicate(chatMessage, ctx.getFileName(),
					ctx.getRangeType(), ctx.getStart(), ctx.getEnd(), ctx.getContent()));
			externallyAddedContext.clear();
			addSelectionAsContext(chatMessage);

			conversation.getOptions().put(REASONING_ENABLED, settings.isReasoningEnabled());
			conversation.getOptions().put(REASONING_BUDGET_TOKENS, settings.getReasoningTokens());

			conversation.addMessage(chatMessage, true);
			connection.chat(conversation, settings.getMaxResponseTokens());
			userInput.set("");

			// Set text to "⏹️"
			btnSend.setText("\u23F9");
		}
	}

	public void editChat(String messageUuidString) {
		Display.getDefault().syncExec(() -> {
			// Treat the conversation as new history-wise.
			conversation.setConversationId(null);

			UUID messageUuid = UUID.fromString(messageUuidString);
			if (connection == null || !connection.isChatPending()) {
				getExternallyAddedContext().clear();
				ChatConversation oldConvo = conversation;

				List<ChatMessage> messages = oldConvo.getMessages();
				ChatMessage msgToEdit = null;
				for (int i = messages.size() - 1; i >= 0; i--) {
					ChatMessage msg = messages.get(i);
					if (msgToEdit == null) {
						messages.remove(i);
					}
					if (messageUuid.equals(msg.getId())) {
						msgToEdit = msg;
						break;
					}
				}

				replaceChat(oldConvo);

				userInput.set(msgToEdit.getContent());
				getExternallyAddedContext().addAll(msgToEdit.getContext());
			}
		});
	}

	private void addSelectionAsContext(ChatMessage chatMessage) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			return;
		}
		IEditorPart editor = page.getActiveEditor();
		if (editor == null || !(editor instanceof ITextEditor)) {
			return;
		}
		ITextEditor textEditor = (ITextEditor) editor;
		ISelectionProvider selectionService = textEditor.getSelectionProvider();
		ISelection selection = selectionService.getSelection();
		if (!selection.isEmpty() && selection instanceof ITextSelection textSelection) {
			String selectedText = textSelection.getText();
			if (StringUtils.isBlank(selectedText)) {
				return;
			}

			String fileName = textEditor.getEditorInput().getName();
			int startLine = textSelection.getStartLine();
			int endLine = textSelection.getEndLine();
			String processedText = CodeUtil.removeCommonIndentation(selectedText);
			addContextToMessageIfNotDuplicate(chatMessage, fileName, RangeType.LINE, startLine + 1, endLine + 1,
					processedText);
		}
	}

	public void addContextToMessageIfNotDuplicate(ChatMessage chatMessage, String fileName, RangeType rangeType,
			int start, int end, String selectedText) {
		boolean duplicate = false;
		// Process the selected text to remove common indentation
		String processedText = CodeUtil.removeCommonIndentation(selectedText);
		MessageContext newCtx = new MessageContext(fileName, rangeType, start, end, processedText);
		for (MessageContext ctx : chatMessage.getContext()) {
			if (newCtx.isDuplicate(ctx)) {
				duplicate = true;
			}
		}
		if (!duplicate) {
			chatMessage.getContext().add(newCtx);
		}
	}

	private void clearChatInternal(ChatConversation replacement) {
		if (connection != null) {
			connection.abortChat();
			connection = null;
		}
		conversation.removeListener(chatListener);
		conversation = replacement;
		conversation.addListener(chatListener);
		externallyAddedContext.clear();
		chat.reset();
		userInput.set("");

		if (!replacement.getMessages().isEmpty()) {
			ProgressAdapter listener = new ProgressAdapter() {
				@Override
				public void completed(ProgressEvent event) {
					List<ChatMessage> messages = new ArrayList<>(replacement.getMessages());
					messages.forEach(m -> conversation.notifyMessageAdded(m, false));
					chat.removeProgressListener(this);
				}
			};
			chat.addProgressListener(listener);
		}
	}

	private void clearChat() {
		clearChatInternal(createNewChatConversation());
	}

	private void replaceChat(ChatConversation replacement) {
		clearChatInternal(replacement);

		conversation.getOptions().clear();
		conversation.getOptions().putAll(replacement.getOptions());
		conversation.setConversationId(replacement.getConversationId());
	}

	private ChatConversation createNewChatConversation() {
		ChatConversation res = new ChatConversation();

		if (settings.getPromptTemplate() != null) {
			res.addMessage(new ChatMessage(Role.SYSTEM, settings.getPromptTemplate().getPrompt()), false);
		}

		return res;
	}

	private static List<MessageContext> getExternallyAddedContext() {
		return externallyAddedContext;
	}

	public static void addContext(MessageContext newCtx) {
		boolean isDuplicate = false;
		for (MessageContext ctx : getExternallyAddedContext()) {
			isDuplicate |= newCtx.isDuplicate(ctx);
		}
		if (!isDuplicate) {
			getExternallyAddedContext().add(newCtx);
		}
	}

	private class OnClickFunction extends BrowserFunction {

		public OnClickFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			if (arguments[0] instanceof String str && !StringUtils.isBlank(str)) {
				if (str.startsWith("edit:")) {
					String messageUuid = str.substring("edit:".length());
					editChat(messageUuid);
				} else if (str.startsWith("copy:")) {
					String messageUuid = str.substring("copy:".length());
					copyMessageToClipboard(messageUuid);
				} else if (str.startsWith("apply:")) {
					String encodedCode = str.substring("apply:".length());
					applyCodeToEditor(encodedCode);
				} else if (str.startsWith("attachment:")) { // Add this case
					String attachmentUuid = str.substring("attachment:".length());
					openAttachmentDialog(attachmentUuid);
				}
			}
			return null;
		}
	}

	private void openAttachmentDialog(String attachmentUuid) {
		MessageContext ctx = findContextByUuid(attachmentUuid);
		if (ctx != null) {
			MessageContextDialog dlg = new MessageContextDialog(getSite().getShell(), ctx);
			dlg.open();
		}
	}

	private MessageContext findContextByUuid(String uuidString) {
		try {
			UUID uuid = UUID.fromString(uuidString);
			for (ChatMessage message : conversation.getMessages()) { // Access messages directly
				for (MessageContext ctx : message.getContext()) {
					if (uuid.equals(ctx.getUuid())) {
						return ctx;
					}
				}
			}
		} catch (IllegalArgumentException e) {
			Activator.logError("Invalid UUID format: " + uuidString, e);
		}
		return null;
	}

	public void copyMessageToClipboard(String messageUuidString) {
		UUID messageUuid = UUID.fromString(messageUuidString);

		// Find the message with the given UUID
		Optional<ChatMessage> messageOpt = conversation.getMessages().stream()
				.filter(msg -> messageUuid.equals(msg.getId())).findFirst();

		if (messageOpt.isPresent()) {
			ChatMessage message = messageOpt.get();

			// Copy to clipboard using SWT
			Clipboard clipboard = new Clipboard(Display.getDefault());
			TextTransfer textTransfer = TextTransfer.getInstance();

			MessageContentWithReasoning thoughtsAndMessage = splitThoughtsFromMessage(message);
			String messageMarkdown = thoughtsAndMessage.getMessage();
			if (!message.getContext().isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("\n\n#Context:\n");
				sb.append(message.getContext().stream().map(c -> c.compile()).collect(Collectors.joining("\n")));
				messageMarkdown += sb.toString();
			}

			clipboard.setContents(new Object[] { messageMarkdown }, new Transfer[] { textTransfer });
			clipboard.dispose();
		}
	}

	/**
	 * Applies code from the chat to the current editor, minimizing undo steps.
	 * <p>
	 * The implementation reduces undo/redo fragmentation by:
	 * <ul>
	 * <li>Wrapping all changes in a single compound operation via
	 * DocumentUndoManagerRegistry</li>
	 * <li>Using a two-phase formatting approach where text is formatted in memory
	 * first</li>
	 * <li>Applying the formatted result as a single document edit rather than
	 * multiple edits</li>
	 * </ul>
	 * This ensures that code application and formatting appear as one atomic
	 * operation in the editor's undo history, improving usability.
	 * 
	 * @param encodedCode URL-encoded string containing the code to apply
	 */
	public void applyCodeToEditor(String encodedCode) {
		try {
			String codeContent = java.net.URLDecoder.decode(encodedCode, StandardCharsets.UTF_8.name());

			// Get the active editor
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}

			IWorkbenchPage page = window.getActivePage();
			if (page == null) {
				return;
			}

			IEditorPart editor = page.getActiveEditor();
			if (editor == null || !(editor instanceof ITextEditor)) {
				return;
			}

			ITextEditor textEditor = (ITextEditor) editor;
			IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());

			if (document == null) {
				return;
			}

			// Get the current selection or cursor position
			ISelectionProvider selectionProvider = textEditor.getSelectionProvider();
			if (selectionProvider == null) {
				return;
			}

			ISelection selection = selectionProvider.getSelection();
			if (selection instanceof ITextSelection) {
				ITextSelection textSelection = (ITextSelection) selection;

				final int offset;
				final int length;

				if (textSelection.getLength() > 0) {
					// Replace the selected text
					offset = textSelection.getOffset();
					length = textSelection.getLength();
				} else {
					// Insert at cursor position
					offset = textSelection.getOffset();
					length = 0;
				}

				// Execute the edit on the UI thread
				Display.getDefault().asyncExec(() -> {
					try {
						// Get undo manager to create a single undoable operation
						IDocumentUndoManager undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);

						// Store original length
						int originalDocumentLength = document.getLength();

						// Begin a single compound change
						undoManager.beginCompoundChange();

						try {
							// Apply the code change
							document.replace(offset, length, codeContent);

							// Format the code using a non-document-modifying approach
							String formattedText = getFormattedText(document.get(), offset, codeContent.length());
							if (formattedText != null) {
								// Apply the formatted text as a single change
								document.replace(offset, codeContent.length(), formattedText);
							} else {
								// Fallback to traditional formatting if above fails
								formatCode(document, offset, codeContent.length());
							}

							// Calculate the formatted region's size
							int formattedLength = document.getLength() - (originalDocumentLength - length);

							// Select the entire applied and formatted code
							textEditor.selectAndReveal(offset, formattedLength);
						} finally {
							// End the compound change
							undoManager.endCompoundChange();
						}
					} catch (BadLocationException e) {
						Activator.logError("Error applying code to editor", e);
					}
				});
			}
		} catch (Exception e) {
			Activator.logError("Error processing code application", e);
		}
	}

	/**
	 * Gets formatted text without directly modifying the document. This allows us
	 * to apply the formatting as a single edit operation.
	 * 
	 * @param documentContent The full document content
	 * @param offset          The offset of the text to format
	 * @param length          The length of text to format
	 * @return Formatted text or null if formatting failed
	 */
	private String getFormattedText(String documentContent, int offset, int length) {
		try {
			CodeFormatter formatter = ToolFactory.createCodeFormatter(null);
			if (formatter != null) {
				TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT, documentContent, offset, length, 0,
						null);
				if (edit != null) {
					// Create a document with the same content to apply the edits to
					Document tempDocument = new Document(documentContent);
					edit.apply(tempDocument);

					// Extract only the formatted region
					return tempDocument.get(offset, length + (tempDocument.getLength() - documentContent.length()));
				}
			}
		} catch (Exception e) {
			Activator.logError("Error in getFormattedText", e);
		}
		return null;
	}

	/**
	 * Fallback formatting method - applies edits directly to the document
	 */
	private void formatCode(IDocument document, int offset, int length) {
		CodeFormatter formatter = ToolFactory.createCodeFormatter(null);
		if (formatter != null) {
			TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT, document.get(), offset, length, 0, null);
			if (edit != null) {
				try {
					edit.apply(document);
				} catch (MalformedTreeException | BadLocationException e) {
					Activator.logError("Error formatting code", e);
				}
			}
		}
	}

	private MessageContentWithReasoning splitThoughtsFromMessage(ChatMessage message) {
		String content = StringUtils.stripToEmpty(message.getContent());
		Matcher thinkStartMatcher = PATTERN_THINK_START.matcher(content);
		Matcher thinkEndMatcher = PATTERN_THINK_END.matcher(message.getContent());

		String thinkContent = "";
		String messageContent = message.getContent();
		boolean endOfThinkingReached = false;
		match_found: if (thinkStartMatcher.find()) {

			// If we encounter a start tag in the middle of a conversation, it's probably a
			// model talking about reasoning.
			if ((thinkStartMatcher.start() > 0)) {
				break match_found;
			}

			if (thinkEndMatcher.find()) {
				int endPosition = thinkEndMatcher.start();
				thinkContent = messageContent.substring(thinkStartMatcher.end(), endPosition);
				messageContent = messageContent.substring(thinkEndMatcher.end());
				endOfThinkingReached = true;
			} else {
				thinkContent = messageContent.substring(thinkStartMatcher.end());
				messageContent = "";
			}
		}
		messageContent = PATTERN_TAGS_TO_REMOVE.matcher(messageContent).replaceAll("");

		MessageContentWithReasoning thoughtsAndMessage = new MessageContentWithReasoning(thinkContent, messageContent,
				endOfThinkingReached);
		return thoughtsAndMessage;
	}

	@Override
	public void dispose() {
		executorService.shutdownNow();
		super.dispose();
	}

	private void createPaperclipBase64() {
		ImageData imageData = paperclipImage.getImageData();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		org.eclipse.swt.graphics.ImageLoader imageLoader = new org.eclipse.swt.graphics.ImageLoader();
		imageLoader.data = new ImageData[] { imageData };
		imageLoader.save(baos, SWT.IMAGE_PNG);

		paperclipBase64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
	}

	private String ONCLICK_LISTENER = "document.onmousedown = function(e) {" + "if (!e) {e = window.event;} "
			+ "if (e) {var target = e.target || e.srcElement; " + "var elementId = target.id ? target.id : 'no-id';"
			+ "elementClicked(elementId);}}";
}
