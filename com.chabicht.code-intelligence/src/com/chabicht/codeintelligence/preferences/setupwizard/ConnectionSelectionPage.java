package com.chabicht.codeintelligence.preferences.setupwizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;

public class ConnectionSelectionPage extends WizardPage {
	private DataBindingContext m_bindingContext;
	private Text txtBaseUri;

	private WritableList<ApiType> providers = new WritableList<>();

	private Link lnkApiKey;
	private Text txtApiKey;

	private AiApiConnection connection;
	private ComboViewer cvProvider;
	private boolean apiConnSuccessful;
	private Button btnTestConnection;

	protected ConnectionSelectionPage(String pageName, AiApiConnection connection) {
		super(pageName);

		setPageComplete(false);

		this.connection = connection;
		setTitle("Select a connection provider");
		setMessage("First you have to select the AI provider you want to use."
				+ "\nYou also can customize the URI of the API here."
				+ "\nUsually, you also need to provide an API key to access the API.");
		List<ApiType> list = new ArrayList<>(Arrays.asList(ApiType.values()));
		Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
		providers.addAll(list);
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		container.setLayout(new GridLayout(2, false));

		Label lblProvider = new Label(container, SWT.NONE);
		lblProvider.setText("Provider:");

		cvProvider = new ComboViewer(container, SWT.NONE);
		Combo cboProvider = cvProvider.getCombo();
		cboProvider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblBaseUri = new Label(container, SWT.NONE);
		lblBaseUri.setText("Base URI:");

		txtBaseUri = new Text(container, SWT.BORDER);
		txtBaseUri.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblSpacer = new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);

		Label lblObtainApiKey = new Label(container, SWT.NONE);
		lblObtainApiKey.setText("Obtain API key:");

		lnkApiKey = new Link(container, SWT.NONE);
		lnkApiKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		lnkApiKey.setText("");
		lnkApiKey.setVisible(false);
		lnkApiKey.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			Program.launch(lnkApiKey.getText().replaceAll("</?a>", ""));
		}));

		Label lblApiKey = new Label(container, SWT.NONE);
		lblApiKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblApiKey.setText("API Key:");

		txtApiKey = new Text(container, SWT.BORDER);
		txtApiKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		new Label(container, SWT.NONE);

		btnTestConnection = new Button(container, SWT.NONE);
		btnTestConnection.setText("Test connection");

		m_bindingContext = initDataBindings();
		initData(cvProvider);
		initListeners();
	}

	private void initData(ComboViewer cvProvider) {
		cvProvider.setLabelProvider(new LabelProvider() {

			@Override
			public String getText(Object element) {
				if (element instanceof ApiType a) {
					return a.getName();
				}
				return super.getText(element);
			}
		});
		cvProvider.setContentProvider(new ObservableListContentProvider<ApiType>());
		cvProvider.setInput(providers);
	}

	private void initListeners() {
		cvProvider.addSelectionChangedListener(e -> {
			if (e.getStructuredSelection().getFirstElement() instanceof ApiType a) {
				connection.setName(a.getName());
				txtBaseUri.setText(a.getDefaultBaseUri());

				String apiKeyUri = a.getApiKeyUri();
				if (StringUtils.isNotBlank(apiKeyUri)) {
					lnkApiKey.setVisible(true);
					lnkApiKey.setText("<a>" + apiKeyUri + "</a>");
				} else {
					lnkApiKey.setVisible(false);
				}
			}

			apiConnSuccessful = false;
			checkPageComplete();
		});

		txtBaseUri.addModifyListener(e -> {
			connection.setBaseUri(txtBaseUri.getText());
			apiConnSuccessful = false;
			checkPageComplete();
		});
		txtApiKey.addModifyListener(e -> {
			connection.setApiKey(txtApiKey.getText());
			apiConnSuccessful = false;
			checkPageComplete();
		});

		btnTestConnection.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			try {
				connection.getApiClient(true).getModels();
				apiConnSuccessful = true;
			} catch (Exception ex) {
				// Ignored.
				apiConnSuccessful = false;
			}
			checkPageComplete();
		}));
	}

	private void checkPageComplete() {
		boolean hasConnection = !cvProvider.getSelection().isEmpty();
		boolean hasBaseUri = StringUtils.isNotBlank(txtBaseUri.getText());
		boolean hasApiKey = StringUtils.isNotBlank(txtApiKey.getText()) || ApiType.OLLAMA.equals(connection.getType());
		setPageComplete(hasConnection && hasBaseUri && hasApiKey && apiConnSuccessful);
	}

	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		IObservableValue observeSingleSelectionCvProvider_1 = ViewerProperties.singleSelection().observe(cvProvider);
		IObservableValue typeConnectionObserveValue = BeanProperties.value("type").observe(connection);
		bindingContext.bindValue(observeSingleSelectionCvProvider_1, typeConnectionObserveValue, null, null);
		//
		IObservableValue observeTextTxtBaseUriObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtBaseUri);
		IObservableValue baseUriConnectionObserveValue = BeanProperties.value("baseUri").observe(connection);
		bindingContext.bindValue(observeTextTxtBaseUriObserveWidget, baseUriConnectionObserveValue, null, null);
		//
		IObservableValue observeTextTxtApiKeyObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtApiKey);
		IObservableValue apiKeyConnectionObserveValue = BeanProperties.value("apiKey").observe(connection);
		bindingContext.bindValue(observeTextTxtApiKeyObserveWidget, apiKeyConnectionObserveValue, null, null);
		//
		return bindingContext;
	}
}
