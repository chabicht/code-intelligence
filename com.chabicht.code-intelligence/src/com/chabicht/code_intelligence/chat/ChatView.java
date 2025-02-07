package com.chabicht.code_intelligence.chat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.core.databinding.observable.list.IListChangeListener;
import org.eclipse.core.databinding.observable.list.ListDiffEntry;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.ITextSelection;
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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
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
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ChatView extends ViewPart {
	private static final Pattern PATTERN_THINK_START = Pattern.compile("<think>");
	private static final Pattern PATTERN_THINK_END = Pattern.compile("<[/]think>");

	private static final WritableList<MessageContext> externallyAddedContext = new WritableList<>();

	private Text txtUserInput;

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private Font buttonSymbolFont;
	private Image paperclipImage;

	private ChatConversation conversation = new ChatConversation();

	private Browser bChat;

	private Parser markdownParser = Parser.builder().build();
	private HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

	private Button btnSend;
	private AiModelConnection connection;

	private Composite cmpAttachments;
	private Composite composite;

	private ChatListener chatListener = new ChatListener() {

		@Override
		public void onMessageUpdated(ChatMessage message) {
			Matcher thinkStartMatcher = PATTERN_THINK_START.matcher(message.getContent());
			Matcher thinkEndMatcher = PATTERN_THINK_END.matcher(message.getContent());

			String thinkContent = "";
			String messageContent = message.getContent();
			if (thinkStartMatcher.find()) {
				if (thinkEndMatcher.find()) {
					int endPosition = thinkEndMatcher.start();
					thinkContent = messageContent.substring(thinkStartMatcher.end(), endPosition);
					messageContent = messageContent.substring(endPosition);
				} else {
					thinkContent = messageContent.substring(thinkStartMatcher.end());
					messageContent = "";
				}
			}

			String thinkHtml = "";
			if (StringUtils.isNotBlank(thinkContent)) {
				thinkHtml = String.format(
						"<details><summary>Thinking...</summary><blockquote>%s</blockquote></details>",
						markdownRenderer.render(markdownParser.parse(thinkContent)));
			}
			String messageHtml = markdownRenderer.render(markdownParser.parse(messageContent));
			String combinedHtml = thinkHtml + messageHtml;
			Display.getDefault().asyncExec(() -> {
				bChat.execute(String.format("updateMessage('%s', '%s');", message.getId(),
						escapeForJavaScript(combinedHtml)));
			});
		}

		@Override
		public void onMessageAdded(ChatMessage message) {
			Display.getDefault().asyncExec(() -> {
				StringBuilder attachments = new StringBuilder();
				if (!message.getContext().isEmpty()) {
					for (MessageContext ctx : message.getContext()) {
						attachments.append(String.format(
								"<span class=\"attachment-container\">"
										+ "<span class=\"attachment-icon\">&#128206;</span>"
										+ "<span class=\"tooltip\">%s</span>" + "</span>",
								ctx.getLabel()));
					}
				}
				String messageHtml = markdownRenderer.render(markdownParser.parse(message.getContent()));
				String combinedHtml = messageHtml + "\n" + attachments.toString();
				bChat.execute(String.format("addMessage('%s', '%s', '%s');", message.getId(),
						message.getRole().name().toLowerCase(), escapeForJavaScript(combinedHtml)));
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
		gl_composite.marginWidth = 0;
		gl_composite.horizontalSpacing = 0;
		composite.setLayout(gl_composite);

		bChat = new Browser(composite, SWT.NONE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bChat.setText(CHAT_TEMPLATE);

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

		txtUserInput = new Text(composite, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		txtUserInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if ((e.stateMask & SWT.CTRL) > 0 && (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR)) {
					e.doit = false;
					sendMessageOrAbortChat();
				}
			}
		});
		GridData gd_txtUserInput = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_txtUserInput.heightHint = 80;
		txtUserInput.setLayoutData(gd_txtUserInput);

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
		conversation.addListener(chatListener);

		initListeners();
	}

	private void initListeners() {
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

		bChat.addProgressListener(ProgressListener.completedAdapter(event -> {
			final BrowserFunction function = new OnClickFunction(bChat, "elementClicked");
			bChat.execute(ONCLICK_LISTENER);
			bChat.addLocationListener(new LocationAdapter() {
				@Override
				public void changed(LocationEvent event) {
					bChat.removeLocationListener(this);
					function.dispose();
				}
			});
		}));
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
		if (txtUserInput != null && !txtUserInput.isDisposed()) {
			txtUserInput.setFocus();
		}
	}

	private void sendMessageOrAbortChat() {
		if (connection == null) {
			connection = ConnectionFactory.forChat();
		}
		if (connection.isChatPending()) {
			connection.abortChat();

			// Set text to "▶️"
			btnSend.setText("\u25B6");

			connection = null;
		} else {
			ChatMessage chatMessage = new ChatMessage(Role.USER, txtUserInput.getText());

			externallyAddedContext.forEach(ctx -> addContextToMessageIfNotDuplicate(chatMessage, ctx.getFileName(),
					ctx.getRangeType(), ctx.getStart(), ctx.getEnd(), ctx.getContent()));
			externallyAddedContext.clear();
			addSelectionAsContext(chatMessage);

			conversation.addMessage(chatMessage);
			connection.chat(conversation);
			txtUserInput.setText("");

			// Set text to "⏹️"
			btnSend.setText("\u23F9");
		}
	}

	public void editChat(String messageUuidString) {
		Display.getDefault().syncExec(() -> {
			UUID messageUuid = UUID.fromString(messageUuidString);
			getExternallyAddedContext().clear();
			if (connection == null || !connection.isChatPending()) {
				ChatConversation oldConvo = conversation;

				clearChat();

				List<ChatMessage> messages = oldConvo.getMessages();
				for (int i = 0; i < messages.size(); i++) {
					ChatMessage msg = messages.get(i);
					if (messageUuid.equals(msg.getId())) {
						txtUserInput.setText(msg.getContent());
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
			int start,
			int end,
			String selectedText) {
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
		conversation = new ChatConversation();
		conversation.addListener(chatListener);
		externallyAddedContext.clear();
		bChat.setText(CHAT_TEMPLATE);
		txtUserInput.setText("");
	}

	/**
	 * Escapes a string for safe use in a JavaScript literal.
	 *
	 * @param input the original string to be escaped
	 * @return a string where characters that might break a JS literal are escaped
	 */
	public static String escapeForJavaScript(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			switch (c) {
			case '\\':
				escaped.append("\\\\");
				break;
			case '"':
				escaped.append("\\\"");
				break;
			case '\'':
				escaped.append("\\'");
				break;
			case '`':
				escaped.append("\\`");
				break;
			case '\n':
				escaped.append("\\n");
				break;
			case '\r':
				escaped.append("\\r");
				break;
			case '\t':
				escaped.append("\\t");
				break;
			default:
				escaped.append(c);
			}
		}
		return escaped.toString();
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

	private String CHAT_TEMPLATE = """
			<!DOCTYPE html>
			<meta http-equiv="X-UA-Compatible" content="IE=edge" />
			<html>
			<head>
			  <style>
				html, body {
				  margin: 0;
				  padding: 0;
				  font-family: "Helvetica", Sans-Serif;
				}

			    /* The main container uses flex layout in column direction */
			    .chat-container {
			      display: flex;
			      flex-direction: column;
			      width: 100%;
			      height: 100%;
			      margin: 0;
			      padding: 10px;
			      box-sizing: border-box;
			      background-color: #ffffff;
				  overflow-y: hidden;
				  overflow-x: auto;
			    }

			    /* General message bubble styling */
			    .message {
			      border: 1px solid #ccc;
			      border-radius: 10px;
			      width: 80vw;       /* The message bubble will be 80% of the viewport width */
			      max-width: 600px;  /* Optionally set a max width for larger screens */
			      padding: 10px;
			      margin: 5px 0;
			      box-sizing: border-box;
				  overflow: auto;
			    }

			    /* Message from "me": align bubbles to the right */
			    .from-me {
			      align-self: flex-start;
			      background-color: #ededed;
			    }

			    /* Message from "them": align bubbles to the left */
			    .from-them {
			      align-self: flex-end;
			      background-color: #ededed;
			    }

				/* Style for the details element */
				.details {
				  margin: 5px 0;
				}

				/* Style the summary element to look like a clickable label */
				summary {
				  cursor: pointer;
				  font-weight: bold;
				  color: #007acc;
				}

				/* Optional: Style the blockquote to better match your chat theme */
				blockquote {
				  border-left: 2px solid #ccc;
				  margin: 10px 0;
				  padding-left: 10px;
				  background: #ededed;
				}

				/* Container for the attachment icon and tooltip */
				.attachment-container {
				  position: relative; /* Allows the tooltip to be positioned absolutely relative to this container */
				  display: inline-block; /* Keeps the container only as large as its contents */
				  margin-left: 5px; /* Optional spacing from other text */
				}

				/* The attachment icon styling */
				.attachment-icon {
				  font-size: 16px; /* Adjust the size as needed */
				  color: #555;
				  cursor: default; /* No pointer so it just indicates information */
				}

				/* Tooltip styling */
				.attachment-container .tooltip {
				  visibility: hidden;
				  width: 400px; /* Adjust width as needed */
				  background-color: #000;
				  color: #fff;
				  text-align: center;
				  padding: 5px;
				  border-radius: 4px;

				  /* Positioning */
				  position: absolute;
				  z-index: 1;
				  bottom: 125%; /* Position above the icon */
				  left: 50%;
				  margin-left: -20px; /* Half the tooltip width to center it */

				  /* IE 11 supports opacity transition */
				  opacity: 0;
				  filter: alpha(opacity=0); /* For older IE versions if needed */

				  /* Optional arrow at the bottom */
				  /* For IE 11, using border triangle works fine */
				}

				/* Tooltip arrow (using a pseudo-element) */
				.attachment-container .tooltip::after {
				  content: "";
				  position: absolute;
				  top: 100%; /* At the bottom of the tooltip */
				  left: 20px;
				  margin-left: -5px;
				  border-width: 5px;
				  border-style: solid;
				  border-color: #000 transparent transparent transparent;
				}

				/* Show the tooltip when hovering over the container */
				.attachment-container:hover .tooltip {
				  visibility: visible;
				  opacity: 1;
				  filter: alpha(opacity=100);
				}

			  </style>
			</head>
			<body>

			<div id="chat-container" class="chat-container">
			</div>
			<div id='bottom' style='visibility: hidden;'></div>

			<script>
			/**
			 * Adds a new message to the chat container.
			 * @param {String} uuid - Unique identifier for the message.
			 * @param {String} role - The role of the sender ("assistant" or "user").
			 * @param {String} content - The HTML content of the message.
			 */
			function addMessage(uuid, role, content) {
			  var container = document.getElementById("chat-container");
			  if (!container) {
			    return;
			  }
			  var div = document.createElement("div");

			  // Choose the correct CSS class based on the role
			  if (role === "assistant") {
			    div.className = "message from-them";
			  } else if (role === "user") {
			    div.className = "message from-me";
			  } else {
			    div.className = "message";
			  }

			  // Set the uuid as the element's id for future reference
			  div.id = uuid;
			  div.innerHTML = content;

			  // If the message is from the user, add the edit icon
			  if (role === "user") {
			    var editSpan = document.createElement("span");
			    editSpan.id = "edit:" + uuid;
			    editSpan.innerHTML = "\uD83D\uDD8A"; // 🖊️ emoji
			    editSpan.style.position = "absolute";
			    editSpan.style.bottom = "5px";
			    editSpan.style.right = "5px";
			    editSpan.style.cursor = "pointer";
			    editSpan.style.fontSize = "26px";
			    editSpan.style.fontWeight = "800";

			    div.style.position = "relative";
			    div.appendChild(editSpan);
			  }

			  container.appendChild(div);

			  var bottom = document.getElementById("bottom");
			  bottom.scrollIntoView(true);
			}

			/**
			 * Updates an existing message's content.
			 * @param {String} uuid - Unique identifier for the message to update.
			 * @param {String} updatedContent - The new HTML content for the message.
			 */
			function updateMessage(uuid, updatedContent) {
			  var message = document.getElementById(uuid);
			  if (message) {
			    message.innerHTML = updatedContent;
			  }

			  var bottom = document.getElementById("bottom");
			  bottom.scrollIntoView(true);
			}
			</script>

			</body>
			</html>
			""";

	private String ONCLICK_LISTENER = "document.onmousedown = function(e) {" + "if (!e) {e = window.event;} "
			+ "if (e) {var target = e.target || e.srcElement; " + "var elementId = target.id ? target.id : 'no-id';"
			+ "elementClicked(elementId);}}";
}
