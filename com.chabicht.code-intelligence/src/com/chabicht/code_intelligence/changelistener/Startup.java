package com.chabicht.code_intelligence.changelistener;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Registers listeners for changes in the workspace.
 */
public class Startup implements IStartup {

	IPageListener pageListener = createPageListener();
	IPartListener2 partListener = createIPartListener2();

	@Override
	public void earlyStartup() {
		IWorkbench wb = PlatformUI.getWorkbench();
		wb.addWindowListener(createWindowListener());
		for (IWorkbenchWindow window : wb.getWorkbenchWindows()) {
			if (window.getPages() != null) {
				for (IWorkbenchPage page : window.getPages()) {
					page.addPartListener(partListener);
					for (IEditorReference editorRef : page.getEditorReferences()) {
						registerDocumentListener(editorRef.getEditor(false));
					}
				}
			}
		}
	}

	private IPageListener createPageListener() {
		return new IPageListener() {
			@Override
			public void pageOpened(IWorkbenchPage page) {
				page.addPartListener(partListener);
			}

			@Override
			public void pageClosed(IWorkbenchPage page) {
			}

			@Override
			public void pageActivated(IWorkbenchPage page) {
				page.addPartListener(partListener);
			}
		};
	}

	private IWindowListener createWindowListener() {
		return new IWindowListener() {
			@Override
			public void windowOpened(IWorkbenchWindow window) {
				window.addPageListener(pageListener);
			}

			@Override
			public void windowDeactivated(IWorkbenchWindow window) {
			}

			@Override
			public void windowClosed(IWorkbenchWindow window) {
			}

			@Override
			public void windowActivated(IWorkbenchWindow window) {
			}
		};
	}

	protected IPartListener2 createIPartListener2() {
		return new IPartListener2() {

			private void checkPart(IWorkbenchPartReference partRef) {
				IWorkbenchPart part = partRef.getPart(false);
				registerDocumentListener(part);
			}

			@Override
			public void partOpened(IWorkbenchPartReference partRef) {
				checkPart(partRef);
			}

			@Override
			public void partInputChanged(IWorkbenchPartReference partRef) {
				checkPart(partRef);
			}

			@Override
			public void partVisible(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partHidden(IWorkbenchPartReference partRef) {
				LastEditsDocumentListener.getInstance().update(partRef.getPart(false));
			}

			@Override
			public void partDeactivated(IWorkbenchPartReference partRef) {
			}

			@Override
			public void partClosed(IWorkbenchPartReference partRef) {
				LastEditsDocumentListener.getInstance().update(partRef.getPart(false));
			}

			@Override
			public void partBroughtToTop(IWorkbenchPartReference partRef) {
				registerDocumentListener(partRef.getPart(false));
			}

			@Override
			public void partActivated(IWorkbenchPartReference partRef) {
				registerDocumentListener(partRef.getPart(false));
			}
		};
	}

	private void registerDocumentListener(IWorkbenchPart part) {
		if (part instanceof IEditorPart) {
			IEditorPart editor = (IEditorPart) part;
			IEditorInput input = editor.getEditorInput();
			if (editor instanceof ITextEditor && input instanceof FileEditorInput) {
				IDocument document = (((ITextEditor) editor).getDocumentProvider()).getDocument(input);
				LastEditsDocumentListener listener = LastEditsDocumentListener.getInstance();
				document.addDocumentListener(listener);
				((ITextEditor) editor).addPropertyListener(listener);
			}
		}
	}

}
