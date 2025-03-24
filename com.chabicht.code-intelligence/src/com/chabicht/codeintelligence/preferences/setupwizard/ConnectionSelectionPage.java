package com.chabicht.codeintelligence.preferences.setupwizard;

import java.util.ArrayList;
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

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.model.ProviderDefaults;

public class ConnectionSelectionPage extends WizardPage {
	private DataBindingContext m_bindingContext;
	private Text txtBaseUri;

	private WritableList<ProviderDefaults> providers = new WritableList<>();
	
	private Link lnkApiKey;
	private Text txtApiKey;

	private AiApiConnection connection;
	private ComboViewer cvProvider;
	private boolean apiConnSuccessful;
	private Button btnTestConnection;
	private Label lblConnectionTestResult;

	protected ConnectionSelectionPage(String pageName, AiApiConnection connection) {
		super(pageName);

		setPageComplete(false);

		this.connection = connection;
		setTitle("Select a connection provider");
		setMessage("First you have to configure the AI provider you want to use.");
		
	    List<ProviderDefaults> list = new ArrayList<>(Activator.getDefault().getSupportedProviders().values());
	    Collections.sort(list, (a, b) -> a.getProviderName().compareTo(b.getProviderName()));
	    providers.addAll(list);
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		container.setLayout(new GridLayout(3, false));

		Label lblProvider = new Label(container, SWT.NONE);
		lblProvider.setText("Provider:");

		cvProvider = new ComboViewer(container, SWT.NONE);
		Combo cboProvider = cvProvider.getCombo();
		cboProvider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Label lblBaseUri = new Label(container, SWT.NONE);
		lblBaseUri.setText("Base URI:");

		txtBaseUri = new Text(container, SWT.BORDER);
		txtBaseUri.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Label lblSpacer = new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);

		Label lblObtainApiKey = new Label(container, SWT.NONE);
		lblObtainApiKey.setText("Obtain API key:");

		lnkApiKey = new Link(container, SWT.NONE);
		lnkApiKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		lnkApiKey.setText("");
		lnkApiKey.setVisible(false);
		lnkApiKey.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			Program.launch(lnkApiKey.getText().replaceAll("</?a>", ""));
		}));

		Label lblApiKey = new Label(container, SWT.NONE);
		lblApiKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblApiKey.setText("API Key:");

		txtApiKey = new Text(container, SWT.BORDER);
		txtApiKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		new Label(container, SWT.NONE);

		btnTestConnection = new Button(container, SWT.NONE);
		btnTestConnection.setText("Test connection");

		m_bindingContext = initDataBindings();
		initData(cvProvider);

		lblConnectionTestResult = new Label(container, SWT.NONE);
		lblConnectionTestResult.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		initListeners();
	}

	private void initData(ComboViewer cvProvider) {
	    cvProvider.setLabelProvider(new LabelProvider() {
	        @Override
	        public String getText(Object element) {
	            if (element instanceof ProviderDefaults pd) {
	                return pd.getProviderName();
	            }
	            return super.getText(element);
	        }
	    });
	    cvProvider.setContentProvider(new ObservableListContentProvider<ProviderDefaults>());
	    cvProvider.setInput(providers);
	}

	private void initListeners() {
		cvProvider.addSelectionChangedListener(e -> {
			if (e.getStructuredSelection().getFirstElement() instanceof ProviderDefaults defaults) {
				connection.setName(defaults.getProviderName());
				connection.setType(defaults.getApiType());
				connection.setBaseUri(defaults.getBaseUri());

				((ConnectionSetupWizard) getWizard()).setSelectedProviderDefaults(defaults);

				txtBaseUri.setText(defaults.getBaseUri());

				String apiKeyUri = defaults.getApiKeyUri();
				if (StringUtils.isNotBlank(apiKeyUri)) {
					lnkApiKey.setVisible(true);
					lnkApiKey.setText("<a>" + apiKeyUri + "</a>");
				} else {
					lnkApiKey.setVisible(false);
				}

				if (ApiType.OLLAMA.equals(defaults.getApiType())) {
					txtApiKey.setText("(no key needed)");
				} else if ("(no key needed)".equals(txtApiKey.getText())) {
					txtApiKey.setText("");
				}
			}

			apiConnSuccessful = false;
			lblConnectionTestResult.setText("");
			checkPageComplete();
		});

		txtBaseUri.addModifyListener(e -> {
			connection.setBaseUri(txtBaseUri.getText());
			apiConnSuccessful = false;
			lblConnectionTestResult.setText("");
			checkPageComplete();
		});
		txtApiKey.addModifyListener(e -> {
			connection.setApiKey(txtApiKey.getText());
			apiConnSuccessful = false;
			lblConnectionTestResult.setText("");
			checkPageComplete();
		});

		btnTestConnection.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			try {
				connection.getApiClient(true).getModels();
				apiConnSuccessful = true;
				lblConnectionTestResult.setText("✔️");
			} catch (Exception ex) {
				// Ignored.
				apiConnSuccessful = false;
				lblConnectionTestResult.setText("❌");
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

		IObservableValue<ProviderDefaults> observeSingleSelectionCvProvider = ViewerProperties
				.singleSelection(ProviderDefaults.class).observe(cvProvider);
		IObservableValue<ApiType> apiTypeObservable = BeanProperties.value("apiType", ApiType.class)
				.observe(observeSingleSelectionCvProvider);
		IObservableValue<ApiType> typeConnectionObserveValue = BeanProperties.value("type", ApiType.class)
				.observe(connection);
		bindingContext.bindValue(apiTypeObservable, typeConnectionObserveValue, null, null);

		IObservableValue observeTextTxtBaseUriObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtBaseUri);
		IObservableValue baseUriConnectionObserveValue = BeanProperties.value("baseUri").observe(connection);
		bindingContext.bindValue(observeTextTxtBaseUriObserveWidget, baseUriConnectionObserveValue, null, null);

		IObservableValue observeTextTxtApiKeyObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtApiKey);
		IObservableValue apiKeyConnectionObserveValue = BeanProperties.value("apiKey").observe(connection);
		bindingContext.bindValue(observeTextTxtApiKeyObserveWidget, apiKeyConnectionObserveValue, null, null);

		return bindingContext;
	}

}
