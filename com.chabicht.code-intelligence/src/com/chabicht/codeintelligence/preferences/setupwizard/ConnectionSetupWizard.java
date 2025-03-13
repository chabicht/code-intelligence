package com.chabicht.codeintelligence.preferences.setupwizard;

import org.eclipse.jface.wizard.Wizard;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;

public class ConnectionSetupWizard extends Wizard {

	private AiApiConnection connection = new AiApiConnection();

	public ConnectionSetupWizard() {
		connection.setEnabled(true);

		setWindowTitle("Code Intelligence Setup Wizard");
		addPage(new ConnectionSelectionPage("Select Provider", connection));
		addPage(new CompletionModelSelectionPage("Select completion model", connection));
	}

	@Override
	public boolean performFinish() {
		return false;
	}

}
