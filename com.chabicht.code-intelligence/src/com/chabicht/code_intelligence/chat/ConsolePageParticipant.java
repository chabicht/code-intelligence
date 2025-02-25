package com.chabicht.code_intelligence.chat;

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.part.IPageBookViewPage;

import com.chabicht.code_intelligence.Tuple;

public class ConsolePageParticipant implements IConsolePageParticipant {

	private Map<ConsolePageParticipant, Tuple<IPageBookViewPage, IConsole>> instances = new IdentityHashMap<>();

	private static String consoleName = "";
	private static Point selectionRange = null;
	private static String selectedText = "";

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(IPageBookViewPage page, IConsole console) {
		instances.put(this, Tuple.of(page, console));

		Object control = page.getControl();
		if (control instanceof StyledText textWidget) {
			textWidget.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					consoleName = "Console Log " + console.getName();
					selectionRange = textWidget.getSelectionRange();
					selectedText = textWidget.getSelectionText();
				}
			});
		}
	}

	@Override
	public void dispose() {
		instances.remove(this);
	}

	@Override
	public void activated() {
	}

	@Override
	public void deactivated() {

	}

	public static String getSelectedText() {
		return selectedText;
	}

	public static String getConsoleName() {
		return consoleName;
	}

	public static Point getSelectionRange() {
		return selectionRange;
	}
}
