package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.codeintelligence.preferences.setupwizard.ConnectionSetupWizard;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */
public class CodeIntelligencePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	WritableList<AiApiConnection> connections = new WritableList<>();
	private List<FieldEditor> myFieldEditors = new ArrayList<>();

	public CodeIntelligencePreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Preferences for the Code Intelligence plugin");
	}

	/**
	 * Creates the field editors. Field editors are abstractions of the common GUI
	 * blocks needed to manipulate various types of preferences. Each field editor
	 * knows how to save and restore itself.
	 */
	public void createFieldEditors() {
		Font boldFont = JFaceResources.getResources().create(FontDescriptor.createFrom(getFont()).setStyle(SWT.BOLD));

		Label ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
		Composite cmpTopButtons = new Composite(getFieldEditorParent(), SWT.NONE);
		cmpTopButtons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 3, 1));
		GridLayout cmpTopButtonsLayout = new GridLayout(3, false);
		cmpTopButtonsLayout.marginWidth = 0;
		cmpTopButtonsLayout.marginHeight = 0;
		cmpTopButtons.setLayout(cmpTopButtonsLayout);
		Button btnSetupWizardDialog = new Button(cmpTopButtons, SWT.NONE);
		btnSetupWizardDialog.setText("Setup wizard...");
		btnSetupWizardDialog.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		btnSetupWizardDialog.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			WizardDialog dlg = new WizardDialog(getShell(), new ConnectionSetupWizard());
			dlg.open();

			// Refresh all field editors
			for (FieldEditor editor : myFieldEditors) {
				editor.load();
			}
			// // Special handling for ApiConnectionFieldEditor
			// for (FieldEditor editor : myFieldEditors) {
			// if (editor instanceof ApiConnectionFieldEditor) {
			// ((ApiConnectionFieldEditor) editor).refreshConnectionsList();
			// }
			// }
		}));

		ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
		addField(new ApiConnectionFieldEditor(PreferenceConstants.API_CONNECTION_DATA, "&API connections:",
				getFieldEditorParent(), connections));

		Composite cmpButtons = new Composite(getFieldEditorParent(), SWT.NONE);
		cmpButtons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 3, 1));
		GridLayout cmpButtonsLayout = new GridLayout(1, false);
		cmpButtonsLayout.marginWidth = 0;
		cmpButtonsLayout.marginHeight = 0;
		cmpButtons.setLayout(cmpButtonsLayout);
		Button btnCustomParamsDlog = new Button(cmpButtons, SWT.NONE);
		btnCustomParamsDlog.setText("Custom params...");
		btnCustomParamsDlog.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		btnCustomParamsDlog.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			CustomConfigurationParametersDialog dlg = new CustomConfigurationParametersDialog(
					Display.getCurrent().getActiveShell(), connections);
			dlg.open();
		}));

		ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
		Label lblCompletion = new Label(getFieldEditorParent(), SWT.NONE);
		lblCompletion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		lblCompletion.setText("Completion:");
		lblCompletion.setFont(boldFont);

		CompletionModelFieldEditor completionModelIdFieldEditor = new CompletionModelFieldEditor(
				PreferenceConstants.COMPLETION_MODEL_NAME, "&Model:", getFieldEditorParent());
		addField(completionModelIdFieldEditor);
		completionModelIdFieldEditor.doCheckState();
		addField(new IntegerFieldEditor(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS, "Max. &response tokens:",
				getFieldEditorParent()));
		// Add fields for context lines before and after cursor
		addField(new IntegerFieldEditor(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE,
				"Context lines &before cursor:", getFieldEditorParent()));
		addField(new IntegerFieldEditor(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER,
				"Context lines &after cursor:", getFieldEditorParent()));

		ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
		Label lblChat = new Label(getFieldEditorParent(), SWT.NONE);
		lblChat.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		lblChat.setText("Chat:");
		lblChat.setFont(boldFont);

		ChatModelFieldEditor chatModelIdFieldEditor = new ChatModelFieldEditor(PreferenceConstants.CHAT_MODEL_NAME,
				"M&odel:", getFieldEditorParent());
		addField(chatModelIdFieldEditor);
		chatModelIdFieldEditor.doCheckState();
		addField(new IntegerFieldEditor(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS, "Max. r&esponse tokens:",
				getFieldEditorParent()));

		addField(new IntegerFieldEditor(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT, "Max. &history items:",
				getFieldEditorParent()));

		// Add BooleanFieldEditor for enabling/disabling tools globally in Chat
		addField(new BooleanFieldEditor(PreferenceConstants.CHAT_TOOLS_ENABLED, "Enable &Tools globally in Chat",
				getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceConstants.CHAT_TOOLS_APPLY_DEFERRED_ENABLED,
				"C&ollect tool calls and execute together at the end of a streak", getFieldEditorParent()));

		// Add button to open dialog for managing specific tools
		Composite cmpToolManagementButtons = new Composite(getFieldEditorParent(), SWT.NONE);
		cmpToolManagementButtons.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1));
		GridLayout cmpToolManagementButtonsLayout = new GridLayout(1, false);
		cmpToolManagementButtonsLayout.marginWidth = 0;
		cmpToolManagementButtonsLayout.marginHeight = 0;
		cmpToolManagementButtons.setLayout(cmpToolManagementButtonsLayout);

		Button btnManageTools = new Button(cmpToolManagementButtons, SWT.NONE);
		btnManageTools.setText("Manage Specific Tools...");
		btnManageTools.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		btnManageTools.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			ManageToolsDialog dlg = new ManageToolsDialog(getShell());
			dlg.open();
		}));

		ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));

		addField(new PromptTemplateFieldEditor(PreferenceConstants.PRMPT_TEMPLATES, "&Prompt Templates:",
				getFieldEditorParent(), connections, completionModelIdFieldEditor::getStringValue,
				chatModelIdFieldEditor::getStringValue));

		addField(new BooleanFieldEditor(PreferenceConstants.DEBUG_LOG_PROMPTS, "Log prompts to Error Log",
				getFieldEditorParent()));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {

	}

	/**
	 * Base class for model field editors that provides common functionality
	 * for model selection and validation.
	 */
	private abstract class BaseModelFieldEditor extends StringButtonFieldEditor {
		protected BaseModelFieldEditor(String name, String labelText, Composite parent) {
			super(name, labelText, parent);
		}

		@Override
		protected String changePressed() {
			List<AiModel> models = loadAvailableModels();
			ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
			String res = null;
			if (dialog.open() == ModelSelectionDialog.OK) {
				AiModel model = dialog.getSelectedModel();
				res = model.getApiConnection().getName() + "/" + model.getId();
			}
			return res;
		}

		@Override
		protected boolean doCheckState() {
			String value = getStringValue();
			if (value == null || value.trim().isEmpty()) {
				setErrorMessage("Model must be specified");
				return false;
			}

			// Parse the value format: "connectionName/modelId"
			String[] parts = value.split("/", 2);
			if (parts.length != 2) {
				setErrorMessage("Invalid model format. Expected: connectionName/modelId");
				return false;
			}

			String connectionName = StringUtils.trim(parts[0]);
			String modelId = StringUtils.trim(parts[1]);

			// Check if the connection exists and is enabled
			AiApiConnection targetConnection = findConnectionByName(connectionName);

			if (targetConnection == null) {
				setErrorMessage("Connection '" + connectionName + "' not found");
				return false;
			}

			if (!targetConnection.isEnabled()) {
				setErrorMessage("Connection '" + connectionName + "' is disabled");
				return false;
			}

			// Check if the model exists in the connection
			try {
				List<AiModel> models = targetConnection.getApiClient().getModels();
				boolean modelExists = models.stream()
						.anyMatch(m -> m.getId().equals(modelId));
				
				if (!modelExists) {
					setErrorMessage("Model '" + modelId + "' not found in connection '" + connectionName + "'");
					return false;
				}
			} catch (RuntimeException e) {
				setErrorMessage("Error validating model: " + e.getMessage());
				Activator.logError("Error validating model for connection " + connectionName + ": " + e.getMessage(), e);
				return false;
			}

			// Validation passed
			return true;
		}

		/**
		 * Loads all available models from enabled connections.
		 */
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

		/**
		 * Finds a connection by its name.
		 */
		private AiApiConnection findConnectionByName(String connectionName) {
			for (AiApiConnection conn : connections) {
				if (StringUtils.equals(StringUtils.stripToEmpty(conn.getName()),
						StringUtils.stripToEmpty(connectionName))) {
					return conn;
				}
			}
			return null;
		}
	}

	private final class CompletionModelFieldEditor extends BaseModelFieldEditor {
		private CompletionModelFieldEditor(String name, String labelText, Composite parent) {
			super(name, labelText, parent);
		}
	}

	private final class ChatModelFieldEditor extends BaseModelFieldEditor {
		private ChatModelFieldEditor(String name, String labelText, Composite parent) {
			super(name, labelText, parent);
		}
	}

	@Override
	public void addField(FieldEditor editor) {
		super.addField(editor);
		myFieldEditors.add(editor);
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();
		Activator.getDefault().triggerConfigChangeNotification();
		return ok;
	}
}
