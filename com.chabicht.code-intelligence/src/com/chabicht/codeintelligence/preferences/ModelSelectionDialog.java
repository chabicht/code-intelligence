package com.chabicht.codeintelligence.preferences;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.util.ThemeUtil;

public class ModelSelectionDialog extends Dialog {
	private DataBindingContext m_bindingContext;
	private Table table;
	private WritableList<AiModel> models;
	private UIModel uiModel = new UIModel();
	private TableViewer tableViewer;
	private DataBindingContext bindingContext;
	private Text txtFilter;
	private Button btnClearFilter;

	public ModelSelectionDialog(Shell parentShell, List<AiModel> models) {
		super(parentShell);
		this.models = new WritableList<>(models, AiModel.class);
	}

	private static class ModelFilter extends ViewerFilter {
		private String filterText = "";

		public ModelFilter(String filterText) {
			this.filterText = StringUtils.stripToEmpty(filterText);
		}

		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (element instanceof AiModel model) {
				// Split the filter text into individual words.
				String[] filterWords = filterText.trim().split("\\s+"); // \\s+ matches one or more whitespace
																		// characters

				// Get the strings to search within. Important to do this *before* the loop.
				String[] searchStrings = new String[] { model.getApiConnection().getName(), model.getId(),
						model.getName() };

				// Iterate through each filter word. If *any* word doesn't match, return false.
				for (String filterWord : filterWords) {
					boolean wordMatched = false;
					for (String searchString : searchStrings) {
						if (StringUtils.containsIgnoreCase(searchString, filterWord)) {
							wordMatched = true;
							break; // Optimization: Once a word is found, no need to check other strings.
						}
					}
					if (!wordMatched) {
						return false; // Early exit: If any word doesn't match, the whole filter fails.
					}
				}

				// If we get here, all words matched.
				return true;
			}
			return false;
		}

		public void setFilterText(String filterText) {
			this.filterText = filterText;
		}
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		LocalResourceManager rm = new LocalResourceManager(JFaceResources.getResources());

		GridLayout layout = new GridLayout();
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		applyDialogFont(composite);

		// Add filter controls
		Composite filterComposite = new Composite(composite, SWT.NONE);
		filterComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout filterLayout = new GridLayout(2, false); // Two columns: text field and button
		filterLayout.marginWidth = 0; // Remove extra margin
		filterLayout.marginHeight = 0;
		filterComposite.setLayout(filterLayout);

		txtFilter = new Text(filterComposite, SWT.BORDER);
		txtFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		txtFilter.setMessage("Filter models..."); // Placeholder text

		btnClearFilter = new Button(filterComposite, SWT.PUSH);
		btnClearFilter.setImage(rm.create(ImageDescriptor.createFromFile(this.getClass(),
				String.format("/icons/delete_%s.png", ThemeUtil.isDarkTheme() ? "dark" : "light"))));
		btnClearFilter.setToolTipText("Clear filter");
		btnClearFilter.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		btnClearFilter.setEnabled(false);
		txtFilter.addModifyListener(e -> {
			String filterText = txtFilter.getText();
			btnClearFilter.setEnabled(StringUtils.isNotBlank(filterText));
			if (tableViewer.getFilters() != null && tableViewer.getFilters().length == 1) {
				((ModelFilter) tableViewer.getFilters()[0]).setFilterText(filterText);
				tableViewer.refresh();
			} else {
				tableViewer.setFilters(new ModelFilter(filterText));
			}
		});
		btnClearFilter.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			txtFilter.setText("");
			tableViewer.setFilters();
		}));

		tableViewer = new TableViewer(composite, SWT.BORDER | SWT.FULL_SELECTION);
		table = tableViewer.getTable();
		GridData gd_table = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gd_table.widthHint = 753;
		gd_table.heightHint = 500;
		table.setLayoutData(gd_table);
		table.setHeaderVisible(true);

		TableViewerColumn tableViewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnConnection = tableViewerColumn.getColumn();
		tblclmnConnection.setWidth(200);
		tblclmnConnection.setText("Connection");
		tableViewerColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiModel) element).getApiConnection().getName();
			}

		});

		TableViewerColumn tableViewerColumn_1 = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnModelId = tableViewerColumn_1.getColumn();
		tblclmnModelId.setWidth(250);
		tblclmnModelId.setText("Model ID");
		tableViewerColumn_1.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiModel) element).getId();
			}
		});

		TableViewerColumn tableViewerColumn_2 = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tblclmnModelName = tableViewerColumn_2.getColumn();
		tblclmnModelName.setWidth(300);
		tblclmnModelName.setText("Model Name");
		tableViewerColumn_2.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiModel) element).getName();
			}
		});

		tableViewer.addDoubleClickListener(e -> {
			okPressed();
		});

		tableViewer.setContentProvider(new ObservableListContentProvider<AiModel>());
		m_bindingContext = initDataBindings();
		tableViewer.setInput(models);

		return composite;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void okPressed() {
		bindingContext.updateModels();
		super.okPressed();
	}

	private class UIModel {
		private AiModel selectedModel;

		public AiModel getSelectedModel() {
			return selectedModel;
		}

		public void setSelectedModel(AiModel selectedModel) {
			this.selectedModel = selectedModel;
		}
	}

	public AiModel getSelectedModel() {
		return uiModel.getSelectedModel();
	}

	protected DataBindingContext initDataBindings() {
		bindingContext = new DataBindingContext();
		//
		IObservableValue observeSingleSelectionTableViewer = ViewerProperties.singleSelection().observe(tableViewer);
		IObservableValue selectedModelUiModelObserveValue = PojoProperties.value("selectedModel").observe(uiModel);
		bindingContext.bindValue(observeSingleSelectionTableViewer, selectedModelUiModelObserveValue, null, null);
		//
		return bindingContext;
	}
}
