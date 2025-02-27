package com.chabicht.code_intelligence.chat;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.chabicht.code_intelligence.model.ChatHistoryEntry;

public class ChatHistoryDialog extends Dialog {

    private List<ChatHistoryEntry> chatHistory;
    private TableViewer tableViewer;
    private ChatHistoryEntry selectedEntry;
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.getDefault())
                            .withZone(ZoneId.systemDefault());

    public ChatHistoryDialog(Shell parentShell, List<ChatHistoryEntry> chatHistory) {
        super(parentShell);
        this.chatHistory = chatHistory;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Chat History");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, true));
        
        tableViewer = new TableViewer(container, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
        Table table = tableViewer.getTable();
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        
        // Create columns
        TableViewerColumn titleColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        titleColumn.getColumn().setText("Title");
        titleColumn.getColumn().setWidth(250);
        titleColumn.setLabelProvider(new ColumnLabelProvider() {
            private final Font boldFont = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
            
            @Override
            public String getText(Object element) {
                if (element instanceof ChatHistoryEntry entry) {
                    return entry.getTitle();
                }
                return "";
            }
            
            @Override
            public Font getFont(Object element) {
                // Use bold font for items from today
                if (element instanceof ChatHistoryEntry entry) {
                    if (entry.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                            .equals(java.time.LocalDate.now())) {
                        return boldFont;
                    }
                }
                return super.getFont(element);
            }
        });
        
        TableViewerColumn messagesColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        messagesColumn.getColumn().setText("Messages");
        messagesColumn.getColumn().setWidth(80);
        messagesColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof ChatHistoryEntry entry) {
                    return String.valueOf(entry.getMessageCount());
                }
                return "";
            }
        });
        
        TableViewerColumn dateColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        dateColumn.getColumn().setText("Last Update");
        dateColumn.getColumn().setWidth(150);
        dateColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof ChatHistoryEntry entry) {
                    return DATE_FORMATTER.format(entry.getUpdatedAt());
                }
                return "";
            }
        });
        
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        tableViewer.setInput(chatHistory);
        
        tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                if (!selection.isEmpty()) {
                    selectedEntry = (ChatHistoryEntry) selection.getFirstElement();
                    getButton(IDialogConstants.OK_ID).setEnabled(true);
                } else {
                    selectedEntry = null;
                    getButton(IDialogConstants.OK_ID).setEnabled(false);
                }
            }
        });
        
        tableViewer.addDoubleClickListener((DoubleClickEvent event) -> {
            if (selectedEntry != null) {
                okPressed();
            }
        });
        
        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        getButton(IDialogConstants.OK_ID).setText("Load Conversation");
        getButton(IDialogConstants.OK_ID).setEnabled(false);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(550, 400);
    }

    public ChatHistoryEntry getSelectedEntry() {
        return selectedEntry;
    }
}
