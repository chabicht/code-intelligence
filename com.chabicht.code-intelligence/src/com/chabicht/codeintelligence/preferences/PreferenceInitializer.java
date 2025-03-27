package com.chabicht.codeintelligence.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.chabicht.code_intelligence.Activator;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#
	 * initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.COMPLETION_MAX_RESPONSE_TOKENS, 1024);
		store.setDefault(PreferenceConstants.COMPLETION_CONTEXT_LINES_BEFORE, 50);
		store.setDefault(PreferenceConstants.COMPLETION_CONTEXT_LINES_AFTER, 10);
		store.setDefault(PreferenceConstants.CHAT_MAX_RESPONSE_TOKENS, 8192);
		store.setDefault(PreferenceConstants.CHAT_HISTORY_SIZE_LIMIT, 50);
	}

}
