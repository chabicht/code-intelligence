package com.chabicht.code_intelligence.changelistener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPartConstants;
import org.eclipse.ui.texteditor.ITextEditor;

public class LastEditsDocumentListener implements IDocumentListener, IPropertyListener {

	private static LastEditsDocumentListener INSTANCE = null;

	private static final long DEBOUNCE_DELAY_MS = 1000;

	private Map<IDocument, DocumentEditAggregator> aggregators = new HashMap<>();

	private final Deque<String> lastEdits;

	private LastEditsDocumentListener() {
		lastEdits = new ArrayDeque<>(25);
	}

	public static LastEditsDocumentListener getInstance() {
		if (INSTANCE == null)
			INSTANCE = new LastEditsDocumentListener();
		return INSTANCE;
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// no-op
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		IDocument doc = event.getDocument();

		// Find or create aggregator for this doc
		DocumentEditAggregator aggregator = aggregators.get(doc);
		if (aggregator == null) {
			aggregator = new DocumentEditAggregator(doc, this);
			aggregators.put(doc, aggregator);
		}

		aggregator.addChange(event);
	}

	public Deque<String> getLastEdits() {
		return lastEdits;
	}

	@Override
	public void propertyChanged(Object source, int propId) {
		if (source instanceof ITextEditor texteditor && IWorkbenchPartConstants.PROP_DIRTY == propId
				&& !texteditor.isDirty()) {
			update(source);
		}
	}

	public void update(Object source) {
		if (source instanceof ITextEditor texteditor) {
			IDocument doc = texteditor.getDocumentProvider().getDocument(texteditor.getEditorInput());
			DocumentEditAggregator aggregator = aggregators.get(doc);
			if (aggregator != null) {
				aggregator.finalizeCurrentChunk();
			}
		}
	}
}
