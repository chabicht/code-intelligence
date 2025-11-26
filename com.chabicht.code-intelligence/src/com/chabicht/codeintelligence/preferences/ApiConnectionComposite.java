package com.chabicht.codeintelligence.preferences;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.PixelConverter;
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
import org.eclipse.swt.widgets.Label;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;

public class ApiConnectionComposite extends Composite {
	private List<AiApiConnection> connections;

	private TableViewer tableViewer;
	private Composite buttonBox;
	private Button addButton;
	private Button removeButton;
	private Button editButton;

	public ApiConnectionComposite(Composite parent, int style, List<AiApiConnection> connections) {
		super(parent, style);
		this.connections = connections;
		setLayout(new GridLayout(2, false));
		
		createContent();
	}

	private void createContent() {
		Label label = new Label(this, SWT.NONE);
		label.setText("API connections:");
		GridData gdLabel = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		label.setLayoutData(gdLabel);

		tableViewer = createTableViewer(this);
		GridData gdTable = new GridData(SWT.FILL, SWT.FILL, true, true);
		gdTable.heightHint = 100;
		tableViewer.getTable().setLayoutData(gdTable);

		buttonBox = createButtonBox(this);
		GridData gdButtons = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
		buttonBox.setLayoutData(gdButtons);
	}

	private TableViewer createTableViewer(Composite parent) {
		TableViewer viewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.BORDER);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
		viewer.setContentProvider(new ObservableListContentProvider<>());

		TableViewerColumn colName = new TableViewerColumn(viewer, SWT.NONE);
		colName.getColumn().setText("Name");
		colName.getColumn().setWidth(200);
		colName.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiApiConnection) element).getName();
			}
		});

		TableViewerColumn colBaseUri = new TableViewerColumn(viewer, SWT.NONE);
		colBaseUri.getColumn().setText("Base URI");
		colBaseUri.getColumn().setWidth(200);
		colBaseUri.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiApiConnection) element).getBaseUri();
			}
		});

		TableViewerColumn colEnabled = new TableViewerColumn(viewer, SWT.NONE);
		colEnabled.getColumn().setText("Enabled");
		colEnabled.getColumn().setWidth(100);
		colEnabled.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiApiConnection) element).isEnabled() ? "\u2705" : "\u274C";
			}
		});

		viewer.addDoubleClickListener(e -> editConnection());
		viewer.setInput(connections);
		
		return viewer;
	}

	private Composite createButtonBox(Composite parent) {
		Composite box = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		box.setLayout(layout);

		addButton = createPushButton(box, "Add...", e -> addNewConnection());
		editButton = createPushButton(box, "Edit...", e -> editConnection());
		removeButton = createPushButton(box, "Remove", e -> {
			connections.removeAll(tableViewer.getStructuredSelection().toList());
			tableViewer.refresh();
		});
		
		return box;
	}

	private void editConnection() {
		IStructuredSelection ssel = tableViewer.getStructuredSelection();
		if (!ssel.isEmpty()) {
			AiApiConnection connection = (AiApiConnection) ssel.getFirstElement();
			AiApiConnectionEditDialog dialog = new AiApiConnectionEditDialog(getShell(), connection);
			if (dialog.open() == AiApiConnectionEditDialog.OK) {
				tableViewer.refresh();
			}
		}
	}

	private void addNewConnection() {
		AiApiConnection connection = new AiApiConnection();
		connection.setEnabled(true);
		AiApiConnectionEditDialog dialog = new AiApiConnectionEditDialog(getShell(), connection);
		if (dialog.open() == AiApiConnectionEditDialog.OK) {
			connections.add(connection);
			tableViewer.refresh();
		}
	}

	private Button createPushButton(Composite parent, String text, Consumer<SelectionEvent> listener) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(text);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		PixelConverter converter = new PixelConverter(button);
		int widthHint = converter.convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		data.widthHint = Math.max(widthHint, button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		button.setLayoutData(data);
		button.addSelectionListener(SelectionListener.widgetSelectedAdapter(listener));
		return button;
	}
	
	public void refresh() {
		if (tableViewer != null && !tableViewer.getTable().isDisposed()) {
			tableViewer.refresh();
		}
	}
}
