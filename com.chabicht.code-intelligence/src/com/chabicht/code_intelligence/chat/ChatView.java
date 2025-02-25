package com.chabicht.code_intelligence.chat;

import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_BUDGET_TOKENS;
import static com.chabicht.code_intelligence.model.ChatConversation.ChatOption.REASONING_ENABLED;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.core.databinding.observable.list.IListChangeListener;
import org.eclipse.core.databinding.observable.list.ListDiffEntry;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
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
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
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
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiModelConnection;
import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.RangeType;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ChatView extends ViewPart {
	private static final Pattern PATTERN_THINK_START = Pattern.compile("<think>|<\\|begin_of_thought\\|>");
	private static final Pattern PATTERN_THINK_END = Pattern.compile("<[/]think>|<\\|end_of_thought\\|>");
	private static final Pattern PATTERN_TAGS_TO_REMOVE = Pattern
			.compile("<\\|begin_of_solution\\|>|<\\|end_of_solution\\|>");

	private static final WritableList<MessageContext> externallyAddedContext = new WritableList<>();

	private final ChatSettings settings = new ChatSettings();

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private ChatComponent chat;

	private TextViewer tvUserInput;
	private IDocument userInput;

	private Font buttonSymbolFont;
	private Image paperclipImage;

	private ChatConversation conversation;

	private Parser markdownParser = Parser.builder().build();
	private HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

	private Button btnSend;
	private AiModelConnection connection;

	private Composite cmpAttachments;
	private Composite composite;
	private Button btnSettings;

	private ChatListener chatListener = new ChatListener() {

		@Override
		public void onMessageUpdated(ChatMessage message) {
			Matcher thinkStartMatcher = PATTERN_THINK_START.matcher(message.getContent());
			Matcher thinkEndMatcher = PATTERN_THINK_END.matcher(message.getContent());

			String thinkContent = "";
			String messageContent = message.getContent();
			boolean endOfThinkingReached = false;
			if (thinkStartMatcher.find()) {
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

			String thinkHtml = "";
			if (StringUtils.isNotBlank(thinkContent)) {
				thinkHtml = String.format("<details%s><summary>%s</summary><blockquote>%s</blockquote></details>",
						endOfThinkingReached ? "" : " open", endOfThinkingReached ? "Thoughts" : "Thinking...",
						markdownRenderer.render(markdownParser.parse(thinkContent)));
			}
			String messageHtml = markdownRenderer.render(markdownParser.parse(messageContent));
			String combinedHtml = thinkHtml + messageHtml;
			Display.getDefault().asyncExec(() -> {
				chat.updateMessage(message.getId(), combinedHtml);
			});
		}

		@Override
		public void onMessageAdded(ChatMessage message) {
			Display.getDefault().asyncExec(() -> {
				StringBuilder attachments = new StringBuilder();
				if (!message.getContext().isEmpty()) {
					for (MessageContext ctx : message.getContext()) {
						attachments.append(String.format("<span class=\"attachment-container\">"
								+ "<span class=\"attachment-icon\">&#128206;</span>"
								+ "<span class=\"tooltip\">%s</span>" + "</span>", ctx.getLabel()));
					}
				}
				String messageHtml = markdownRenderer.render(markdownParser.parse(message.getContent()));
				String combinedHtml = messageHtml + "\n" + attachments.toString();
				chat.addMessage(message.getId(), message.getRole().name().toLowerCase(), combinedHtml);
			});
		}

		@Override
		public void onChatResponseFinished(ChatMessage message) {
			Display.getDefault().asyncExec(() -> {
				// Set text to "▶️"
				btnSend.setText("\u25B6");

				connection = null;

				if (isDebugPromptLoggingEnabled()) {
					Activator.logInfo(conversation.toString());
				}
			});
		}
	};

	private boolean isDebugPromptLoggingEnabled() {
		return Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS);
	}

	public ChatView() {
		buttonSymbolFont = resources.create(JFaceResources.getDefaultFontDescriptor().setHeight(18));

		ImageDescriptor paperclipDescriptor = ImageDescriptor.createFromFile(getClass(), "/icons/paperclip.png");
		paperclipImage = resources.create(paperclipDescriptor);

	}

	@Override
	public void createPartControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE);
		GridLayout gl_composite = new GridLayout(2, false);
		gl_composite.marginHeight = 0;
		gl_composite.marginWidth = 0;
		composite.setLayout(gl_composite);

		chat = new ChatComponent(composite, SWT.BORDER);
		chat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		Button btnClear = new Button(composite, SWT.NONE);
		btnClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clearChat();
			}
		});
		GridData gd_btnClear = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_btnClear.heightHint = 40;
		gd_btnClear.widthHint = 40;
		btnClear.setLayoutData(gd_btnClear);
		btnClear.setToolTipText("Clear conversation");
		// Broom 🧹
		btnClear.setText("\uD83E\uDDF9");
		btnClear.setFont(buttonSymbolFont);

		cmpAttachments = new Composite(composite, SWT.NONE);
		GridData gd_cmpAttachments = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		gd_cmpAttachments.heightHint = 30;
		cmpAttachments.setLayoutData(gd_cmpAttachments);
		RowLayout layoutCmpAttachments = new RowLayout(SWT.HORIZONTAL);
		layoutCmpAttachments.marginTop = 0;
		layoutCmpAttachments.marginBottom = 0;
		cmpAttachments.setLayout(layoutCmpAttachments);
		new Label(composite, SWT.NONE);

		tvUserInput = new TextViewer(composite, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		GridData gridDataTvUserInput = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gridDataTvUserInput.verticalSpan = 2;
		gridDataTvUserInput.heightHint = 80;
		tvUserInput.getTextWidget().setLayoutData(gridDataTvUserInput);

		btnSettings = new Button(composite, SWT.NONE);
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
		gd_btnSettings.widthHint = 40;
		gd_btnSettings.heightHint = 40;
		btnSettings.setLayoutData(gd_btnSettings);
		btnSettings.setText("\u2699");
		btnSettings.setFont(buttonSymbolFont);
		GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gridData.verticalSpan = 2;
		gridData.heightHint = 80;

		btnSend = new Button(composite, SWT.NONE);
		btnSend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				sendMessageOrAbortChat();
			}
		});
		GridData gd_btnSend = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1);
		gd_btnSend.heightHint = 40;
		gd_btnSend.widthHint = 40;
		btnSend.setLayoutData(gd_btnSend);
		btnSend.setToolTipText("Send message (Ctrl + Enter)");
		// Set text to "▶️"
		btnSend.setText("\u25B6");
		btnSend.setFont(buttonSymbolFont);

		init();
		initUserInputControl();
		initListeners();

		conversation = createNewChatConversation();
		conversation.addListener(chatListener);
	}

	private void init() {
		String defaultModel = Activator.getDefault().getPreferenceStore()
				.getString(PreferenceConstants.CHAT_MODEL_NAME);
		settings.setModel(defaultModel);

		if (StringUtils.isNotBlank(defaultModel)) {
			String[] split = defaultModel.split("/");
			if (split.length == 2) {
				String connectionName = split[0];
				String modelId = split[1];
				Activator.getDefault().loadPromptTemplates().stream()
						.filter(pt -> PromptType.CHAT.equals(pt.getType()) && pt.isUseByDefault()
								&& pt.isApplicable(connectionName, modelId))
						.findFirst().ifPresent(settings::setPromptTemplate);
			}
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
					RowData rd = new RowData(16, 25);
					l.setLayoutData(rd);
					cmpAttachments.layout();
				} else {
					removeAttachmentLabel(ctx);
				}
			}
		};
		externallyAddedContext.addListChangeListener(listChangeListener);
		composite.addDisposeListener(e -> ChatView.externallyAddedContext.removeListChangeListener(listChangeListener));

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

			// Set text to "▶️"
			btnSend.setText("\u25B6");

			connection = null;
		} else {
			ChatMessage chatMessage = new ChatMessage(Role.USER, userInput.get());

			String consoleSelection = ConsolePageParticipant.getSelectedText();
			if (StringUtils.isNotBlank(consoleSelection)) {
				Point selectionRange = Optional.ofNullable(ConsolePageParticipant.getSelectionRange())
						.orElse(new Point(0, 0));
				String consoleName = Optional.ofNullable(ConsolePageParticipant.getConsoleName()).orElse("Console Log");
				externallyAddedContext.add(new MessageContext(consoleName, RangeType.OFFSET,
						selectionRange.x, selectionRange.x + selectionRange.y, consoleSelection));
			}

			externallyAddedContext.forEach(ctx -> addContextToMessageIfNotDuplicate(chatMessage, ctx.getFileName(),
					ctx.getRangeType(), ctx.getStart(), ctx.getEnd(), ctx.getContent()));
			externallyAddedContext.clear();
			addSelectionAsContext(chatMessage);

			conversation.getOptions().put(REASONING_ENABLED, settings.isReasoningEnabled());
			conversation.getOptions().put(REASONING_BUDGET_TOKENS, settings.getReasoningTokens());

			conversation.addMessage(chatMessage);
			connection.chat(conversation);
			userInput.set("");

			// Set text to "⏹️"
			btnSend.setText("\u23F9");
		}
	}

	public void editChat(String messageUuidString) {
		Display.getDefault().syncExec(() -> {
			UUID messageUuid = UUID.fromString(messageUuidString);
			if (connection == null || !connection.isChatPending()) {
				getExternallyAddedContext().clear();
				ChatConversation oldConvo = conversation;

				clearChat();

				List<ChatMessage> messages = oldConvo.getMessages();
				for (int i = 0; i < messages.size(); i++) {
					ChatMessage msg = messages.get(i);
					if (messageUuid.equals(msg.getId())) {
						userInput.set(msg.getContent());
						getExternallyAddedContext().addAll(msg.getContext());
						break;
					} else {
						conversation.addMessage(msg);
					}
				}
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
			addContextToMessageIfNotDuplicate(chatMessage, fileName, RangeType.LINE, startLine + 1, endLine + 1,
					selectedText);
		}
	}

	public void addContextToMessageIfNotDuplicate(ChatMessage chatMessage, String fileName, RangeType rangeType,
			int start, int end, String selectedText) {
		boolean duplicate = false;
		MessageContext newCtx = new MessageContext(fileName, rangeType, start, end, selectedText);
		for (MessageContext ctx : chatMessage.getContext()) {
			if (newCtx.isDuplicate(ctx)) {
				duplicate = true;
			}
		}
		if (!duplicate) {
			chatMessage.getContext().add(new MessageContext(fileName, start, end, selectedText));
		}
	}

	private void clearChat() {
		if (connection != null) {
			connection.abortChat();
			connection = null;
		}
		conversation.removeListener(chatListener);
		conversation = createNewChatConversation();
		conversation.addListener(chatListener);
		externallyAddedContext.clear();
		chat.reset();
		userInput.set("");
	}

	private ChatConversation createNewChatConversation() {
		ChatConversation res = new ChatConversation();

		if (settings.getPromptTemplate() != null) {
			res.addMessage(new ChatMessage(Role.SYSTEM, settings.getPromptTemplate().getPrompt()));
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
			if (arguments[0] instanceof String str && !StringUtils.isBlank(str) && str.startsWith("edit:")) {
				String messageUuid = str.substring("edit:".length());
				editChat(messageUuid);
			}
			return null;
		}

	}

	private String ONCLICK_LISTENER = "document.onmousedown = function(e) {" + "if (!e) {e = window.event;} "
			+ "if (e) {var target = e.target || e.srcElement; " + "var elementId = target.id ? target.id : 'no-id';"
			+ "elementClicked(elementId);}}";
}
