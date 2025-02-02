package com.chabicht.code_intelligence.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public class ChatView extends ViewPart {
	private static final Pattern PATTERN_THINK_START = Pattern.compile("<think>");
	private static final Pattern PATTERN_THINK_END = Pattern.compile("<[/]think>");

	private Text txtUserInput;

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private Font buttonSymbolFont;

	private ChatConversation conversation = new ChatConversation();

	private Browser bChat;

	private Parser markdownParser = Parser.builder().build();
	private HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

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
				bChat.execute(
						String.format("updateMessage('%s', '%s');", message.getId(),
								escapeForJavaScript(combinedHtml)));
			});
		}

		@Override
		public void onMessageAdded(ChatMessage message) {
			Display.getDefault().asyncExec(() -> {
				String messageHtml = markdownRenderer.render(markdownParser.parse(message.getContent()));
				bChat.execute(String.format("addMessage('%s', '%s', '%s');", message.getId(),
						message.getRole().name().toLowerCase(), escapeForJavaScript(messageHtml)));
			});
		}
	};

	public ChatView() {
		buttonSymbolFont = resources.create(JFaceResources.getDefaultFontDescriptor().setHeight(18));
	}

	@Override
	public void createPartControl(Composite parent) {

		Composite composite = new Composite(parent, SWT.NONE);
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
				conversation.removeListener(chatListener);
				conversation = new ChatConversation();
				conversation.addListener(chatListener);
				bChat.setText(CHAT_TEMPLATE);
				txtUserInput.setText("");
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

		txtUserInput = new Text(composite, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		txtUserInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if ((e.stateMask & SWT.CTRL) > 0 && (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR)) {
					e.doit = false;
					sendMessage();
				}
			}
		});
		GridData gd_txtUserInput = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_txtUserInput.heightHint = 80;
		txtUserInput.setLayoutData(gd_txtUserInput);

		Button btnSend = new Button(composite, SWT.NONE);
		btnSend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				sendMessage();
			}
		});
		GridData gd_btnSend = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1);
		gd_btnSend.heightHint = 40;
		gd_btnSend.widthHint = 40;
		btnSend.setLayoutData(gd_btnSend);
		btnSend.setToolTipText("Send message (Ctrl + Enter)");
		btnSend.setText("\u25B6");
		btnSend.setFont(buttonSymbolFont);
		conversation.addListener(chatListener);
	}

	@Override
	public void setFocus() {
		if (txtUserInput != null && !txtUserInput.isDisposed()) {
			txtUserInput.setFocus();
		}
	}

	private void sendMessage() {
		conversation.addMessage(new ChatMessage(Role.USER, txtUserInput.getText()));
		ConnectionFactory.forChat().chat(conversation);
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

	private String CHAT_TEMPLATE = """
			<!DOCTYPE html>
			<meta http-equiv="X-UA-Compatible" content="IE=edge" />
			<html>
			<head>
			  <style>
				html, body {
				  margin: 0;
				  padding: 0;
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
}
