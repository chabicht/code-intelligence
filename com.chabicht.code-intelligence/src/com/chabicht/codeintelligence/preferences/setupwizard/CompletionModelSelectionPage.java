package com.chabicht.codeintelligence.preferences.setupwizard;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiModel;
import com.chabicht.codeintelligence.preferences.ModelSelectionDialog;

public class CompletionModelSelectionPage extends WizardPage {
	private AiApiConnection connection;
	private Text txtCompletionModel;
	private Button btnSelect;

	protected CompletionModelSelectionPage(String pageName, AiApiConnection connection) {
		super(pageName);
		this.connection = connection;
		setTitle("Selection completion model");
		setDescription("Now you select the model used for code completion tasks."
				+ "\nIf the connection data is correct, you should see all models the connection you chose on the first page when you click on the \"Select...\" button");
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		container.setLayout(new GridLayout(3, false));

		Label lblCompletionModel = new Label(container, SWT.NONE);
		lblCompletionModel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblCompletionModel.setText("Completion model:");

		txtCompletionModel = new Text(container, SWT.BORDER);
		txtCompletionModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		btnSelect = new Button(container, SWT.NONE);
		btnSelect.setText("Select...");
		initListeners();
	}

	private void initListeners() {
		btnSelect.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			List<AiModel> models = new ArrayList<>();
			if (connection.isEnabled()) {
				try {
					List<AiModel> modelsFromConn = connection.getApiClient().getModels();
					modelsFromConn.sort((m1, m2) -> m1.getName().compareTo(m2.getName()));
					models.addAll(modelsFromConn);
				} catch (RuntimeException ex) {
					Activator.logError(
							"Error loading models for connection " + connection.getName() + ": " + ex.getMessage(),
							ex);
				}
			}
			ModelSelectionDialog dialog = new ModelSelectionDialog(getShell(), models);
			if (dialog.open() == ModelSelectionDialog.OK) {
				AiModel model = dialog.getSelectedModel();
				if (model != null) {
					String modelString = model.getApiConnection().getName() + "/" + model.getId();
					txtCompletionModel.setText(modelString);
				}
			}
		}));
	}

}
