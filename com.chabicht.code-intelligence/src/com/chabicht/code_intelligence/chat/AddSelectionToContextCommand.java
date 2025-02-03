package com.chabicht.code_intelligence.chat;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;

public class AddSelectionToContextCommand implements IHandler {

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		addToContext();
		return null;
	}

	private void addToContext() {
		ITextEditor textEditor = getTextEditor();
		ITextSelection selection = getSelection(textEditor);
		if (selection != null) {
			String selectedText = selection.getText();
			if (StringUtils.isNotBlank(selectedText)) {
				String fileName = textEditor.getEditorInput().getName();
				ChatView.getExternallyaddedcontext().add(
						new MessageContext(fileName, selection.getStartLine(), selection.getEndLine(), selectedText));
			}
		}
	}

	private ITextSelection getSelection(ITextEditor editor) {
		ISelectionProvider selectionService = editor.getSelectionProvider();
		ISelection selection = selectionService.getSelection();
		if (selection instanceof ITextSelection textSelection) {
			return textSelection;
		}

		return null;
	}

	private ITextEditor getTextEditor() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return null;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			return null;
		}
		IEditorPart editor = page.getActiveEditor();
		if (editor == null || !(editor instanceof ITextEditor)) {
			return null;
		}
		ITextEditor textEditor = (ITextEditor) editor;
		return textEditor;
	}

	@Override
	public boolean isEnabled() {
		ITextEditor textEditor = getTextEditor();
		ITextSelection selection = getSelection(textEditor);
		return selection != null && !selection.isEmpty() && StringUtils.isNotBlank(selection.getText());
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {
		// TODO Auto-generated method stub

	}

}
