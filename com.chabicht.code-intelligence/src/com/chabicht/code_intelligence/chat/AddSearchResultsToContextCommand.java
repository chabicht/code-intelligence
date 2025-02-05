package com.chabicht.code_intelligence.chat;

import java.io.BufferedInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.search.internal.ui.text.LineElement;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;

public class AddSearchResultsToContextCommand implements IHandler {

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selectionObj = org.eclipse.ui.handlers.HandlerUtil.getCurrentSelection(event);
		System.out.println(selectionObj);
		if (selectionObj != null) {
			if (selectionObj instanceof TreeSelection treeSelection) {
				TreePath[] paths = treeSelection.getPaths();
				for (TreePath path : paths) {
					Object segmentObj = path.getLastSegment();
					if (segmentObj instanceof ISourceReference sre) {
						try {
							String ancestor = getFileOrTypeName(sre);
							ISourceRange sourceRange = sre.getSourceRange();
							ChatView.getExternallyaddedcontext()
									.add(new MessageContext(ancestor, "offset", sourceRange.getOffset(),
											sourceRange.getOffset() + sourceRange.getLength(), sre.getSource()));
						} catch (JavaModelException e) {
							Activator.logError("Could not obtain source for reference " + segmentObj, e);
						}
					} else if (segmentObj instanceof LineElement le) {
						int line = le.getLine();
						String parent = le.getParent().getName();
						ChatView.getExternallyaddedcontext()
								.add(new MessageContext(parent, line, line, le.getContents()));

					} else if (segmentObj instanceof File f) {
						String name = f.getName();
						try {
							AtomicInteger lines = new AtomicInteger(0);
							StringBuilder content = new StringBuilder(1025);
							IOUtils.lineIterator(new BufferedInputStream(f.getContents()), f.getCharset())
									.forEachRemaining(l -> {
								lines.incrementAndGet();
								content.append(l).append("\n");
							});
							ChatView.getExternallyaddedcontext()
									.add(new MessageContext(name, 1, lines.get(), content.toString()));
						} catch (CoreException e) {
							Activator.logError("Could not read file " + name, e);
						}
					}
				}
				System.out.println(paths);
			}
		}
		return null;
	}

	private String getFileOrTypeName(ISourceReference sr) {
		if (sr instanceof IJavaElement je) {
			for (int type : new int[] { IJavaElement.COMPILATION_UNIT, IJavaElement.TYPE }) {
				IJavaElement ancestor = je.getAncestor(type);
				if (ancestor != null) {
					return ancestor.getElementName();
				}
			}
		}
		return "unknown source";
	}

	private ICompilationUnit getCompilationUnit(Object obj) {
		if (obj == null) {
			return null;
		}

		try {
			Object compUnitObj = PropertyUtils.getProperty(obj, "compilationUnit");
			return compUnitObj instanceof ICompilationUnit compUnit ? compUnit : null;
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			// No getKey method.
		}

		return null;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {
	}

}
