package com.chabicht.code_intelligence.chat;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

public class AddSearchResultsToContextAction implements IWorkbenchWindowActionDelegate {

    @Override
    public void run(IAction action) {
        ISelection selection = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService().getSelection();
		AddSelectionToContextUtil.addSelectionToContext(selection);
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {}

    @Override
    public void dispose() {}

    @Override
    public void init(IWorkbenchWindow window) {}
}