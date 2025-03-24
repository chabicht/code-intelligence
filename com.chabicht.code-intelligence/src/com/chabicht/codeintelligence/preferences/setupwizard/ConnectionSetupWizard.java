package com.chabicht.codeintelligence.preferences.setupwizard;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.Wizard;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.code_intelligence.apiclient.AiApiConnection.ApiType;
import com.chabicht.code_intelligence.model.ProviderDefaults;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

public class ConnectionSetupWizard extends Wizard {

	private AiApiConnection connection = new AiApiConnection();
	private String completionModelId;
	private String chatModelId;
	private ProviderDefaults selectedProviderDefaults;

	public ConnectionSetupWizard() {
		connection.setEnabled(true);

		setWindowTitle("Code Intelligence Setup Wizard");
		addPage(new ConnectionSelectionPage("Select Provider", connection));
		addPage(new CompletionModelSelectionPage("Select completion model", this, connection));
		addPage(new ChatModelSelectionPage("Select chat model", this, connection));
	}

	@Override
	public boolean canFinish() {
		boolean connectionOk = connection.getType() != null && StringUtils.isNotBlank(connection.getBaseUri())
				&& (StringUtils.isNotBlank(connection.getApiKey()) || ApiType.OLLAMA.equals(connection.getType()));
		boolean completionModelSelected = StringUtils.isNotBlank(completionModelId);
		boolean chatModelSelected = StringUtils.isNotBlank(chatModelId);
		return connectionOk && completionModelSelected && chatModelSelected;
	}

	@Override
	public boolean performFinish() {
		List<AiApiConnection> apiConnections = new ArrayList<>(Activator.getDefault().loadApiConnections());
		updateOrAddConnection(apiConnections);
		Activator.getDefault().saveApiConnections(apiConnections);
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		prefs.setValue(PreferenceConstants.COMPLETION_MODEL_NAME, StringUtils.strip(completionModelId));
		prefs.setValue(PreferenceConstants.CHAT_MODEL_NAME, StringUtils.strip(chatModelId));

		return true;
	}

	private void updateOrAddConnection(List<AiApiConnection> apiConnections) {
		boolean found = false;
		for (int i = 0; i < apiConnections.size(); i++) {
			AiApiConnection existingConnection = apiConnections.get(i);
			if (existingConnection.getName().equals(connection.getName())) {
				apiConnections.set(i, connection);
				found = true;
				break;
			}
		}

		if (!found) {
			apiConnections.add(connection); // Add the new connection if no existing connection with the same name was
											// found.
		}
	}

	public String getCompletionModelId() {
		return completionModelId;
	}

	public void setCompletionModelId(String completionModelId) {
		this.completionModelId = completionModelId;
	}

	public String getChatModelId() {
		return chatModelId;
	}

	public void setChatModelId(String chatModelId) {
		this.chatModelId = chatModelId;

	}

	public ProviderDefaults getSelectedProviderDefaults() {
		return selectedProviderDefaults;
	}

	public void setSelectedProviderDefaults(ProviderDefaults defaults) {
		this.selectedProviderDefaults = defaults;
	}
}
