package com.chabicht.codeintelligence.preferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.chabicht.code_intelligence.CustomConfigurationParameters;
import com.chabicht.code_intelligence.Tuple;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.model.PromptType;
import com.chabicht.code_intelligence.util.ThemeUtil;

public class CustomConfigurationParametersDialog extends Dialog {
	private List<AiApiConnection> connections;
	private Map<String, Map<String, String>> configMap = new HashMap<>();

	private ComboViewer connectionCombo;
	private ComboViewer promptTypeCombo;
	private StyledText jsonEditor;

	private AiApiConnection selectedConnection;
	private PromptType type = PromptType.INSTRUCT;

	boolean dirty = false;

	public CustomConfigurationParametersDialog(Shell parentShell, List<AiApiConnection> connections) {
		super(parentShell);

		// TODO add other types once supported.
		this.connections = connections.stream()
				.filter(c -> ApiType.OLLAMA.equals(c.getType()) || ApiType.OPENAI.equals(c.getType())
						|| ApiType.OPENAI_RESPONSES.equals(c.getType())
						|| ApiType.ANTHROPIC.equals(c.getType()) || ApiType.GEMINI.equals(c.getType())
						|| ApiType.XAI.equals(c.getType()))
				.collect(Collectors.toList());

		// Load existing configuration
		Map<String, Map<String, String>> loadedConfig = CustomConfigurationParameters.getInstance().getMap();
		if (loadedConfig != null) {
			this.configMap.putAll(loadedConfig);
		}
	}

	public void save() {
		saveCurrentJson();
		CustomConfigurationParameters.getInstance().setMap(configMap);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(5, false));

		// Connection selection
		Label connectionLabel = new Label(composite, SWT.NONE);
		connectionLabel.setText("API Connection:");

		connectionCombo = new ComboViewer(composite, SWT.READ_ONLY | SWT.DROP_DOWN);
		connectionCombo.setContentProvider(ArrayContentProvider.getInstance());
		connectionCombo.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof AiApiConnection) {
					return ((AiApiConnection) element).getName();
				}
				return super.getText(element);
			}
		});
		connectionCombo.setInput(connections);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
				.applyTo(connectionCombo.getControl());

		// Request type selection
		Label promptTypeLabel = new Label(composite, SWT.NONE);
		promptTypeLabel.setText("Type:");
		promptTypeLabel.setToolTipText(
				"The type of API call: Instruct is used for completion and generating captions, Chat for chat conversations.");
		promptTypeLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

		promptTypeCombo = new ComboViewer(composite, SWT.READ_ONLY | SWT.DROP_DOWN);
		promptTypeCombo.setContentProvider(ArrayContentProvider.getInstance());
		promptTypeCombo.setInput(PromptType.values());
		promptTypeCombo.setSelection(new StructuredSelection(PromptType.CHAT));
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
				.applyTo(promptTypeCombo.getControl());

		// Reset button
		Button resetButton = new Button(composite, SWT.PUSH);
		resetButton.setImage(new LocalResourceManager(JFaceResources.getResources())
				.create(ImageDescriptor.createFromFile(this.getClass(),
						String.format("/icons/reset_settings_%s.png", ThemeUtil.isDarkTheme() ? "dark" : "light"))));
		resetButton.setToolTipText("Reset to Default");
		resetButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		// JSON editor
		jsonEditor = new StyledText(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData editorGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		editorGridData.horizontalSpan = 5;
		editorGridData.heightHint = 400;
		jsonEditor.setLayoutData(editorGridData);

		// Set monospace font for the JSON editor
		FontData[] fontData = jsonEditor.getFont().getFontData();
		for (FontData fd : fontData) {
			fd.setName("Courier New");
		}
		Font monoFont = new Font(parent.getDisplay(), fontData);
		jsonEditor.setFont(monoFont);
		jsonEditor.addDisposeListener(e -> monoFont.dispose());

		// Setup listeners
		jsonEditor.addModifyListener(e -> {
			dirty = true;
		});

		connectionCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (!selection.isEmpty()) {
					if (dirty) {
						saveCurrentJson(); // Save current settings
					}
					selectedConnection = (AiApiConnection) selection.getFirstElement();
					updateJsonEditor();
				}
			}
		});

		promptTypeCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (!selection.isEmpty()) {
					if (dirty) {
						saveCurrentJson(); // Save current settings
					}
					type = (PromptType) selection.getFirstElement();
					updateJsonEditor();
				}
			}
		});

		resetButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (selectedConnection == null) {
					return;
				}
				if (type == null) {
					return;
				}

				Map<Tuple<ApiType, PromptType>, String> templates = CustomConfigurationParameters
						.getConnectionparamtemplates();
				Tuple<ApiType, PromptType> tuple = Tuple.of(selectedConnection.getType(), type);
				if (templates.containsKey(tuple)) {
					jsonEditor.setText(templates.get(tuple));
				} else {
					jsonEditor.setText("{}");
				}
			}
		});

		// Initialize with first connection
		if (!connections.isEmpty()) {
			selectedConnection = connections.get(0);
			connectionCombo.setSelection(new StructuredSelection(selectedConnection));
			promptTypeCombo.setSelection(new StructuredSelection(PromptType.CHAT));
		}

		return composite;
	}

	private void updateJsonEditor() {
		if (selectedConnection != null) {
			String key = getConfigKey();
			Map<String, String> map = configMap.get(key);

			String jsonTemplate = null;

			if (map != null) {
				jsonTemplate = map.get(type.name());
			}

			if (jsonTemplate == null) {
				jsonTemplate = "{}";
			}

			jsonEditor.setText(jsonTemplate);
			dirty = false;
		}
	}

	private String getConfigKey() {
		return selectedConnection.getName();
	}

	@Override
	protected void okPressed() {
		save();
		super.okPressed();
	}

	private void saveCurrentJson() {
		if (selectedConnection != null) {
			String key = getConfigKey();
			String jsonContent = jsonEditor.getText().trim();

			if (StringUtils.isNotBlank(jsonContent)) {
				if (!configMap.containsKey(key)) {
					configMap.put(key, new HashMap<String, String>());
				}
				configMap.get(key).put(type.name(), jsonContent);
			} else {
				if (configMap.containsKey(key) && configMap.get(key).containsKey(type.name())) {
					configMap.get(key).remove(type.name());
				}
			}
		}
	}

	@Override
	protected Point getInitialSize() {
		return new Point(800, 600);
	}

	@Override
	protected boolean isResizable() {
		return true;
	}
}
