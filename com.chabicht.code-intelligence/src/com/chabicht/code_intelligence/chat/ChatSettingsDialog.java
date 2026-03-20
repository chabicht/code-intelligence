package com.chabicht.code_intelligence.chat;

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
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningControlMode;
import com.chabicht.code_intelligence.chat.ChatSettings.ReasoningEffort;
import com.chabicht.code_intelligence.chat.tools.ToolProfile;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.codeintelligence.preferences.ModelSelectionDialog;

public class ChatSettingsDialog extends Dialog {
	public static final PromptTemplate NONE = createNoTemplateSelection();

	private DataBindingContext m_bindingContext;
	private final ChatSettings settings;

	private final WritableList<PromptTemplate> systemPrompts = new WritableList<>();
	private Composite dialogAreaComposite;
	private Group grpReasoning;
	private ComboViewer cvSystemPrompt;
	private Text txtModel;
	private Label lblReasoningBudgetTokens;
	private Text txtReasoningBudgetTokens;
	private Label lblReasoningEffort;
	private Label lblReasoningTogglePlaceholder;
	private ComboViewer cvReasoningEffort;
	private Label lblReasoningHint;
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
		dialogAreaComposite = new Composite(parent, SWT.NONE);
		dialogAreaComposite.setLayout(layout);
		dialogAreaComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		applyDialogFont(dialogAreaComposite);

		Label lblModel = new Label(dialogAreaComposite, SWT.NONE);
		lblModel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblModel.setText("Model:");

		txtModel = new Text(dialogAreaComposite, SWT.BORDER);
		txtModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Button btnChange = new Button(dialogAreaComposite, SWT.NONE);
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

		Label lblChatCompletionMaxTokens = new Label(dialogAreaComposite, SWT.NONE);
		lblChatCompletionMaxTokens.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblChatCompletionMaxTokens.setText("Max. response tokens:");

		txtChatCompletionMaxTokens = new Text(dialogAreaComposite, SWT.BORDER);
		txtChatCompletionMaxTokens.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		Label lblSystemPrompt = new Label(dialogAreaComposite, SWT.NONE);
		lblSystemPrompt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSystemPrompt.setText("System prompt:");

		cvSystemPrompt = new ComboViewer(dialogAreaComposite, SWT.NONE);
		Combo cbSystemPrompt = cvSystemPrompt.getCombo();
		cbSystemPrompt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		grpReasoning = new Group(dialogAreaComposite, SWT.NONE);
		grpReasoning.setLayout(new GridLayout(2, false));
		GridData gd_grpReasoning = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
		gd_grpReasoning.widthHint = 75;
		grpReasoning.setLayoutData(gd_grpReasoning);
		grpReasoning.setText("Reasoning");

		btnReasoningEnabled = new Button(grpReasoning, SWT.CHECK);
		btnReasoningEnabled.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		btnReasoningEnabled.setText("enabled");
		lblReasoningTogglePlaceholder = new Label(grpReasoning, SWT.NONE);
		lblReasoningTogglePlaceholder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		lblReasoningBudgetTokens = new Label(grpReasoning, SWT.NONE);
		lblReasoningBudgetTokens.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblReasoningBudgetTokens.setText("Budget tokens:");

		txtReasoningBudgetTokens = new Text(grpReasoning, SWT.BORDER);
		txtReasoningBudgetTokens.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		lblReasoningEffort = new Label(grpReasoning, SWT.NONE);
		lblReasoningEffort.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblReasoningEffort.setText("Effort:");

		cvReasoningEffort = new ComboViewer(grpReasoning, SWT.READ_ONLY);
		cvReasoningEffort.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		cvReasoningEffort.setContentProvider(ArrayContentProvider.getInstance());
		cvReasoningEffort.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ReasoningEffort) element).getDisplayName();
			}
		});
		cvReasoningEffort.setInput(ReasoningEffort.values());

		lblReasoningHint = new Label(grpReasoning, SWT.WRAP);
		GridData gdReasoningHint = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		gdReasoningHint.widthHint = 320;
		lblReasoningHint.setLayoutData(gdReasoningHint);
		lblReasoningHint.setText(
				"Model default omits the parameter. None sends reasoning=none. Explicit effort support depends on the selected provider and model.");

		// Tool Configuration Group
		Group grpTools = new Group(dialogAreaComposite, SWT.NONE);
		grpTools.setLayout(new GridLayout(2, false)); // Changed to 2 columns for profile combo
		GridData gd_grpTools = new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1);
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

		return dialogAreaComposite;
	}

	@Override
	public void create() {
		super.create();
		Shell shell = getShell();
		if (shell != null && !shell.isDisposed()) {
			shell.getDisplay().asyncExec(() -> {
				if (shell.isDisposed()) {
					return;
				}
				relayoutDialog();
			});
		}
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
		settings.addPropertyChangeListener("reasoningEnabled", e -> updateReasoningEnablement(settings.getModel()));
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
		btnReasoningEnabled.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateReasoningEnablement(settings.getModel());
			}
		});
	}

	private void updateReasoningEnablement(String modelId) {
		Display.getDefault().syncExec(() -> {
			ReasoningControlMode reasoningMode = ChatSettings.getReasoningControlMode(modelId);
			boolean supportsReasoning = reasoningMode != ReasoningControlMode.NONE;
			boolean reasoningInputsEnabled = reasoningMode == ReasoningControlMode.EFFORT
					? supportsReasoning
					: supportsReasoning && settings.isReasoningEnabled();
			updateReasoningControl(btnReasoningEnabled, null, reasoningMode == ReasoningControlMode.TOKEN_BUDGET,
					supportsReasoning);
			toggleVisibility(lblReasoningTogglePlaceholder, reasoningMode == ReasoningControlMode.TOKEN_BUDGET);
			updateReasoningControl(lblReasoningBudgetTokens, txtReasoningBudgetTokens,
					reasoningMode == ReasoningControlMode.TOKEN_BUDGET, reasoningInputsEnabled);
			updateReasoningControl(lblReasoningEffort, cvReasoningEffort.getCombo(),
					reasoningMode == ReasoningControlMode.EFFORT, reasoningInputsEnabled);
			updateReasoningHint(reasoningMode == ReasoningControlMode.EFFORT);
			relayoutDialog();
		});
	}

	private void relayoutDialog() {
		if (grpReasoning != null && !grpReasoning.isDisposed()) {
			grpReasoning.layout(true, true);
		}
		if (dialogAreaComposite != null && !dialogAreaComposite.isDisposed()) {
			dialogAreaComposite.layout(true, true);
		}
		Shell shell = getShell();
		if (shell != null && !shell.isDisposed()) {
			shell.layout(true, true);
			if (shell.isVisible()) {
				Point currentSize = shell.getSize();
				Point preferredSize = shell.computeSize(currentSize.x, SWT.DEFAULT, true);
				shell.setSize(currentSize.x, preferredSize.y);
			}
		}
	}

	private void updateReasoningControl(Control label, Control input, boolean visible, boolean enabled) {
		toggleVisibility(label, visible);
		if (label instanceof Button button) {
			button.setEnabled(visible && enabled);
		}
		if (input != null) {
			toggleVisibility(input, visible);
			input.setEnabled(visible && enabled);
		}
	}

	private void updateReasoningHint(boolean visible) {
		toggleVisibility(lblReasoningHint, visible);
	}

	private void toggleVisibility(Control control, boolean visible) {
		control.setVisible(visible);
		if (control.getLayoutData() instanceof GridData gridData) {
			gridData.exclude = !visible;
		}
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
		IObservableValue observeReasoningEffortSelection = ViewerProperties.singleSelection().observe(cvReasoningEffort);
		IObservableValue reasoningEffortSettingsObserveValue = BeanProperties.value("reasoningEffort").observe(settings);
		bindingContext.bindValue(observeReasoningEffortSelection, reasoningEffortSettingsObserveValue, null, null);

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
