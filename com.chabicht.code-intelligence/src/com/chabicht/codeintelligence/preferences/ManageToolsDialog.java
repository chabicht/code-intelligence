package com.chabicht.codeintelligence.preferences;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions;
import com.chabicht.code_intelligence.chat.tools.ToolDefinitions.Tool;

public class ManageToolsDialog extends Dialog {

    private CheckboxTableViewer tableViewer;
	private List<Tool> tools;

	protected ManageToolsDialog(Shell parentShell) {
        super(parentShell);
		this.tools = ToolDefinitions.getInstance().getTools();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        container.setLayout(layout);

        tableViewer = CheckboxTableViewer.newCheckList(container, SWT.BORDER | SWT.FULL_SELECTION);
        Table table = tableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData gd_table = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd_table.heightHint = 200;
        gd_table.widthHint = 400;
        table.setLayoutData(gd_table);

        tableViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                return tools.toArray();
            }
        });

        // Enabled column
        TableViewerColumn enabledColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnEnabled = enabledColumn.getColumn();
        tblclmnEnabled.setWidth(50);
        tblclmnEnabled.setText("Enabled");
        enabledColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                // The checkbox is handled by CheckboxTableViewer, so no text is needed here.
                return null;
            }
        });

        // Name column
        TableViewerColumn nameColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnName = nameColumn.getColumn();
        tblclmnName.setWidth(150);
        tblclmnName.setText("Name");
        nameColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
				return ((Tool) element).getName();
            }
        });

        // Description column
        TableViewerColumn descriptionColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnDescription = descriptionColumn.getColumn();
        tblclmnDescription.setWidth(200);
        tblclmnDescription.setText("Description");
        descriptionColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
				return ((Tool) element).getDescription();
            }
        });

        tableViewer.setInput(tools);

        // Set initial checked state
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		for (Tool tool : tools) {
			boolean enabled = store.getBoolean(String.format("%s.%s.%s", PreferenceConstants.CHAT_TOOL_ENABLED_PREFIX,
					tool.getName(), PreferenceConstants.CHAT_TOOL_ENABLED_SUFFIX));
            tableViewer.setChecked(tool, enabled);
        }

        return container;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Manage Specific Tools");
    }

    @Override
    protected void okPressed() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        for (ToolDefinitions.Tool tool : tools) {
            boolean isChecked = tableViewer.getChecked(tool);
            tool.setEnabled(isChecked); // Update the model
			store.setValue(String.format("%s.%s.%s", PreferenceConstants.CHAT_TOOL_ENABLED_PREFIX, tool.getName(),
					PreferenceConstants.CHAT_TOOL_ENABLED_SUFFIX), isChecked);
        }
        // Potentially trigger a refresh or notification if needed by other parts of the application
        Activator.getDefault().triggerConfigChangeNotification();
        super.okPressed();
    }
}
