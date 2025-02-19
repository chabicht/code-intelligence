package com.chabicht.codeintelligence.preferences;

import java.util.List;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import com.chabicht.code_intelligence.apiclient.AiModel;

public class ModelSelectionDialog extends Dialog {
	private DataBindingContext m_bindingContext;
	private Table table;
	private WritableList<AiModel> models;
	private UIModel uiModel = new UIModel();
	private TableViewer tableViewer;
	private DataBindingContext bindingContext;

	public ModelSelectionDialog(Shell parentShell, List<AiModel> models) {
		super(parentShell);
		this.models = new WritableList<>(models, AiModel.class);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		GridLayout layout = new GridLayout();
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		applyDialogFont(composite);

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
