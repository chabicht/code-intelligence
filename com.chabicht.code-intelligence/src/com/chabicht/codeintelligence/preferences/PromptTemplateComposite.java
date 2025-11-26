package com.chabicht.codeintelligence.preferences;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
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
import org.eclipse.swt.widgets.Table;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.model.DefaultPrompts;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.code_intelligence.util.ModelUtil;

public class PromptTemplateComposite extends Composite {

	private WritableList<PromptTemplate> templates;
	private TableViewer tableViewer;
	private Composite buttonBox;
	private Button addButton;
	private Button editButton;
	private Button removeButton;
	private Button upButton;
	private Button downButton;
	private List<AiApiConnection> apiConnections;
	private Supplier<String> instructModelIdGetter;
	private Supplier<String> chatModelIdGetter;

	public PromptTemplateComposite(Composite parent, int style, WritableList<PromptTemplate> templates,
			List<AiApiConnection> apiConnections, Supplier<String> instructModelIdGetter,
			Supplier<String> chatModelIdGetter) {
		super(parent, style);
		this.templates = templates;
		this.apiConnections = apiConnections;
		this.instructModelIdGetter = instructModelIdGetter;
		this.chatModelIdGetter = chatModelIdGetter;
		
		setLayout(new GridLayout(2, false));
		createContent();
	}

	private void createContent() {
		Label label = new Label(this, SWT.NONE);
		label.setText("Prompt Templates:");
		GridData gdLabel = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		label.setLayoutData(gdLabel);

		tableViewer = createTableViewer(this);
		GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tableData.heightHint = 150;
		tableViewer.getTable().setLayoutData(tableData);

		buttonBox = createButtonBox(this);
		GridData buttonData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
		buttonBox.setLayoutData(buttonData);
	}

	private TableViewer createTableViewer(Composite parent) {
		TableViewer viewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.BORDER);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		viewer.setContentProvider(new ObservableListContentProvider<>());

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

		// Column: Type
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

		// Column: Enabled
		TableViewerColumn colEnabled = new TableViewerColumn(viewer, SWT.NONE);
		colEnabled.getColumn().setText("Enabled");
		colEnabled.getColumn().setWidth(75);
		colEnabled.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((PromptTemplate) element).isEnabled() ? "\u2705" : "\u274C";
			}
		});

		viewer.addDoubleClickListener(event -> editTemplate());

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateButtonEnablement();
			}
		});

		viewer.setInput(templates);
		return viewer;
	}

	private Composite createButtonBox(Composite parent) {
		Composite box = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		box.setLayout(layout);

		addButton = createPushButton(box, "Add...", e -> addNewTemplate());
		editButton = createPushButton(box, "Edit...", e -> editTemplate());
		removeButton = createPushButton(box, "Remove", e -> removeSelectedTemplates());

		upButton = createPushButton(box, "Up", e -> moveTemplateUp());
		downButton = createPushButton(box, "Down", e -> moveTemplateDown());

		updateButtonEnablement();
		return box;
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
			if (upButton != null) upButton.setEnabled(hasSelection && index > 0);
			if (downButton != null) downButton.setEnabled(hasSelection && index < templates.size() - 1);
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
		template.setType(PromptType.INSTRUCT);
		template.setPrompt(DefaultPrompts.INSTRUCT_PROMPT);
		String providerModelString = instructModelIdGetter.get();
		ModelUtil.getProviderModelTuple(providerModelString).ifPresent(t -> {
			template.setConnectionName(t.getFirst());
			template.setModelId(t.getSecond());
		});
		PromptManagementDialog dialog = new PromptManagementDialog(getShell(), apiConnections, template);
		if (dialog.open() == Dialog.OK) {
			templates.add(template);
			tableViewer.refresh();
		}
	}

	private void editTemplate() {
		IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
		if (selection.isEmpty()) {
			return;
		}
		PromptTemplate template = (PromptTemplate) selection.getFirstElement();
		PromptManagementDialog dialog = new PromptManagementDialog(getShell(), apiConnections, template);
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
			tableViewer.refresh();
			tableViewer.setSelection(new StructuredSelection(templates.get(index - 1)));
		}
	}

	private void moveTemplateDown() {
		int index = getSelectedIndex();
		if (index >= 0 && index < templates.size() - 1) {
			Collections.swap(templates, index, index + 1);
			tableViewer.refresh();
			tableViewer.setSelection(new StructuredSelection(templates.get(index + 1)));
		}
	}
}
