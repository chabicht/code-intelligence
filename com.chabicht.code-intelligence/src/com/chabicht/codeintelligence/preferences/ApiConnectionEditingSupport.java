package com.chabicht.codeintelligence.preferences;

import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.chabicht.code_intelligence.apiclient.AiApiConnection;
import com.chabicht.codeintelligence.preferences.CustomConfigurationParametersDialog.Row;

public class ApiConnectionEditingSupport extends EditingSupport {
	private List<AiApiConnection> connections;
	private ColumnViewer columnViewer;

	public ApiConnectionEditingSupport(ColumnViewer columnViewer, List<AiApiConnection> connections) {
		super(columnViewer);
		this.columnViewer = columnViewer;
		this.connections = connections;
	}

	@Override
	protected CellEditor getCellEditor(Object element) {
		return createApiConnectionEditor((Composite) getViewer().getControl());
	}

	@Override
	protected boolean canEdit(Object element) {
		return true;
	}

	@Override
	protected Object getValue(Object element) {
		if (element instanceof Row row) {
			String connectionName = row.getConnectionName();
			if (connectionName != null) {
				for (AiApiConnection conn : connections) {
					if (connectionName.equals(conn.getName())) {
						return conn;
					}
				}
			}
		}
		return null;
	}

	@Override
	protected void setValue(Object element, Object value) {
		if (element instanceof Row row) {
			if (value instanceof AiApiConnection conn) {
				row.setConnectionName(conn.getName());
				columnViewer.refresh();
			}
		}
	}

	private ComboBoxViewerCellEditor createApiConnectionEditor(Composite parent) {
		ComboBoxViewerCellEditor editor = new ComboBoxViewerCellEditor(parent, SWT.READ_ONLY);

		// Use ArrayContentProvider to work with the list of connections
		editor.setContentProvider(ArrayContentProvider.getInstance());

		// Display the name property of each connection
		editor.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof AiApiConnection) {
					return ((AiApiConnection) element).getName();
				}
				return super.getText(element);
			}
		});

		// Set the connections list as input
		editor.setInput(this.connections);

		return editor;
	}
}
