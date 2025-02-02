package com.chabicht.code_intelligence.chat;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.part.ViewPart;

import com.chabicht.code_intelligence.apiclient.ConnectionFactory;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatListener;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;

public class ChatView extends ViewPart {
	private Text txtUserInput;

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private Font buttonSymbolFont;

	private ChatConversation conversation = new ChatConversation();
	private ChatMessage currentMessage;

	private Browser bChat;

	private Parser markdownParser = Parser.builder().build();
	private HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();
	private int latestMessageOffset = 0;

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

		ToolBar tbTop = new ToolBar(composite, SWT.FLAT | SWT.RIGHT);
		tbTop.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		bChat = new Browser(composite, SWT.NONE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

		txtUserInput = new Text(composite, SWT.BORDER | SWT.MULTI);
		GridData gd_txtUserInput = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_txtUserInput.heightHint = 80;
		txtUserInput.setLayoutData(gd_txtUserInput);

		Button btnSend = new Button(composite, SWT.NONE);
		btnSend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				conversation.addMessage(new ChatMessage(Role.USER, txtUserInput.getText()));
				ConnectionFactory.forChat().chat(conversation);
			}
		});
		GridData gd_btnSend = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1);
		gd_btnSend.heightHint = 40;
		gd_btnSend.widthHint = 40;
		btnSend.setLayoutData(gd_btnSend);
		btnSend.setToolTipText("Send message (Ctrl + Enter)");
		btnSend.setText("\u25B6");
		btnSend.setFont(buttonSymbolFont);

		conversation.addListener(new ChatListener() {

			@Override
			public void onMessageUpdated(ChatMessage message) {
				String messageHtml = markdownRenderer.render(markdownParser.parse(message.getContent()));
				Display.getDefault().asyncExec(() -> {
					bChat.setText(bChat.getText().substring(0, latestMessageOffset) + "<br/><br/>" + messageHtml);
				});
			}

			@Override
			public void onMessageAdded(ChatMessage message) {
				latestMessageOffset = bChat.getText().length();
				Display.getDefault().asyncExec(() -> {
					currentMessage = message;
					bChat.setText(bChat.getText() + "Message:" + message.getId() + "<br/><br/>" + message.getContent());
				});
			}
		});
	}

	@Override
	public void setFocus() {
		if (txtUserInput != null && !txtUserInput.isDisposed()) {
			txtUserInput.setFocus();
		}
	}

	private final static String CHAT_TEMPLATE = """
			<!DOCTYPE html>
			<html>
			<head>
			  <style>
			    /* The main container uses flex layout in column direction */
			    .chat-container {
			      display: flex;
			      flex-direction: column;
			      width: 100vw;
			      height: 100vh;
			      margin: 0;
			      padding: 10px;
			      box-sizing: border-box;
			      background-color: #f5f5f5;
			      overflow-y: auto;
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
			    }

			    /* Message from "me": align bubbles to the right */
			    .from-me {
			      align-self: flex-end;
			      background-color: #e0f7fa;
			    }

			    /* Message from "them": align bubbles to the left */
			    .from-them {
			      align-self: flex-start;
			      background-color: #ffffff;
			    }
			  </style>
			</head>
			<body>

			<div class="chat-container">
			  <div class="message from-them">
			    Hey, how are you?
			  </div>
			  <div class="message from-me">
			    I’m doing well, how about you?
			  </div>
			  <div class="message from-them">
			    I’m great, thanks for asking!
			  </div>
			  <div class="message from-me">
			    Glad to hear. Let’s chat more later!
			  </div>
			</div>

			</body>
			</html>
			""";
}
