package com.chabicht.codeintelligence.preferences;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;

import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * A field editor for managing PromptTemplates.
 */
public class PromptTemplateFieldEditor extends FieldEditor {

	private List templates;
	private TableViewer tableViewer;
	private Composite buttonBox;
	private Button addButton;
	private Button editButton;
	private Button removeButton;
	private Button upButton;
	private Button downButton;

	/**
	 * ◦Constructs a PromptTemplateFieldEditor. ◦ ◦@param name the property name
	 * ◦@param labelText the label to display ◦@param parent the parent composite
	 * ◦@param templates the list of PromptTemplate objects to manage
	 */
	public PromptTemplateFieldEditor(String name, String labelText, Composite parent, List templates) {
		super(name, labelText, parent);
		this.templates = templates;
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		Control labelControl = getLabelControl();
		GridData gd = (GridData) labelControl.getLayoutData();
		gd.horizontalSpan = numColumns;
		GridData tableGD = (GridData) tableViewer.getTable().getLayoutData(); // leave one column for the button box.
		tableGD.horizontalSpan = numColumns - 1;
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) { // Add the label control Control control =
																		// getLabelControl(parent); GridData gridData =
																		// new GridData(); gridData.horizontalSpan =
																		// numColumns; control.setLayoutData(gridData);
		// Create the table viewer control
		tableViewer = createTableViewer(parent);
		GridData tableData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		tableData.verticalAlignment = GridData.FILL;
		tableData.horizontalSpan = numColumns - 1;
		tableData.grabExcessHorizontalSpace = true;
		tableData.heightHint = 150;
		tableViewer.getTable().setLayoutData(tableData);

		// Create the button box control
		buttonBox = createButtonBox(parent);
		GridData buttonData = new GridData();
		buttonData.verticalAlignment = GridData.BEGINNING;
		buttonBox.setLayoutData(buttonData);

	}

	private TableViewer createTableViewer(Composite parent) {
		TableViewer viewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.BORDER);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		viewer.setContentProvider(new ObservableListContentProvider<>());
		// Create columns

		// Column: Name
		TableViewerColumn colName = new TableViewerColumn(viewer, SWT.NONE);
		colName.getColumn().setText("Name");
		colName.getColumn().setWidth(150);
		colName.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((PromptTemplate) element).getName();
			}
		});

		// Column: Type (based on PromptType)
		TableViewerColumn colType = new TableViewerColumn(viewer, SWT.NONE);
		colType.getColumn().setText("Type");
		colType.getColumn().setWidth(75);
		colType.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				PromptType type = ((PromptTemplate) element).getType();
				return type == null ? "" : type.getLabel();
			}
		});

		// Column: Connection
		TableViewerColumn colConnection = new TableViewerColumn(viewer, SWT.NONE);
		colConnection.getColumn().setText("Connection");
		colConnection.getColumn().setWidth(120);
		colConnection.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				String conn = ((PromptTemplate) element).getConnectionName();
				return StringUtils.isEmpty(conn) ? "Default" : conn;
			}
		});

		// Column: Model ID
		TableViewerColumn colModel = new TableViewerColumn(viewer, SWT.NONE);
		colModel.getColumn().setText("Model ID");
		colModel.getColumn().setWidth(100);
		colModel.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				String model = ((PromptTemplate) element).getModelId();
				return model == null ? "" : model;
			}
		});

		// Column: Enabled (using check-mark or cross)
		TableViewerColumn colEnabled = new TableViewerColumn(viewer, SWT.NONE);
		colEnabled.getColumn().setText("Enabled");
		colEnabled.getColumn().setWidth(75);
		colEnabled.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((PromptTemplate) element).isEnabled() ? "\u2705" : "\u274C";
			}
		});

		// Double-click to edit the template.
		viewer.addDoubleClickListener(event -> editTemplate());

		// Update button enablement when selection changes.
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateButtonEnablement();
			}
		});

		// Set the input list.
		viewer.setInput(templates);
		return viewer;

	}

	private Composite createButtonBox(Composite parent) {
		Composite box = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.numColumns = 1;
		box.setLayout(layout);
		// Create the buttons.
		addButton = createPushButton(box, "Add...", e -> addNewTemplate());
		editButton = createPushButton(box, "Edit...", e -> editTemplate());
		removeButton = createPushButton(box, "Remove", e -> removeSelectedTemplates());

		// Create reordering buttons.
		upButton = createPushButton(box, "Up", e -> moveTemplateUp());
		downButton = createPushButton(box, "Down", e -> moveTemplateDown());

		updateButtonEnablement();
		return box;

	}

	private Button createPushButton(Composite parent, String text, Consumer listener) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(JFaceResources.getString(text));
		button.setFont(parent.getFont());
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		int widthHint = convertHorizontalDLUsToPixels(button, IDialogConstants.BUTTON_WIDTH);
		data.widthHint = Math.max(widthHint, button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		button.setLayoutData(data);
		button.addSelectionListener(SelectionListener.widgetSelectedAdapter(listener));
		return button;
	}

	private void updateButtonEnablement() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		boolean hasSelection = !selection.isEmpty();
		if (editButton != null) {
			editButton.setEnabled(hasSelection);
		}
		if (removeButton != null) {
			removeButton.setEnabled(hasSelection);
		}
		if (upButton != null || downButton != null) {
			int index = getSelectedIndex();
			upButton.setEnabled(hasSelection && index > 0);
			downButton.setEnabled(hasSelection && index < templates.size() - 1);
		}
	}

	private int getSelectedIndex() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		if (selection.isEmpty()) {
			return -1;
		}
		Object element = selection.getFirstElement();
		return templates.indexOf(element);
	}

	private void addNewTemplate() {
		PromptTemplate template = new PromptTemplate();
		// Optionally set defaults here.
		PromptManagementDialog dialog = new PromptManagementDialog(tableViewer.getTable().getShell(),
				/* Pass relevant connection list if needed */ Collections.emptyList(), template);
		if (dialog.open() == Dialog.OK) {
			templates.add(template);
			tableViewer.setInput(templates);
			tableViewer.refresh();
		}
	}

	private void editTemplate() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		if (selection.isEmpty()) {
			return;
		}
		PromptTemplate template = (PromptTemplate) selection.getFirstElement();
		PromptManagementDialog dialog = new PromptManagementDialog(tableViewer.getTable().getShell(),
				Collections.emptyList(), template);
		if (dialog.open() == Dialog.OK) {
			tableViewer.refresh();
		}
	}

	private void removeSelectedTemplates() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		if (!selection.isEmpty()) {
			templates.removeAll(selection.toList());
			tableViewer.refresh();
		}
	}

	private void moveTemplateUp() {
		int index = getSelectedIndex();
		if (index > 0) {
			Collections.swap(templates, index, index - 1);
			tableViewer.refresh(); // Preserve selection on the moved element.
			tableViewer.setSelection(new StructuredSelection(templates.get(index - 1)));
		}
	}

	private void moveTemplateDown() {
		int index = getSelectedIndex();
		if (index >= 0 && index < templates.size() - 1) {
			Collections.swap(templates, index, index + 1);
			tableViewer.refresh(); // Preserve selection on the moved element.
			tableViewer.setSelection(new StructuredSelection(templates.get(index + 1)));
		}
	}

	@Override
	protected void doLoad() { // Retrieve the stored JSON value from the preference store.
		String value = getPreferenceStore().getString(getPreferenceName());
		templates.clear();
		if (!StringUtils.isEmpty(value)) {
			Type listType = new TypeToken<List>() {
			}.getType();
			List loadedTemplates = new Gson().fromJson(value, listType);
			templates.addAll(loadedTemplates);
			tableViewer.setInput(templates);
		}
	}

	@Override
	protected void doLoadDefault() {
		// Do nothing so that no templates are reset inadvertently.
	}

	@Override
	protected void doStore() {
		getPreferenceStore().setValue(getPreferenceName(), new Gson().toJson(templates));
	}

	@Override
	public int getNumberOfControls() {
		return 2;
	}
}
