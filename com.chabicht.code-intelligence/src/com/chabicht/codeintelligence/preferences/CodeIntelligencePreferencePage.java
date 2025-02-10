package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;

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

		Group grpCompletion = new Group(getFieldEditorParent(), SWT.NONE);
		grpCompletion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		FillLayout grpCompletionLayout = new FillLayout();
		grpCompletionLayout.marginHeight = 5;
		grpCompletionLayout.marginWidth = 5;
		grpCompletion.setLayout(grpCompletionLayout);
		grpCompletion.setText("Completion");
		Composite innerCompletion = new Composite(grpCompletion, SWT.NONE);
		innerCompletion.setLayout(new GridLayout());

		addField(new CompletionModelFieldEditor(PreferenceConstants.COMPLETION_MODEL_NAME, "&Model:", innerCompletion));

		Group grpChat = new Group(getFieldEditorParent(), SWT.NONE);
		grpChat.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		FillLayout grpChatLayout = new FillLayout();
		grpChatLayout.marginHeight = 5;
		grpChatLayout.marginWidth = 5;
		grpChat.setLayout(grpChatLayout);
		grpChat.setText("Chat");
		Composite innerChat = new Composite(grpChat, SWT.NONE);
		innerChat.setLayout(new GridLayout());

		addField(new ChatModelFieldEditor(PreferenceConstants.CHAT_MODEL_NAME, "M&odel:", innerChat));

		addField(new BooleanFieldEditor(PreferenceConstants.DEBUG_LOG_PROMPTS, "Log prompts to Error Log",
				getFieldEditorParent()));

		Button btnManagePrompts = new Button(getFieldEditorParent(), SWT.NONE);
		btnManagePrompts.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 3, 1));
		btnManagePrompts.setText("Manage Prompts...");
		btnManagePrompts.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			PromptTemplate pt = new PromptTemplate();
			pt.setType(PromptType.INSTRUCT);
			String currentCompletionModel = getPreferenceStore().getString(PreferenceConstants.COMPLETION_MODEL_NAME);
			if (StringUtils.isNotBlank(currentCompletionModel)) {
				int indexOfSlash = currentCompletionModel.indexOf("/");
				pt.setConnectionName(currentCompletionModel.substring(0, indexOfSlash));
				pt.setModelId(currentCompletionModel.substring(indexOfSlash) + 1);
			}
			pt.setPrompt(COMPLETION_PROMPT_TEMPLATE);
			if (new PromptManagementDialog(getShell(), connections, pt).open() == Dialog.OK) {
				// ...
			}
		}));
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

	private static final String COMPLETION_PROMPT_TEMPLATE =
	// @formatter:off
	"""
	Complete the code beginning at the <<<cursor>>> position.
	A selection may be present, indicated by <<<selection_start>>> and <<<selection_end>>> markers.
	A completion always starts at the <<<cursor>>> marker, but it may span more than one line.

	Example 1:
	```
	public class Main {
	  public static void main(String[] args) {
	    String name = "John";
	    System.ou<<<cursor>>>
	  }
	}
	```
	Completion:
	```
	    System.out.println(name);
	```

	Example 2:
	```
	var model = configuration.getSelectedModel().orElseThrow();

	HttpClient client = HttpClient.newBuilder()
	                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
	                                  .build();

	String requestBody = getRequestBody(prompt, model);
	HttpRequest request = HttpRequest.newBuil<<<cursor>>>
	logger.info("Sending request to ChatGPT.\n\n" + requestBody);

	try
	{
	        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

	        if (response.statusCode() != 200)

	```
	Completion:
	```
	HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
	```


	Example 3:
	```
	var model = configuration.getSelectedModel().orElseThrow();

	HttpClient client = HttpClient.newBuilder()
	                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
	                                  .build();

	String requestBody = getRequestBody(prompt, model);
	HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
	<<<cursor>>>
	logger.info("Sending request to ChatGPT.\n\n" + requestBody);

	try
	{
	        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

	        if (response.statusCode() != 200)

	```
	Completion:
	```
	.timeout( Duration.ofSeconds( configuration.getRequestTimoutSeconds() ) )
	```


	Here is a list of the most recent edits made by the user:
	{{#recentEdits}}
	{{.}}
	
	{{/recentEdits}}

	Important details:
	- This is Java 17 code.
	- Do not repeat the context in your answer.
	- Include the current line until the <<<cursor>>> marker in your answer.
	- Focus on relevant variables and methods from the context provided.
	- If the context before the current line ends with a comment, implement what the comment intends to do.
	- Use the provided last edits by the user to guess what might be an appropriate completion here.
	- Output only the completion snippet (no extra explanations, no markdown, not the whole program again).
	- If the code can be completed logically in 1-5 lines, do so; otherwise, finalize the snippet where it makes sense.
	- It is important to create short completions.

	Now do this for this code:
	```
	{{code}}
	```
	Completion:
	""";
	// @formatter:on
}