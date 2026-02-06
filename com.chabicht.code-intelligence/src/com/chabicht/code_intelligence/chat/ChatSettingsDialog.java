package com.chabicht.code_intelligence.chat;

import static com.chabicht.code_intelligence.chat.ChatSettings.supportsReasoning;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.conversion.text.NumberToStringConverter;
import org.eclipse.core.databinding.conversion.text.StringToNumberConverter;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.codeintelligence.preferences.ModelSelectionDialog;

public class ChatSettingsDialog extends Dialog {
	public static final PromptTemplate NONE = createNoTemplateSelection();

	private DataBindingContext m_bindingContext;
	private final ChatSettings settings;

	private final WritableList<PromptTemplate> systemPrompts = new WritableList<>();
	private ComboViewer cvSystemPrompt;
	private Text txtModel;
	private Text txtReasoningBudgetTokens;
	private Button btnReasoningEnabled;
	private Text txtChatCompletionMaxTokens;
	private Button btnToolsEnabled;
	private ComboViewer cvToolProfile;

	protected ChatSettingsDialog(Shell parentShell, ChatSettings settings) {
		super(parentShell);
		ChatSettings clone;
		try {
			clone = (ChatSettings) BeanUtils.cloneBean(settings);
		} catch (IllegalAccessException | InstantiationException | InvocationTargetException
				| NoSuchMethodException e) {
			Activator.logError(e.getMessage(), e);
			clone = new ChatSettings();
		}
		this.settings = clone;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		applyDialogFont(composite);

		Label lblModel = new Label(composite, SWT.NONE);
		lblModel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblModel.setText("Model:");

		txtModel = new Text(composite, SWT.BORDER);
		txtModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Button btnChange = new Button(composite, SWT.NONE);
		btnChange.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				List<AiModel> models = new ArrayList<>();
				List<AiApiConnection> connections = Activator.getDefault().loadApiConnections();
				for (AiApiConnection conn : connections) {
					if (conn.isEnabled()) {
						try {
							List<AiModel> modelsFromConn = conn.getApiClient().getModels();
							modelsFromConn.sort((m1, m2) -> m1.getName().compareTo(m2.getName()));
							models.addAll(modelsFromConn);
						} catch (RuntimeException ex) {
							Activator.logError(
									"Error loading models for connection " + conn.getName() + ": " + ex.getMessage(),
									ex);
						}
					}
				}
				ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
				if (dialog.open() == ModelSelectionDialog.OK) {
					AiModel model = dialog.getSelectedModel();
					String res = model.getApiConnection().getName() + "/" + model.getId();
					settings.setModel(res);
				}
			}
		});
		btnChange.setText("Change...");

		Label lblChatCompletionMaxTokens = new Label(composite, SWT.NONE);
		lblChatCompletionMaxTokens.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblChatCompletionMaxTokens.setText("Max. response tokens:");

		txtChatCompletionMaxTokens = new Text(composite, SWT.BORDER);
		txtChatCompletionMaxTokens.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Label lblSystemPrompt = new Label(composite, SWT.NONE);
		lblSystemPrompt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSystemPrompt.setText("System prompt:");

		cvSystemPrompt = new ComboViewer(composite, SWT.NONE);
		Combo cbSystemPrompt = cvSystemPrompt.getCombo();
		cbSystemPrompt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Group grpReasoning = new Group(composite, SWT.NONE);
		grpReasoning.setLayout(new GridLayout(2, false));
		GridData gd_grpReasoning = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
		gd_grpReasoning.widthHint = 75;
		grpReasoning.setLayoutData(gd_grpReasoning);
		grpReasoning.setText("Reasoning");

		btnReasoningEnabled = new Button(grpReasoning, SWT.CHECK);
		btnReasoningEnabled.setText("enabled");
		new Label(grpReasoning, SWT.NONE);

		Label lblBudgetTokens = new Label(grpReasoning, SWT.NONE);
		lblBudgetTokens.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblBudgetTokens.setText("Budget tokens:");

		txtReasoningBudgetTokens = new Text(grpReasoning, SWT.BORDER);
		txtReasoningBudgetTokens.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		// Tool Configuration Group
		Group grpTools = new Group(composite, SWT.NONE);
		grpTools.setLayout(new GridLayout(2, false)); // Changed to 2 columns for profile combo
		GridData gd_grpTools = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		grpTools.setLayoutData(gd_grpTools);
		grpTools.setText("Tools");

		btnToolsEnabled = new Button(grpTools, SWT.CHECK);
		btnToolsEnabled.setText("Enable Tools Globally");
		btnToolsEnabled.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)); // Span 2 columns

		// Tool Profile selection
		Label lblToolProfile = new Label(grpTools, SWT.NONE);
		lblToolProfile.setText("Tool Set:");
		lblToolProfile.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

		cvToolProfile = new ComboViewer(grpTools, SWT.READ_ONLY);
		cvToolProfile.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		cvToolProfile.setContentProvider(ArrayContentProvider.getInstance());
		cvToolProfile.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ToolProfile) element).getDisplayName();
			}
		});
		cvToolProfile.setInput(ToolProfile.values());

		cvSystemPrompt.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PromptTemplate pt) {
					return pt.getName();
				} else {
					return super.getText(element);
				}
			}
		});
		cvSystemPrompt.setContentProvider(new ObservableListContentProvider<PromptTemplate>());
		cvSystemPrompt.setInput(systemPrompts);

		init();
		m_bindingContext = initDataBindings();

		return composite;
	}

	private void init() {
		List<PromptTemplate> pts = Activator.getDefault().loadPromptTemplates();
		if (pts != null) {
			systemPrompts.clear();
			systemPrompts.add(createNoTemplateSelection());
			pts.stream().filter(pt -> PromptType.CHAT.equals(pt.getType()) && pt.isEnabled())
					.forEach(systemPrompts::add);
		}
		settings.addPropertyChangeListener("model", e -> {
			if (e.getNewValue() instanceof String newString) {
				updateReasoningEnablement(newString);
			}
		});
		updateReasoningEnablement(settings.getModel());

		btnToolsEnabled.setSelection(settings.isToolsEnabled());

		// Initialize tool profile selection
		cvToolProfile.setSelection(new StructuredSelection(settings.getToolProfile()));

		// Enable/disable tool profile combo based on tools enabled checkbox
		cvToolProfile.getCombo().setEnabled(settings.isToolsEnabled());
		btnToolsEnabled.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				cvToolProfile.getCombo().setEnabled(btnToolsEnabled.getSelection());
			}
		});
	}

	private void updateReasoningEnablement(String modelId) {
		Display.getDefault().syncExec(() -> {
			boolean supportsReasoning = supportsReasoning(modelId);
//			if (!supportsReasoning) {
//				btnReasoningEnabled.setSelection(false);
//			}
			btnReasoningEnabled.setEnabled(supportsReasoning);
			txtReasoningBudgetTokens.setEnabled(supportsReasoning);
		});
	}

	private static PromptTemplate createNoTemplateSelection() {
		PromptTemplate res = new PromptTemplate();
		res.setEnabled(true);
		res.setUseByDefault(true);
		res.setName("<None>");
		return res;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		IObservableValue observeSingleSelectionCvSystemPrompt = ViewerProperties.singleSelection()
				.observe(cvSystemPrompt);
		IObservableValue promptTemplateSettingsObserveValue = BeanProperties.value("promptTemplate").observe(settings);
		bindingContext.bindValue(observeSingleSelectionCvSystemPrompt, promptTemplateSettingsObserveValue,
				new UpdateValueStrategy<PromptTemplate, PromptTemplate>()
						.setConverter(new Converter(PromptTemplate.class, PromptTemplate.class) {
							@Override
							public Object convert(Object fromObject) {
								return NONE.equals(fromObject) ? null : fromObject;
							}
						}),
				new UpdateValueStrategy<PromptTemplate, PromptTemplate>()
						.setConverter(new Converter(PromptTemplate.class, PromptTemplate.class) {
							@Override
							public Object convert(Object fromObject) {
								return fromObject == null ? NONE : fromObject;
							}
						}));
		//
		IObservableValue observeTextTxtModelObserveWidget = WidgetProperties.text(SWT.FocusOut).observe(txtModel);
		IObservableValue modelSettingsObserveValue = BeanProperties.value("model").observe(settings);
		bindingContext.bindValue(observeTextTxtModelObserveWidget, modelSettingsObserveValue, null, null);
		//
		IObservableValue observeButtonReasoningEnabledWidget = WidgetProperties.buttonSelection()
				.observe(btnReasoningEnabled);
		IObservableValue reasoningEnabledSettingsObserveValue = BeanProperties.value("reasoningEnabled")
				.observe(settings);
		bindingContext.bindValue(observeButtonReasoningEnabledWidget, reasoningEnabledSettingsObserveValue, null, null);
		//
		IObservableValue observeTextTxtReasoningBudgetTokensObserveWidget = org.eclipse.jface.databinding.swt.typed.WidgetProperties
				.text(org.eclipse.swt.SWT.Modify).observe(txtReasoningBudgetTokens);
		IObservableValue reasoningBudgetTokensSettingsObserveValue = BeanProperties.value("reasoningTokens")
				.observe(settings);
		bindingContext.bindValue(observeTextTxtReasoningBudgetTokensObserveWidget,
				reasoningBudgetTokensSettingsObserveValue,
				new UpdateValueStrategy().setConverter(StringToNumberConverter.toInteger(true)),
				new UpdateValueStrategy().setConverter(NumberToStringConverter.fromInteger(true)));

		IObservableValue observeTextTxtChatCompletionMaxTokensObserveWidget = WidgetProperties.text(SWT.Modify)
				.observe(txtChatCompletionMaxTokens);
		IObservableValue maxResponseTokensSettingsObserveValue = BeanProperties.value("maxResponseTokens")
				.observe(settings);
		bindingContext.bindValue(observeTextTxtChatCompletionMaxTokensObserveWidget,
				maxResponseTokensSettingsObserveValue,
				new UpdateValueStrategy().setConverter(StringToNumberConverter.toInteger(true)),
				new UpdateValueStrategy().setConverter(NumberToStringConverter.fromInteger(true)));

		// Bind btnToolsEnabled to settings.toolsEnabled
		IObservableValue observeToolsEnabledCheckbox = WidgetProperties.buttonSelection().observe(btnToolsEnabled);
		IObservableValue toolsEnabledSettingsObserveValue = BeanProperties.value("toolsEnabled").observe(settings);
		bindingContext.bindValue(observeToolsEnabledCheckbox, toolsEnabledSettingsObserveValue, null, null);

		// Bind cvToolProfile to settings.toolProfile
		IObservableValue observeToolProfileSelection = ViewerProperties.singleSelection().observe(cvToolProfile);
		IObservableValue toolProfileSettingsObserveValue = BeanProperties.value("toolProfile").observe(settings);
		bindingContext.bindValue(observeToolProfileSelection, toolProfileSettingsObserveValue, null, null);

		return bindingContext;
	}

	@Override
	protected Point getInitialSize() {
		Point res = super.getInitialSize();
		res.x = 720;
		return res;
	}

	@Override
	protected void okPressed() {
		super.okPressed();
	}

	public ChatSettings getSettings() {
		return settings;
	}
}