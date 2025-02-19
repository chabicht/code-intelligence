package com.chabicht.codeintelligence.preferences;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;

public class AiApiConnectionEditDialog extends Dialog {
	private final static Map<ApiType, String> DEFAULT_URLS = initDefaultConnectionUrls();

	private Text txtName;
	private Text txtBaseUri;
	private Text txtApiKey;
	private Button btnEnabled;

	private final AiApiConnection model;
	private DataBindingContext bindingContext;
	private ComboViewer cvType;

	private WritableList<ApiType> apiTypes = new WritableList<>();

	protected AiApiConnectionEditDialog(Shell parentShell, AiApiConnection model) {
		super(parentShell);
		this.model = model;
		apiTypes.addAll(Arrays.stream(ApiType.values()).sorted().toList());
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
		GridData layoutData = new GridData(GridData.FILL_BOTH);
		layoutData.widthHint = 300;
		composite.setLayoutData(layoutData);
		applyDialogFont(composite);

		Label lblName = new Label(composite, SWT.NONE);
		lblName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblName.setText("Name:");

		txtName = new Text(composite, SWT.BORDER);
		txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblType = new Label(composite, SWT.NONE);
		lblType.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblType.setText("Type:");

		cvType = new ComboViewer(composite, SWT.NONE);
		Combo cboType = cvType.getCombo();
		cboType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		cvType.setContentProvider(new ObservableListContentProvider<ApiType>());
		cvType.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ApiType apiType) {
					return apiType.getName();
				} else {
					return "";
				}
			}
		});

		Label lblBaseUri = new Label(composite, SWT.NONE);
		lblBaseUri.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblBaseUri.setText("Base URI:");

		txtBaseUri = new Text(composite, SWT.BORDER);
		txtBaseUri.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblApiKey = new Label(composite, SWT.NONE);
		lblApiKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblApiKey.setText("API key:");

		txtApiKey = new Text(composite, SWT.BORDER);
		txtApiKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblEnabled = new Label(composite, SWT.NONE);
		lblEnabled.setText("Enabled:");

		btnEnabled = new Button(composite, SWT.CHECK);

		cvType.setInput(apiTypes);

		initDataBinding();
		initListeners();

		return composite;
	}

	private void initListeners() {
		model.addPropertyChangeListener("type", e -> {
			ApiType type = (ApiType) e.getNewValue();

			txtBaseUri.setEnabled(!ApiType.GEMINI.equals(type));

			presetBaseUri(type);
		});
		if (StringUtils.isBlank(model.getBaseUri())) {
			presetBaseUri(model.getType());
		}
	}

	private void presetBaseUri(ApiType type) {
		String baseUri = model.getBaseUri();
		if (StringUtils.isBlank(baseUri) || DEFAULT_URLS.values().contains(baseUri)) {
			if (DEFAULT_URLS.keySet().contains(type)) {
				txtBaseUri.setText(DEFAULT_URLS.get(type));
			} else {
				txtBaseUri.setText("");
			}
		}
	}

	private void initDataBinding() {
		bindingContext = new DataBindingContext();
		bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(txtName),
				BeanProperties.value("name", String.class).observe(model));
		bindingContext.bindValue(ViewerProperties.singleSelection().observe(cvType),
				BeanProperties.value("type").observe(model));
		bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(txtBaseUri),
				BeanProperties.value("baseUri", String.class).observe(model));
		bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observe(txtApiKey),
				BeanProperties.value("apiKey", String.class).observe(model));
		bindingContext.bindValue(WidgetProperties.buttonSelection().observe(btnEnabled),
				BeanProperties.value("enabled", Boolean.class).observe(model));
	}

	@Override
	protected void okPressed() {
		bindingContext.updateModels();

		super.okPressed();
	}

	private static Map<ApiType, String> initDefaultConnectionUrls() {
		return Map.of(ApiType.OLLAMA, "http://localhost:11434", ApiType.OPENAI, "https://api.openai.com/v1",
				ApiType.ANTHROPIC, "https://api.anthropic.com/v1");
	}
}
