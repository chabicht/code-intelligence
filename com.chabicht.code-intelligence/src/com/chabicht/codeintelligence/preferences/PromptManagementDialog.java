package com.chabicht.codeintelligence.preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.core.databinding.AggregateValidationStatus;
import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
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
import org.eclipse.swt.browser.Browser;
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
import com.chabicht.code_intelligence.model.PromptTemplate;
import com.chabicht.code_intelligence.model.PromptType;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

public class PromptManagementDialog extends Dialog {
	private static final String UNASSIGNED = "<Unassigned>";
	private DataBindingContext m_bindingContext;
	private Text txtPresetName;
	private Text txtModel;
	private Font textEditorFont;
	private PromptTemplate prompt;

	private static final Map<String, Object> COMPLETION_DEMO_DATA = consCompletionDemoData();
	private Browser bPreview;
	private StyledText stPrompt;
	private ComboViewer cvType;
	private ComboViewer cvConnection;
	private Button btnEnabled;

	private Parser markdownParser = Parser.builder().build();
	private HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

	private WritableList<PromptType> promptTypesList = new WritableList<>();
	private WritableList<AiApiConnection> connectionsList = new WritableList<>();

	private final List<String> validModelNames = new ArrayList<>();

	private IObservableValue<IStatus> validationStatus;

	protected PromptManagementDialog(Shell parentShell, List<AiApiConnection> connections, PromptTemplate prompt) {
		super(parentShell);
		this.prompt = prompt;

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
				switch (prompt.getType()) {
				case CHAT:
					prompt.setPrompt(DefaultPrompts.CHAT_PROMPT);
					break;
				default:
					prompt.setPrompt(DefaultPrompts.INSTRUCT_PROMPT);
					break;
				}
			}
		});
		btnResetPrompt.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		btnResetPrompt.setText("Reset to default");
		prompt.addPropertyChangeListener("prompt", e -> {
			updatePromptPreview((String) e.getNewValue());
		});

		Composite cmpSashRight = new Composite(sashForm, SWT.NONE);
		GridLayout gl_cmpSashRight = new GridLayout(1, false);
		gl_cmpSashRight.marginWidth = 0;
		gl_cmpSashRight.marginHeight = 0;
		gl_cmpSashRight.horizontalSpacing = 0;
		cmpSashRight.setLayout(gl_cmpSashRight);

		bPreview = new Browser(cmpSashRight, SWT.BORDER);
		bPreview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		sashForm.setWeights(new int[] { 1, 1 });

		btnEnabled = new Button(composite, SWT.CHECK);
		btnEnabled.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 9, 1));
		btnEnabled.setText("enabled");

		init();
		m_bindingContext = initDataBindings();
		initListeners();

		return composite;
	}

	private void init() {
		promptTypesList.add(PromptType.INSTRUCT);
		promptTypesList.add(PromptType.CHAT);

		updatePromptPreview(prompt.getPrompt());
	}

	private void initListeners() {
	}

	private void updatePromptPreview(String prompt) {
		Template tmpl = Mustache.compiler().compile(StringUtils.stripToEmpty(prompt));
		String markdown = tmpl.execute(COMPLETION_DEMO_DATA);
		String html = markdownRenderer.render(markdownParser.parse(markdown));
		int scrollPosition = getPreviewScrollPosition();
		bPreview.setText(html);
		scrollPreviewTo(scrollPosition);
	}

	private int getPreviewScrollPosition() {
		Object result = bPreview.evaluate("return window.pageYOffset;");
		int scrollPosition = 0;
		if (result instanceof Number) {
			scrollPosition = ((Number) result).intValue();
		}
		return scrollPosition;
	}

	private void scrollPreviewTo(int scrollPosition) {
		bPreview.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {
				bPreview.execute("window.scrollTo(0, " + scrollPosition + ");");
				bPreview.removeProgressListener(this);
			}
		});
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Point getInitialSize() {
		Rectangle bounds = Display.getCurrent().getBounds();
		return new Point(bounds.width - 100, bounds.height - 100);
//		return new Point(1200, 800);
	}

	private static HashMap<String, Object> consCompletionDemoData() {
		HashMap<String, Object> res = new HashMap<>();
		res.put("recentEdits", List.of("Edit 1", "Edit 2", "Edit 3"));
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		IObservableValue observeObserveStringWidget = WidgetProperties.text(SWT.Modify).observe(txtPresetName);
		IObservableValue stringValue = BeanProperties.value("name").observe(prompt);
		Binding binding = bindingContext.bindValue(observeObserveStringWidget, stringValue,
				new UpdateValueStrategy<String, String>().setAfterGetValidator(e -> {
					if (StringUtils.isBlank(e)) {
						return Status.error("Name must not be blank.");
					}
					return Status.OK_STATUS;
				}), null);
		ControlDecorationSupport.create(binding, SWT.TOP | SWT.LEFT);
		//
		observeObserveStringWidget = WidgetProperties.text(SWT.Modify).observe(stPrompt);
		stringValue = BeanProperties.value("prompt").observe(prompt);
		bindingContext.bindValue(observeObserveStringWidget, stringValue, null, null);
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
		binding = bindingContext.bindValue(observeSingleSelectionCvConnection, connectionNamePromptObserveValue,
				strategy, strategy_1);
		ControlDecorationSupport.create(binding, SWT.TOP | SWT.LEFT);
		//
		IObservableValue observeTextTxtModelObserveWidget = WidgetProperties.text(SWT.FocusOut).observe(txtModel);
		IObservableValue modelIdPromptObserveValue = BeanProperties.value("modelId").observe(prompt);
		binding = bindingContext.bindValue(observeTextTxtModelObserveWidget, modelIdPromptObserveValue,
				new UpdateValueStrategy<String, String>().setBeforeSetValidator(e -> {
					if (StringUtils.isNotBlank(e)) {
						if (validModelNames.isEmpty()) {
							return Status.error("No API connection selected.");
						}
						boolean present = false;
						for (String validName : validModelNames) {
							if (StringUtils.equals(validName, e)) {
								present = true;
								break;
							}
						}
						if (!present) {
							return Status.error("Model ID is not valid for the selected provider.");
						}
					}
					return Status.OK_STATUS;
				}), null);
		ControlDecorationSupport.create(binding, SWT.TOP | SWT.LEFT);
		//

		validationStatus = new AggregateValidationStatus(bindingContext, AggregateValidationStatus.MAX_SEVERITY);
		validationStatus.addValueChangeListener(e -> {
			IStatus status = e.diff.getNewValue();
			getButton(IDialogConstants.OK_ID).setEnabled(status.isOK());
		});

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

	private AiApiConnection connectionForName(String name) {
		return connectionsList.stream().filter(conn -> conn.getName().equals(name)).findFirst().orElse(null);
	}
}
