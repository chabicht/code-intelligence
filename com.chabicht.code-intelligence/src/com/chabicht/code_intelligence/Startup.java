package com.chabicht.code_intelligence;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;

import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.chabicht.codeintelligence.preferences.setupwizard.ConnectionSetupWizard;

public class Startup implements IStartup {

	@Override
	public void earlyStartup() {
		Display.getDefault().syncExec(() -> {
			boolean wizardShown = Activator.getDefault().getPreferenceStore()
					.getBoolean(PreferenceConstants.CONNECTION_SETUP_WIZARD_PROPOSED);
//		if (!wizardShown && this.loadApiConnections().isEmpty()) {
			Shell shell = Display.getDefault().getActiveShell();
			if (MessageDialog.openQuestion(shell, "Code Intelligence: launch setup wizard?", """
					Howdie! It looks like you just installed the Code Intelligence extension.
					Do you want to launch the setup wizard?
					""")) {
				WizardDialog dlg = new WizardDialog(shell, new ConnectionSetupWizard());
				dlg.open();
			}
//		}
		});
	}

}
