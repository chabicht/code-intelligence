package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;

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
		addField(new ApiConnectionFieldEditor(PreferenceConstants.API_CONNECTION_DATA, "&API connections:",
				getFieldEditorParent(), connections));

		Font boldFont = JFaceResources.getResources().create(FontDescriptor.createFrom(getFont()).setStyle(SWT.BOLD));

		Label lblCompletion = new Label(getFieldEditorParent(), SWT.NONE);
		lblCompletion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		lblCompletion.setText("Completion:");
		lblCompletion.setFont(boldFont);

		CompletionModelFieldEditor completionFieldEditor = new CompletionModelFieldEditor(
				PreferenceConstants.COMPLETION_MODEL_NAME, "&Model:", getFieldEditorParent());
		addField(completionFieldEditor);

		Label ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));
		Label lblChat = new Label(getFieldEditorParent(), SWT.NONE);
		lblChat.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		lblChat.setText("Chat:");
		lblChat.setFont(boldFont);

		ChatModelFieldEditor chatFieldEditor = new ChatModelFieldEditor(PreferenceConstants.CHAT_MODEL_NAME, "M&odel:",
				getFieldEditorParent());
		addField(chatFieldEditor);

		IntegerFieldEditor integerEditor = new IntegerFieldEditor(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT,
				"Max. &history items:", getFieldEditorParent());
		addField(integerEditor);

		ruler = new Label(getFieldEditorParent(), SWT.SEPARATOR | SWT.HORIZONTAL);
		ruler.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 3, 1));

		addField(new PromptTemplateFieldEditor(PreferenceConstants.PRMPT_TEMPLATES, "&Prompt Templates:",
				getFieldEditorParent(), connections, completionFieldEditor::getStringValue,
				chatFieldEditor::getStringValue));

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

	private final class CompletionModelFieldEditor extends StringButtonFieldEditor {
		private CompletionModelFieldEditor(String name, String labelText, Composite parent) {
			super(name, labelText, parent);
		}

		@Override
		protected String changePressed() {
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
			ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
			String res = null;
			if (dialog.open() == ModelSelectionDialog.OK) {
				AiModel model = dialog.getSelectedModel();
				res = model.getApiConnection().getName() + "/" + model.getId();
			}
			return res;
		}
	}

	private final class ChatModelFieldEditor extends StringButtonFieldEditor {
		private ChatModelFieldEditor(String name, String labelText, Composite parent) {
			super(name, labelText, parent);
		}

		@Override
		protected String changePressed() {
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
			ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
			String res = null;
			if (dialog.open() == ModelSelectionDialog.OK) {
				AiModel model = dialog.getSelectedModel();
				res = model.getApiConnection().getName() + "/" + model.getId();
			}
			return res;
		}
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();
		Activator.getDefault().triggerConfigChangeNotification();
		return ok;
	}
}