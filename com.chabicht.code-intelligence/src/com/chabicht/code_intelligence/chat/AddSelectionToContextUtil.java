package com.chabicht.code_intelligence.chat;

import java.io.BufferedInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.ChatConversation.MessageContext;
import com.chabicht.code_intelligence.model.ChatConversation.RangeType;

public class AddSelectionToContextUtil {
	private AddSelectionToContextUtil() {
		// No instances.
	}

	@SuppressWarnings("unchecked")
	static void addSelectionToContext(ISelection selectionObj) {
		if (selectionObj != null) {
			if (selectionObj instanceof TreeSelection treeSelection) {
				TreePath[] paths = treeSelection.getPaths();
				for (TreePath path : paths) {
					Object obj = path.getLastSegment();
					processSelectedObject(obj);
				}
			} else if (selectionObj instanceof IStructuredSelection ssel) {
				ssel.forEach(AddSelectionToContextUtil::processSelectedObject);
			} else if (selectionObj instanceof ITextSelection selection) {
				if (selection != null) {
					String selectedText = selection.getText();
					if (StringUtils.isNotBlank(selectedText)) {
						String fileName = getTextEditor().getEditorInput().getName();
						ChatView.addContext(new MessageContext(fileName, selection.getStartLine() + 1,
								selection.getEndLine() + 1, selectedText));
					}
				}
			}
		}
	}

	private static void processSelectedObject(Object obj) {
		if (obj instanceof ISourceReference sre) {
			try {
				String ancestor = getFileOrTypeName(sre);
				ISourceRange sourceRange = sre.getSourceRange();
				ChatView.addContext(new MessageContext(ancestor, RangeType.OFFSET, sourceRange.getOffset(),
						sourceRange.getOffset() + sourceRange.getLength(), sre.getSource()));
			} catch (JavaModelException e) {
				Activator.logError("Could not obtain source for reference " + obj, e);
			}
		} else if (obj instanceof LineElement le) {
			int line = le.getLine();
			String parent = le.getParent().getName();
			ChatView.addContext(new MessageContext(parent, line, line, le.getContents()));

		} else if (obj instanceof File f) {
			String name = f.getName();
			try {
				AtomicInteger lines = new AtomicInteger(0);
				StringBuilder content = new StringBuilder(1025);
				IOUtils.lineIterator(new BufferedInputStream(f.getContents()), f.getCharset())
						.forEachRemaining(l -> {
							lines.incrementAndGet();
							content.append(l).append("\n");
						});
				ChatView.addContext(new MessageContext(name, 1, lines.get(), content.toString()));
			} catch (CoreException e) {
				Activator.logError("Could not read file " + name, e);
			}
		} else if (obj instanceof IAdaptable adaptable) {
			IMarker marker = adaptable.getAdapter(IMarker.class);
			if (marker != null) {
				try {
					IFile file = marker.getResource().getAdapter(IFile.class);
					List<String> content = new ArrayList<>();
					IOUtils.lineIterator(file.getContents(), file.getCharset()).forEachRemaining(content::add);
					Integer offsetStart = (Integer) marker.getAttribute("charStart");
					Integer offsetEnd = (Integer) marker.getAttribute("charEnd");
					Integer lineNumber = (Integer) marker.getAttribute("lineNumber");
					String message = (String) marker.getAttribute("message");
					Integer severity = (Integer) marker.getAttribute("severity");

					int startLine = Math.max(0, lineNumber - 5);
					int endLine = Math.min(lineNumber + 5, content.size());
					String context = content.subList(startLine, endLine).stream().collect(Collectors.joining("\n"));
					StringBuilder sb = new StringBuilder();
					sb.append(severity == 2 ? "Error" : "Warning").append(" on line ").append(lineNumber)
							.append(" in document ").append(file.getName()).append(": ").append(message).append("\n");
					sb.append("Lines ").append(startLine).append(" to ").append(endLine)
							.append(" of the document:\n").append(context).append("\n");

					ChatView.addContext(new MessageContext(file.getName(), startLine, endLine, sb.toString()) {
						@Override
						public String getDescriptor() {
							return ""; // No comment above this.
						}
					});
				} catch (CoreException e) {
					Activator.logError("Could not add IMarker to context: " + marker.toString(), e);
				}
			}
		}
	}

	private static String getFileOrTypeName(ISourceReference sr) {
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

	private static ICompilationUnit getCompilationUnit(Object obj) {
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

	private static ITextEditor getTextEditor() {
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

}
