package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
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
	private PreferenceValidationSupport.ValidationResult currentValidationResult = PreferenceValidationSupport.ValidationResult
			.ok();

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
	private Button chkChatSubmitOnEnter;

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

		Font boldFont = JFaceResources.getResources()
				.create(FontDescriptor.createFrom(main.getFont()).setStyle(SWT.BOLD));

		// Top Buttons
		createTopButtons(main);

		// API Connections
		createSeparator(main);
		apiConnectionComp = new ApiConnectionComposite(main, SWT.NONE, connections, this::validate);
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

		chkChatToolsEnabled = createBooleanField(main, "Enable Tools globally in Chat", null);
		chkChatToolsApplyDeferred = createBooleanField(main,
				"Collect tool calls and execute together at the end of a streak",
				"""
						If enabled, modifications from tool calls are recorded and provided for review in one, big chunk when the model is finished editing.
						Otherwise, each modification triggers a separate review dialog as soon as the tool is called.
						""");
		chkChatSubmitOnEnter = createBooleanField(main, "Submit message on Enter (Shift+Enter for new line)",
				"""
						Controls how the Enter key is handled and the current chat message is submitted.
						Default behavior: Enter adds a newline to the text, Ctrl+Enter (or Command+Enter on macOS) submit the message.
						""");

		createManageToolsButton(main);

		// Prompt Templates
		createSeparator(main);
		promptTemplateComp = new PromptTemplateComposite(main, SWT.NONE, templates, connections,
				() -> txtCompletionModel.getText(), () -> txtChatModel.getText());
		GridData gdPrompt = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
		promptTemplateComp.setLayoutData(gdPrompt);

		// Debug Section
		createSeparator(main);
		chkDebugLogPrompts = createBooleanField(main, "Log prompts to Error Log", null);

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
			if (apiConnectionComp != null)
				apiConnectionComp.refresh();
			validate();
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
				if (model != null) {
					text.setText(PreferenceValidationSupport
							.normalizeConfiguredModel(model.getApiConnection().getName() + "/" + model.getId()));
					validate();
				}
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

	private Button createBooleanField(Composite parent, String labelText, String tooltipText) {
		Button button = new Button(parent, SWT.CHECK);
		button.setText(labelText);
		button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

		if (StringUtils.isNoneBlank(tooltipText)) {
			button.setToolTipText(tooltipText);
		}

		return button;
	}

	private void createSeparator(Composite parent) {
		Label ruler = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
	}

	private void initializeValues() {
		IPreferenceStore store = getPreferenceStore();

		txtCompletionModel.setText(store.getString(PreferenceConstants.COMPLETION_MODEL_NAME));
		txtCompletionMaxTokens
				.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS)));
		txtCompletionContextBefore
				.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE)));
		txtCompletionContextAfter
				.setText(Integer.toString(store.getInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER)));

		txtChatModel.setText(store.getString(PreferenceConstants.CHAT_MODEL_NAME));
		txtChatMaxTokens.setText(Integer.toString(store.getInt(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS)));
		txtChatHistorySize.setText(Integer.toString(store.getInt(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT)));

		chkChatToolsEnabled.setSelection(store.getBoolean(PreferenceConstants.CHAT_TOOLS_ENABLED));
		chkChatToolsApplyDeferred.setSelection(store.getBoolean(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED));
		chkChatSubmitOnEnter.setSelection(store.getBoolean(PreferenceConstants.CHAT_SUBMIT_ON_ENTER));

		chkDebugLogPrompts.setSelection(store.getBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS));

		validate();
	}

	@Override
	public boolean performOk() {
		validate();
		if (currentValidationResult.isError()) {
			return false;
		}
		if (currentValidationResult.isWarning() && !confirmSaveDespiteWarnings()) {
			return false;
		}

		IPreferenceStore store = getPreferenceStore();

		store.setValue(PreferenceConstants.COMPLETION_MODEL_NAME,
				PreferenceValidationSupport.normalizeConfiguredModel(txtCompletionModel.getText()));
		store.setValue(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS,
				Integer.parseInt(txtCompletionMaxTokens.getText()));
		store.setValue(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE,
				Integer.parseInt(txtCompletionContextBefore.getText()));
		store.setValue(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER,
				Integer.parseInt(txtCompletionContextAfter.getText()));

		store.setValue(PreferenceConstants.CHAT_MODEL_NAME,
				PreferenceValidationSupport.normalizeConfiguredModel(txtChatModel.getText()));
		store.setValue(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS, Integer.parseInt(txtChatMaxTokens.getText()));
		store.setValue(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT, Integer.parseInt(txtChatHistorySize.getText()));

		store.setValue(PreferenceConstants.CHAT_TOOLS_ENABLED, chkChatToolsEnabled.getSelection());
		store.setValue(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED, chkChatToolsApplyDeferred.getSelection());
		store.setValue(PreferenceConstants.CHAT_SUBMIT_ON_ENTER, chkChatSubmitOnEnter.getSelection());

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
		txtCompletionMaxTokens
				.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS)));
		txtCompletionContextBefore
				.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE)));
		txtCompletionContextAfter
				.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER)));

		txtChatModel.setText(store.getDefaultString(PreferenceConstants.CHAT_MODEL_NAME));
		txtChatMaxTokens.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS)));
		txtChatHistorySize.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT)));

		chkChatToolsEnabled.setSelection(store.getDefaultBoolean(PreferenceConstants.CHAT_TOOLS_ENABLED));
		chkChatToolsApplyDeferred
				.setSelection(store.getDefaultBoolean(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED));
		chkChatSubmitOnEnter.setSelection(store.getDefaultBoolean(PreferenceConstants.CHAT_SUBMIT_ON_ENTER));

		chkDebugLogPrompts.setSelection(store.getDefaultBoolean(PreferenceConstants.DEBUG_LOG_PROMPTS));

		validate();
		super.performDefaults();
	}

	private void validate() {
		currentValidationResult = validatePreferencePage();
		applyValidationResult(currentValidationResult);
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
					Activator.logError("Error loading models for connection " + conn.getName() + ": " + e.getMessage(),
							e);
				}
			}
		}
		return models;
	}

	private PreferenceValidationSupport.ValidationResult validatePreferencePage() {
		PreferenceValidationSupport.ValidationResult validationResult = PreferenceValidationSupport.ValidationResult.ok();

		validationResult = mergeValidationResult(validationResult,
				PreferenceValidationSupport.validateModel(txtCompletionModel.getText(), "Completion Model", connections));
		validationResult = mergeValidationResult(validationResult,
				PreferenceValidationSupport.validateModel(txtChatModel.getText(), "Chat Model", connections));
		validationResult = mergeValidationResult(validationResult, PreferenceValidationSupport
				.validateInt(txtCompletionMaxTokens.getText(), "Completion Max Tokens"));
		validationResult = mergeValidationResult(validationResult, PreferenceValidationSupport
				.validateInt(txtCompletionContextBefore.getText(), "Completion Context Before"));
		validationResult = mergeValidationResult(validationResult, PreferenceValidationSupport
				.validateInt(txtCompletionContextAfter.getText(), "Completion Context After"));
		validationResult = mergeValidationResult(validationResult,
				PreferenceValidationSupport.validateInt(txtChatMaxTokens.getText(), "Chat Max Tokens"));
		validationResult = mergeValidationResult(validationResult,
				PreferenceValidationSupport.validateInt(txtChatHistorySize.getText(), "Chat History Size"));

		return validationResult;
	}

	private PreferenceValidationSupport.ValidationResult mergeValidationResult(
			PreferenceValidationSupport.ValidationResult current,
			PreferenceValidationSupport.ValidationResult candidate) {
		if (current.isError()) {
			return current;
		}
		if (candidate.isError()) {
			return candidate;
		}
		if (current.isOk() && candidate.isWarning()) {
			return candidate;
		}
		return current;
	}

	private void applyValidationResult(PreferenceValidationSupport.ValidationResult validationResult) {
		setErrorMessage(null);
		setMessage(null);
		setValid(true);

		if (validationResult.isError()) {
			setErrorMessage(validationResult.message());
			setValid(false);
		} else if (validationResult.isWarning()) {
			setMessage(validationResult.message(), IMessageProvider.WARNING);
		}
	}

	private boolean confirmSaveDespiteWarnings() {
		return MessageDialog.openQuestion(getShell(), "Save Invalid Preferences",
				"The Code Intelligence preferences contain invalid values.\n\n"
						+ currentValidationResult.message()
						+ "\n\nDo you want to save anyway?");
	}
}
