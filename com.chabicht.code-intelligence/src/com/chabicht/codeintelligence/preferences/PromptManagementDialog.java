package com.chabicht.codeintelligence.preferences;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.core.databinding.AggregateValidationStatus;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.code_intelligence.chat.ChatComponent;
import com.chabicht.code_intelligence.model.ChatConversation;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.Role;
import com.chabicht.code_intelligence.model.DefaultPrompts;
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.code_intelligence.util.MarkdownUtil;
import com.google.gson.JsonSyntaxException;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class PromptManagementDialog extends Dialog {
	private DataBindingContext m_bindingContext;
	private final ScheduledExecutorService scheduler;

	private static final String UNASSIGNED = "<Unassigned>";
	private Text txtPresetName;
	private Text txtModel;
	private Font textEditorFont;
	private PromptTemplate prompt;

	private static final Map<String, Object> COMPLETION_DEMO_DATA = consCompletionDemoData();
	private Composite preview;
	private StyledText stPrompt;
	private ComboViewer cvType;
	private ComboViewer cvConnection;
	private Button btnEnabled;
	private Composite cmpSashRight;

	private Parser markdownParser = MarkdownUtil.createParser();
	private HtmlRenderer markdownRenderer = MarkdownUtil.createRenderer();

	private WritableList<PromptType> promptTypesList = new WritableList<>();
	private WritableList<AiApiConnection> connectionsList = new WritableList<>();

	private final List<String> validModelNames = new ArrayList<>();

	private IObservableValue<IStatus> validationStatus;

	protected PromptManagementDialog(Shell parentShell, List<AiApiConnection> connections, PromptTemplate prompt) {
		super(parentShell);
		this.prompt = prompt;

		this.scheduler = Executors.newScheduledThreadPool(1);

		ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		textEditorFont = theme.getFontRegistry().get("org.eclipse.jface.textfont");
		AiApiConnection dummy = new AiApiConnection();
		dummy.setName(UNASSIGNED);
		this.connectionsList.add(dummy);
		connections.stream().filter(AiApiConnection::isEnabled).forEach(connectionsList::add);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		// create a composite with standard margins and spacing
		GridLayout layout = new GridLayout();
		layout.numColumns = 9;
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		applyDialogFont(composite);

		Label lblName = new Label(composite, SWT.NONE);
		lblName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblName.setText("Name:");

		txtPresetName = new Text(composite, SWT.BORDER);
		txtPresetName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Label lblType = new Label(composite, SWT.NONE);
		lblType.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblType.setText("Type:");

		cvType = new ComboViewer(composite, SWT.NONE);
		Combo cbType = cvType.getCombo();
		GridData gd_cbType = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_cbType.widthHint = 75;
		cbType.setLayoutData(gd_cbType);
		cvType.setContentProvider(new ObservableListContentProvider<PromptType>());
		cvType.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((PromptType) element).getLabel();
			}
		});
		cvType.setInput(this.promptTypesList);

		Label lblConnection = new Label(composite, SWT.NONE);
		lblConnection.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblConnection.setText("Connection:");

		cvConnection = new ComboViewer(composite, SWT.NONE);
		Combo cboConnection = cvConnection.getCombo();
		cboConnection.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		cvConnection.setContentProvider(new ObservableListContentProvider<AiApiConnection>());
		cvConnection.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((AiApiConnection) element).getName();
			}
		});
		cvConnection.setInput(connectionsList);

		Label lblModel = new Label(composite, SWT.NONE);
		lblModel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblModel.setText("Model:");

		txtModel = new Text(composite, SWT.BORDER);
		txtModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		Button btnChoose = new Button(composite, SWT.NONE);
		btnChoose.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				List<AiModel> modelsFromConn = connectionForName(prompt.getConnectionName()).getApiClient().getModels();
				ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), modelsFromConn);
				if (dialog.open() == ModelSelectionDialog.OK) {
					AiModel model = dialog.getSelectedModel();
					prompt.setModelId(model.getId());
				}
			}
		});
		btnChoose.setText("Choose...");

		SashForm sashForm = new SashForm(composite, SWT.NONE);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 9, 1));

		Composite cmpSashLeft = new Composite(sashForm, SWT.NONE);
		GridLayout gl_cmpSashLeft = new GridLayout(1, false);
		gl_cmpSashLeft.marginHeight = 0;
		gl_cmpSashLeft.marginWidth = 0;
		gl_cmpSashLeft.horizontalSpacing = 0;
		cmpSashLeft.setLayout(gl_cmpSashLeft);

		TextViewer tvPrompt = new TextViewer(cmpSashLeft, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		stPrompt = tvPrompt.getTextWidget();
		stPrompt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		stPrompt.setFont(textEditorFont);

		Button btnResetPrompt = new Button(cmpSashLeft, SWT.NONE);
		btnResetPrompt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				resetPrompt();
			}
		});
		btnResetPrompt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		btnResetPrompt.setText("Reset to default");

		cmpSashRight = new Composite(sashForm, SWT.NONE);
		GridLayout gl_cmpSashRight = new GridLayout(1, false);
		gl_cmpSashRight.marginWidth = 0;
		gl_cmpSashRight.marginHeight = 0;
		gl_cmpSashRight.horizontalSpacing = 0;
		cmpSashRight.setLayout(gl_cmpSashRight);

		createPreview();
		sashForm.setWeights(new int[] { 1, 1 });
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);
		new Label(composite, SWT.NONE);

		Composite composite_1 = new Composite(composite, SWT.NONE);
		RowLayout rl_composite_1 = new RowLayout(SWT.HORIZONTAL);
		rl_composite_1.marginTop = 0;
		rl_composite_1.marginRight = 0;
		rl_composite_1.marginLeft = 0;
		rl_composite_1.marginBottom = 0;
		composite_1.setLayout(rl_composite_1);
		composite_1.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));

		btnEnabled = new Button(composite_1, SWT.CHECK);
		btnEnabled.setText("enabled");

		btnUseByDefault = new Button(composite_1, SWT.CHECK);
		btnUseByDefault.setText("Use by default");

		init();
		m_bindingContext = initDataBindings();
		initErrorHandling();
		initListeners();

		parent.addDisposeListener(e -> disposeListeners());

		return composite;
	}

	private void createPreview() {
		if (preview != null) {
			preview.dispose();
			preview = null;
		}
		if (PromptType.INSTRUCT.equals(prompt.getType())) {
			preview = new InstructPreview(cmpSashRight, SWT.BORDER);
			updatePromptPreview();
		} else {
			preview = new ChatComponent(cmpSashRight, SWT.BORDER);
			ChatComponent cc = (ChatComponent) preview;
			ProgressAdapter listener = new ProgressAdapter() {
				@Override
				public void completed(ProgressEvent event) {
					updatePromptPreview();
					cc.removeProgressListener(this);
				}
			};
			cc.addProgressListener(listener);
		}
		preview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		cmpSashRight.layout();
	}

	private void init() {
		promptTypesList.add(PromptType.INSTRUCT);
		promptTypesList.add(PromptType.CHAT);

		updatePromptPreview();
	}

	private PropertyChangeListener promptTypeListener = e -> {
		createPreview();
		chatConversation = null;
		resetPrompt();
	};

	private PropertyChangeListener promptChangeListener = e -> {
		updatePromptPreview();
	};

	private void initListeners() {
		prompt.addPropertyChangeListener("prompt", promptChangeListener);
		prompt.addPropertyChangeListener("type", promptTypeListener);
	}

	private void disposeListeners() {
		prompt.removePropertyChangeListener("prompt", promptChangeListener);
		prompt.removePropertyChangeListener("type", promptTypeListener);
		scheduler.shutdown();
	}

	ChatConversation chatConversation;
	private Button btnUseByDefault;

	private void updatePromptPreview() {
		if (PromptType.INSTRUCT.equals(prompt.getType())) {
			if (preview instanceof InstructPreview instructPreview) {
				Template tmpl = Mustache.compiler().escapeHTML(false)
						.compile(StringUtils.stripToEmpty(prompt.getPrompt()));
				String markdown = tmpl.execute(COMPLETION_DEMO_DATA);
				String html = markdownRenderer.render(markdownParser.parse(markdown));
				instructPreview.setText(html);
			}
		} else if (PromptType.CHAT.equals(prompt.getType())) {
			if (preview instanceof ChatComponent chat && chat.isReady()) {
				try {
					if (chatConversation == null) {
						chatConversation = new ChatConversation();
						chatConversation.addMessage(new ChatMessage(Role.SYSTEM,
								"<details open><summary>System Prompt</summary>" + prompt.getPrompt() + "</details>"));
						chatConversation.addMessage(new ChatMessage(Role.USER, "test"));
						chatConversation.addMessage(new ChatMessage(Role.ASSISTANT,
								"It seems like you're testing or checking something."
										+ " Could you clarify what you need help with?"
										+ " Whether it's a specific question, a problem to solve,"
										+ " or just exploring, I'm here to assist!"));
					} else {
						chatConversation.getMessages().get(0).setContent(prompt.getPrompt());
					}

					for (ChatMessage msg : chatConversation.getMessages()) {
						String html = markdownRenderer.render(markdownParser.parse(msg.getContent()));
						String role = msg.getRole().name().toLowerCase();
						if (chat.isMessageKnown(msg.getId())) {
							chat.updateMessage(msg.getId(), html);
						} else {
							chat.addMessage(msg.getId(), role, html);
						}
					}
				} catch (JsonSyntaxException e) {
					// Notify user
				}
			}
		}
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Point getInitialSize() {
		Rectangle bounds = Display.getCurrent().getBounds();
		return new Point(bounds.width - 300, bounds.height - 200);
//		return new Point(1200, 800);
	}

	private static HashMap<String, Object> consCompletionDemoData() {
		HashMap<String, Object> res = new HashMap<>();
		res.put("recentEdits", List.of("```\nsystemPrompts.add(createNoTemplateSelection());\n```", //
				"""
						```
						PromptTemplate res = new PromptTemplate();
						res.setEnabled(true);
						res.setUseByDefault(true);
						res.setName("<None>");
						return res;
						```
						""", //
				"```\nres.setEnabled(true);\n```"));
		res.put("code", """
				List<Integer> numbers = List.of(1, 2, 3, 4, 5);
				int evenSum = numbers.stream()
				    .filter(n -> n % 2 == 0)
				    <<<cursor>>>
				""");
		return res;
	}

	private final class ApiConnectionToStringConverter extends Converter<AiApiConnection, String> {
		private ApiConnectionToStringConverter() {
			super(AiApiConnection.class, String.class);
		}

		@Override
		public String convert(AiApiConnection fromObject) {
			if (UNASSIGNED.equals(fromObject.getName())) {
				prompt.setModelId(null);
				validModelNames.clear();
				return null;
			}

			List<String> modelNames = fromObject.getApiClient().getModels().stream().map(m -> m.getId())
					.collect(Collectors.toList());
			validModelNames.clear();
			validModelNames.addAll(modelNames);

			return fromObject == null ? "Default" : fromObject.getName();
		}
	}

	private final class StringToApiConnectionConverter extends Converter<String, AiApiConnection> {
		private StringToApiConnectionConverter() {
			super(String.class, AiApiConnection.class);
		}

		@Override
		public AiApiConnection convert(String fromObject) {
			String connectionName = (String) fromObject;
			if (StringUtils.isBlank(connectionName)) {
				connectionName = UNASSIGNED;
			}
			return connectionForName(connectionName);
		}
	}

	private void initErrorHandling() {
		m_bindingContext.getBindings().forEach(binding -> ControlDecorationSupport.create(binding, SWT.LEFT | SWT.TOP));
		validationStatus = new AggregateValidationStatus(m_bindingContext, AggregateValidationStatus.MAX_SEVERITY);
		validationStatus.addValueChangeListener(e -> {
			getButton(IDialogConstants.OK_ID).setEnabled(e.diff.getNewValue().isOK());
		});
	}

	private AiApiConnection connectionForName(String name) {
		return connectionsList.stream().filter(conn -> conn.getName().equals(name)).findFirst().orElse(null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		IObservableValue observeObserveStringWidget = WidgetProperties.text(SWT.Modify).observe(txtPresetName);
		IObservableValue stringValue = BeanProperties.value("name").observe(prompt);
		bindingContext.bindValue(observeObserveStringWidget, stringValue,
				new UpdateValueStrategy<String, String>().setAfterGetValidator(STRING_NOT_EMPTY_VALIDATOR), null);
		//
		IObservableValue observeObservePromptStringWidget = WidgetProperties.text(SWT.Modify).observe(stPrompt);
		IObservableValue promptStringValue = BeanProperties.value("prompt").observe(prompt);
		bindingContext.bindValue(observeObservePromptStringWidget, promptStringValue, null, null);
		//
		IObservableValue observeSelectionBtnEnabledObserveWidget = WidgetProperties.widgetSelection()
				.observe(btnEnabled);
		IObservableValue enabledPromptObserveValue = BeanProperties.value("enabled").observe(prompt);
		bindingContext.bindValue(observeSelectionBtnEnabledObserveWidget, enabledPromptObserveValue, null, null);
		//
		IObservableValue observeSingleSelectionCvType = ViewerProperties.singleSelection().observe(cvType);
		IObservableValue typePromptObserveValue = BeanProperties.value("type").observe(prompt);
		bindingContext.bindValue(observeSingleSelectionCvType, typePromptObserveValue, null, null);
		//
		IObservableValue observeSingleSelectionCvConnection = ViewerProperties.singleSelection().observe(cvConnection);
		IObservableValue connectionNamePromptObserveValue = BeanProperties.value("connectionName").observe(prompt);
		UpdateValueStrategy strategy = new UpdateValueStrategy();
		strategy.setConverter(new ApiConnectionToStringConverter());
		UpdateValueStrategy strategy_1 = new UpdateValueStrategy();
		strategy_1.setConverter(new StringToApiConnectionConverter());
		bindingContext.bindValue(observeSingleSelectionCvConnection, connectionNamePromptObserveValue, strategy,
				strategy_1);
		//
		IObservableValue observeTextTxtModelObserveWidget = WidgetProperties.text(SWT.FocusOut).observe(txtModel);
		IObservableValue modelIdPromptObserveValue = BeanProperties.value("modelId").observe(prompt);
		bindingContext.bindValue(observeTextTxtModelObserveWidget, modelIdPromptObserveValue, null, null);
		//
		IObservableValue observeSelectionBtnUseByDefaultObserveWidget = WidgetProperties.widgetSelection()
				.observe(btnUseByDefault);
		IObservableValue useByDefaultPromptObserveValue = BeanProperties.value("useByDefault").observe(prompt);
		bindingContext.bindValue(observeSelectionBtnUseByDefaultObserveWidget, useByDefaultPromptObserveValue, null,
				null);
		//
		return bindingContext;
	}

	@Override
	protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
		Button button = super.createButton(parent, id, label, defaultButton);
		if (IDialogConstants.OK_ID == id) {
			button.setEnabled(validationStatus.getValue().isOK());
		}
		return button;
	}

	private void resetPrompt() {
		switch (prompt.getType()) {
		case CHAT:
			prompt.setPrompt(DefaultPrompts.CHAT_SYSTEM_PROMPT);
			break;
		default:
			prompt.setPrompt(DefaultPrompts.INSTRUCT_PROMPT);
			break;
		}
	}

	private final static IValidator<String> STRING_NOT_EMPTY_VALIDATOR = new IValidator<>() {
		@Override
		public IStatus validate(String value) {
			if (StringUtils.isBlank(value)) {
				return Status.error("String must not be blank.");
			}
			return Status.OK_STATUS;
		}
	};
}
