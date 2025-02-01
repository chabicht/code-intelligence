package com.chabicht.code_intelligence.chat;

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
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.part.ViewPart;

import com.chabicht.code_intelligence.model.ChatConversation;

public class ChatView extends ViewPart {
	private Text txtUserInput;

	LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources());

	private Font buttonSymbolFont;

	private ChatConversation conversation = new ChatConversation();

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

		Browser bChat = new Browser(composite, SWT.NONE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

		txtUserInput = new Text(composite, SWT.BORDER);
		GridData gd_txtUserInput = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_txtUserInput.heightHint = 80;
		txtUserInput.setLayoutData(gd_txtUserInput);

		Button btnSend = new Button(composite, SWT.NONE);
		btnSend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

			}
		});
		GridData gd_btnSend = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1);
		gd_btnSend.heightHint = 40;
		gd_btnSend.widthHint = 40;
		btnSend.setLayoutData(gd_btnSend);
		btnSend.setToolTipText("Send message (Ctrl + Enter)");
		btnSend.setText("\u25B6");
		btnSend.setFont(buttonSymbolFont);
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
