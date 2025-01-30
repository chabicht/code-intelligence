package com.chabicht.codeintelligence.preferences;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ApiConnectionFieldEditor extends FieldEditor {
	private WritableList<AiApiConnection> connections;

	private TableViewer tableViewer;
	private Composite buttonBox;
	private Button addButton;
	private Button removeButton;
	private Button editButton;

	public ApiConnectionFieldEditor(String name, String labelText, Composite parent,
			WritableList<AiApiConnection> connections) {
		super(name, labelText, parent);
		this.connections = connections;
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		Control control = getLabelControl();
		((GridData) control.getLayoutData()).horizontalSpan = numColumns;
		((GridData) tableViewer.getTable().getLayoutData()).horizontalSpan = numColumns - 1;
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		Control control = getLabelControl(parent);
		GridData gd = new GridData();
		gd.horizontalSpan = numColumns;
		gd.verticalAlignment = SWT.TOP;
		control.setLayoutData(gd);

		tableViewer = getTableViewer(parent);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.verticalAlignment = GridData.FILL;
		gd.horizontalSpan = numColumns - 1;
		gd.grabExcessHorizontalSpace = true;
		gd.heightHint = 100;
		tableViewer.getTable().setLayoutData(gd);

		buttonBox = getButtonBoxControl(parent);
		gd = new GridData();
		gd.verticalAlignment = GridData.BEGINNING;
		buttonBox.setLayoutData(gd);
	}

	private TableViewer getTableViewer(Composite parent) {
		if (tableViewer == null) {
			tableViewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.V_SCROLL);
			tableViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			tableViewer.getTable().setHeaderVisible(true);
			tableViewer.getTable().setLinesVisible(true);
			tableViewer.setContentProvider(new ObservableListContentProvider<>());

			TableViewerColumn colName = new TableViewerColumn(tableViewer, SWT.NONE);
			colName.getColumn().setText("Name");
			colName.getColumn().setWidth(200);
			colName.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					return ((AiApiConnection) element).getName();
				}
			});

			TableViewerColumn colBaseUri = new TableViewerColumn(tableViewer, SWT.NONE);
			colBaseUri.getColumn().setText("Base URI");
			colBaseUri.getColumn().setWidth(200);
			colBaseUri.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					return ((AiApiConnection) element).getBaseUri();
				}
			});

			TableViewerColumn colEnabled = new TableViewerColumn(tableViewer, SWT.NONE);
			colEnabled.getColumn().setText("Enabled");
			colEnabled.getColumn().setWidth(100);
			colEnabled.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					// ✅/❎
					return ((AiApiConnection) element).isEnabled() ? "\u2705" : "\u274C";
				}
			});

			tableViewer.addDoubleClickListener(e -> editConnection());

			tableViewer.setInput(connections);
		} else {
			checkParent(tableViewer.getTable(), parent);
		}

		return tableViewer;
	}

	public Composite getButtonBoxControl(Composite parent) {
		if (buttonBox == null) {
			buttonBox = new Composite(parent, SWT.NULL);
			GridLayout layout = new GridLayout();
			layout.marginWidth = 0;
			buttonBox.setLayout(layout);
			createButtons(buttonBox);
			buttonBox.addDisposeListener(event -> {
				editButton = null;
				addButton = null;
				removeButton = null;
				buttonBox = null;
			});

		} else {
			checkParent(buttonBox, parent);
		}

		return buttonBox;
	}

	private void createButtons(Composite box) {
		addButton = createPushButton(box, "Add..", e -> addNewConnection());//$NON-NLS-1$
		editButton = createPushButton(box, "Edit...", e -> editConnection());//$NON-NLS-1$
		removeButton = createPushButton(box, "ListEditor.remove", //$NON-NLS-1$
				e -> connections.removeAll(tableViewer.getStructuredSelection().toList()));
	}

	private void editConnection() {
		IStructuredSelection ssel = tableViewer.getStructuredSelection();
		if (!ssel.isEmpty()) {
			AiApiConnection connection = (AiApiConnection) ssel.getFirstElement();
			AiApiConnectionEditDialog dialog = new AiApiConnectionEditDialog(tableViewer.getTable().getShell(),
					connection);
			if (dialog.open() == AiApiConnectionEditDialog.OK) {
				tableViewer.setInput(connections);
			}
		}
	}

	private void addNewConnection() {
		AiApiConnection connection = new AiApiConnection();
		AiApiConnectionEditDialog dialog = new AiApiConnectionEditDialog(tableViewer.getTable().getShell(), connection);
		if (dialog.open() == AiApiConnectionEditDialog.OK) {
			connections.add(connection);
			tableViewer.setInput(connections);
		}
	}

	private Button createPushButton(Composite parent, String key, Consumer<SelectionEvent> listener) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(JFaceResources.getString(key));
		button.setFont(parent.getFont());
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		int widthHint = convertHorizontalDLUsToPixels(button, IDialogConstants.BUTTON_WIDTH);
		data.widthHint = Math.max(widthHint, button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		button.setLayoutData(data);
		button.addSelectionListener(SelectionListener.widgetSelectedAdapter(listener));
		return button;
	}

	@Override
	protected void doLoad() {
		String value = getPreferenceStore().getString(getPreferenceName());
		connections.clear();
		if (!StringUtils.isEmpty(value)) {
			Type listType = new TypeToken<List<AiApiConnection>>() {
			}.getType();
			List<AiApiConnection> connectionsList = new Gson().fromJson(value, listType);
			connections.addAll(connectionsList);
			tableViewer.setInput(connections);
		}
	}

	@Override
	protected void doLoadDefault() {
		// Doint nothing so the connections aren't lost.
	}

	@Override
	protected void doStore() {
		getPreferenceStore().setValue(getPreferenceName(), new Gson().toJson(connections));
	}

	@Override
	public int getNumberOfControls() {
		return 2;
	}

}
