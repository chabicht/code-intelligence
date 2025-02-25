package com.chabicht.code_intelligence.chat;

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.part.IPageBookViewPage;

import com.chabicht.code_intelligence.Tuple;

public class ConsolePageParticipant implements IConsolePageParticipant {

	private Map<ConsolePageParticipant, Tuple<IPageBookViewPage, IConsole>> instances = new IdentityHashMap<>();

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(IPageBookViewPage page, IConsole console) {
		instances.put(this, Tuple.of(page, console));
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
}
