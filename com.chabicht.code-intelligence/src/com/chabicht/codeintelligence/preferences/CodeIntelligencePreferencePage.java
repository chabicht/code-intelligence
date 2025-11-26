package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.codeintelligence.preferences.setupwizard.ConnectionSetupWizard;

public class CodeIntelligencePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	
	private WritableList<AiApiConnection> connections;
	private WritableList<PromptTemplate> templates;

	// UI Controls
	private ApiConnectionComposite apiConnectionComp;
	private PromptTemplateComposite promptTemplateComp;
	
	private Text txtCompletionModel;
	private Text txtCompletionMaxTokens;
	private Text txtCompletionContextBefore;
	private Text txtCompletionContextAfter;
	
	private Text txtChatModel;
	private Text txtChatMaxTokens;
	private Text txtChatHistorySize;
	private Button chkChatToolsEnabled;
	private Button chkChatToolsApplyDeferred;
	
	private Button chkDebugLogPrompts;

	public CodeIntelligencePreferencePage() {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Preferences for the Code Intelligence plugin");
	}

	@Override
	public void init(IWorkbench workbench) {
		// Load connections and templates initially
		connections = new WritableList<>(Activator.getDefault().loadApiConnections(), AiApiConnection.class);
		templates = new WritableList<>(Activator.getDefault().loadPromptTemplates(), PromptTemplate.class);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite main = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		main.setLayout(layout);
		
		Font boldFont = JFaceResources.getResources().create(FontDescriptor.createFrom(main.getFont()).setStyle(SWT.BOLD));

		// Top Buttons
		createTopButtons(main);

		// API Connections
		createSeparator(main);
		apiConnectionComp = new ApiConnectionComposite(main, SWT.NONE, connections);
		GridData gdApi = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
		apiConnectionComp.setLayoutData(gdApi);

		// Custom Params Button
		createCustomParamsButton(main);

		// Completion Section
		createSeparator(main);
		Label lblCompletion = new Label(main, SWT.NONE);
		lblCompletion.setText("Completion:");
		lblCompletion.setFont(boldFont);
		lblCompletion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));

		txtCompletionModel = createModelField(main, "Model:");
		txtCompletionMaxTokens = createNumberTextField(main, "Max. response tokens:");
		txtCompletionContextBefore = createNumberTextField(main, "Context lines before cursor:");
		txtCompletionContextAfter = createNumberTextField(main, "Context lines after cursor:");

		// Chat Section
		createSeparator(main);
		Label lblChat = new Label(main, SWT.NONE);
		lblChat.setText("Chat:");
		lblChat.setFont(boldFont);
		lblChat.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));

		txtChatModel = createModelField(main, "Model:");
		txtChatMaxTokens = createNumberTextField(main, "Max. response tokens:");
		txtChatHistorySize = createNumberTextField(main, "Max. history items:");
		
		chkChatToolsEnabled = createBooleanField(main, "Enable Tools globally in Chat");
		chkChatToolsApplyDeferred = createBooleanField(main, "Collect tool calls and execute together at the end of a streak");
		
		createManageToolsButton(main);

		// Prompt Templates
		createSeparator(main);
		promptTemplateComp = new PromptTemplateComposite(main, SWT.NONE, templates, connections, 
				() -> txtCompletionModel.getText(), 
				() -> txtChatModel.getText());
		GridData gdPrompt = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
		promptTemplateComp.setLayoutData(gdPrompt);

		// Debug Section
		createSeparator(main);
		chkDebugLogPrompts = createBooleanField(main, "Log prompts to Error Log");

		initializeValues();
		
		// Add listeners for validation
		FocusListener validationListener = FocusListener.focusLostAdapter(e -> validate());
		txtCompletionModel.addFocusListener(validationListener);
		txtChatModel.addFocusListener(validationListener);
		
		txtCompletionMaxTokens.addFocusListener(validationListener);
		txtCompletionContextBefore.addFocusListener(validationListener);
		txtCompletionContextAfter.addFocusListener(validationListener);
		txtChatMaxTokens.addFocusListener(validationListener);
		txtChatHistorySize.addFocusListener(validationListener);
		
		return main;
	}

	private void createTopButtons(Composite parent) {
		Button btnSetupWizardDialog = new Button(parent, SWT.NONE);
		btnSetupWizardDialog.setText("Setup wizard...");
		btnSetupWizardDialog.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
		btnSetupWizardDialog.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			WizardDialog dlg = new WizardDialog(getShell(), new ConnectionSetupWizard());
			dlg.open();
			// Refresh connections
			connections.clear();
			connections.addAll(Activator.getDefault().loadApiConnections());
			if (apiConnectionComp != null) apiConnectionComp.refresh();
		}));
	}

	private void createCustomParamsButton(Composite parent) {
		Button btnCustomParamsDlog = new Button(parent, SWT.NONE);
		btnCustomParamsDlog.setText("Custom params...");
		btnCustomParamsDlog.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
		btnCustomParamsDlog.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			CustomConfigurationParametersDialog dlg = new CustomConfigurationParametersDialog(
					Display.getCurrent().getActiveShell(), connections);
			dlg.open();
		}));
	}
	
	private void createManageToolsButton(Composite parent) {
		Button btnManageTools = new Button(parent, SWT.NONE);
		btnManageTools.setText("Manage Specific Tools...");
		btnManageTools.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));
		btnManageTools.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			ManageToolsDialog dlg = new ManageToolsDialog(getShell());
			dlg.open();
		}));
	}

	private Text createModelField(Composite parent, String labelText) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		
		Text text = new Text(parent, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Button btnBrowse = new Button(parent, SWT.PUSH);
		btnBrowse.setText("Browse...");
		btnBrowse.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			List<AiModel> models = loadAvailableModels();
			ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
			if (dialog.open() == ModelSelectionDialog.OK) {
				AiModel model = dialog.getSelectedModel();
				text.setText(model.getApiConnection().getName() + "/" + model.getId());
			}
		}));
		
		return text;
	}

	private Text createNumberTextField(Composite parent, String labelText) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		
		Text text = new Text(parent, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		return text;
	}
	
	private Button createBooleanField(Composite parent, String labelText) {
		Button button = new Button(parent, SWT.CHECK);
		button.setText(labelText);
		button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		return button;
	}

	private void createSeparator(Composite parent) {
		Label ruler = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
	}

	private void initializeValues() {
		IPreferenceStore store = getPreferenceStore();
		
		txtCompletionModel.setText(store.getString(PreferenceConstants.COMPLETION_MODEL_NAME));
		txtCompletionMaxTokens.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS)));
		txtCompletionContextBefore.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE)));
		txtCompletionContextAfter.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER)));
		
		txtChatModel.setText(store.getString(PreferenceConstants.CHAT_MODEL_NAME));
		txtChatMaxTokens.setText(Integer.toString(store.getInt(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS)));
		txtChatHistorySize.setText(Integer.toString(store.getInt(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT)));
		
		chkChatToolsEnabled.setSelection(store.getBoolean(PreferenceConstants.CHAT_TOOLS_ENABLED));
		chkChatToolsApplyDeferred.setSelection(store.getBoolean(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED));
		
		chkDebugLogPrompts.setSelection(store.getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS));
		
		validate();
	}

	@Override
	public boolean performOk() {
		IPreferenceStore store = getPreferenceStore();
		
		store.setValue(PreferenceConstants.COMPLETION_MODEL_NAME, txtCompletionModel.getText());
		store.setValue(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS, Integer.parseInt(txtCompletionMaxTokens.getText()));
		store.setValue(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE, Integer.parseInt(txtCompletionContextBefore.getText()));
		store.setValue(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER, Integer.parseInt(txtCompletionContextAfter.getText()));
		
		store.setValue(PreferenceConstants.CHAT_MODEL_NAME, txtChatModel.getText());
		store.setValue(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS, Integer.parseInt(txtChatMaxTokens.getText()));
		store.setValue(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT, Integer.parseInt(txtChatHistorySize.getText()));
		
		store.setValue(PreferenceConstants.CHAT_TOOLS_ENABLED, chkChatToolsEnabled.getSelection());
		store.setValue(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED, chkChatToolsApplyDeferred.getSelection());
		
		store.setValue(PreferenceConstants.DEBUG_LOG_PROMPTS, chkDebugLogPrompts.getSelection());
		
		// Save connections and templates
		Activator.getDefault().saveApiConnections(connections);
		Activator.getDefault().savePromptTemplates(templates);
		
		Activator.getDefault().triggerConfigChangeNotification();
		return super.performOk();
	}
	
	@Override
	protected void performDefaults() {
		IPreferenceStore store = getPreferenceStore();
		
		txtCompletionModel.setText(store.getDefaultString(PreferenceConstants.COMPLETION_MODEL_NAME));
		txtCompletionMaxTokens.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS)));
		txtCompletionContextBefore.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE)));
		txtCompletionContextAfter.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER)));
		
		txtChatModel.setText(store.getDefaultString(PreferenceConstants.CHAT_MODEL_NAME));
		txtChatMaxTokens.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS)));
		txtChatHistorySize.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT)));
		
		chkChatToolsEnabled.setSelection(store.getDefaultBoolean(PreferenceConstants.CHAT_TOOLS_ENABLED));
		chkChatToolsApplyDeferred.setSelection(store.getDefaultBoolean(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED));
		
		chkDebugLogPrompts.setSelection(store.getDefaultBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS));
		
		validate();
		super.performDefaults();
	}

	private void validate() {
		setErrorMessage(null);
		setValid(true);
		
		if (!validateModel(txtCompletionModel.getText(), "Completion Model")) return;
		if (!validateModel(txtChatModel.getText(), "Chat Model")) return;
		
		if (!validateInt(txtCompletionMaxTokens.getText(), "Completion Max Tokens")) return;
		if (!validateInt(txtCompletionContextBefore.getText(), "Completion Context Before")) return;
		if (!validateInt(txtCompletionContextAfter.getText(), "Completion Context After")) return;
		if (!validateInt(txtChatMaxTokens.getText(), "Chat Max Tokens")) return;
		if (!validateInt(txtChatHistorySize.getText(), "Chat History Size")) return;
	}
	
	private boolean validateInt(String value, String fieldName) {
		try {
			Integer.parseInt(value);
			return true;
		} catch (NumberFormatException e) {
			setErrorMessage(fieldName + " must be a valid integer");
			setValid(false);
			return false;
		}
	}
	
	private boolean validateModel(String value, String fieldName) {
		if (value == null || value.trim().isEmpty()) {
			setErrorMessage(fieldName + " must be specified");
			setValid(false);
			return false;
		}

		String[] parts = value.split("/", 2);
		if (parts.length != 2) {
			setErrorMessage("Invalid format for " + fieldName + ". Expected: connectionName/modelId");
			setValid(false);
			return false;
		}

		String connectionName = StringUtils.trim(parts[0]);
		String modelId = StringUtils.trim(parts[1]);

		AiApiConnection targetConnection = findConnectionByName(connectionName);

		if (targetConnection == null) {
			setErrorMessage("Connection '" + connectionName + "' not found for " + fieldName);
			setValid(false);
			return false;
		}

		if (!targetConnection.isEnabled()) {
			setErrorMessage("Connection '" + connectionName + "' is disabled");
			setValid(false);
			return false;
		}

		try {
			List<AiModel> models = targetConnection.getApiClient().getModels();
			boolean modelExists = models.stream().anyMatch(m -> m.getId().equals(modelId));
			
			if (!modelExists) {
				setErrorMessage("Model '" + modelId + "' not found in connection '" + connectionName + "'");
				setValid(false);
				return false;
			}
		} catch (RuntimeException e) {
			setErrorMessage("Error validating " + fieldName + ": " + e.getMessage());
			setValid(false);
			return false;
		}
		
		return true;
	}

	private AiApiConnection findConnectionByName(String connectionName) {
		for (AiApiConnection conn : connections) {
			if (StringUtils.equals(StringUtils.stripToEmpty(conn.getName()),
					StringUtils.stripToEmpty(connectionName))) {
				return conn;
			}
		}
		return null;
	}

	private List<AiModel> loadAvailableModels() {
		List<AiModel> models = new ArrayList<>();
		for (AiApiConnection conn : connections) {
			if (conn.isEnabled()) {
				try {
					List<AiModel> modelsFromConn = conn.getApiClient().getModels();
					modelsFromConn.sort((m1, m2) -> m1.getName().compareTo(m2.getName()));
					models.addAll(modelsFromConn);
				} catch (RuntimeException e) {
					Activator.logError(
							"Error loading models for connection " + conn.getName() + ": " + e.getMessage(), e);
				}
			}
		}
		return models;
	}
}
