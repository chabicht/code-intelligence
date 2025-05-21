package com.chabicht.code_intelligence.chat;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.util.ThemeUtil;

/**
 * Shows the contents of a message context entry.
 */
public class MessageContextDialog extends Dialog {

    private MessageContext messageContext;

    public MessageContextDialog(Shell parentShell, MessageContext messageContext) {
        super(parentShell);
        this.messageContext = messageContext;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
		newShell.setText("Message Context: " + messageContext.getLabel());
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        StyledText styledText = new StyledText(container, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		styledText.setText(messageContext.compile());
		styledText.setFont(ThemeUtil.getTextEditorFont());
        styledText.setEditable(false);

        return container;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(600, 400);
    }
}
