package com.chabicht.codeintelligence.preferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import com.chabicht.code_intelligence.Bean;
import com.chabicht.code_intelligence.CustomConfigurationParameters;
import com.chabicht.code_intelligence.apiclient.AiApiConnection;

public class CustomConfigurationParametersDialog extends Dialog {
	private static final String NEW = "<NEW>";
	private Table table;
	private List<AiApiConnection> connections;

	private WritableList<Row> rows = new WritableList<>();

	protected CustomConfigurationParametersDialog(Shell parentShell, List<AiApiConnection> connections) {
		super(parentShell);
		this.connections = connections;

		Map<String, Map<String, String>> config = CustomConfigurationParameters.getInstance().getMap();
		for (Entry<String, Map<String, String>> connEntry : config.entrySet()) {
			if (connEntry.getValue() != null) {
				for (Entry<String, String> e : connEntry.getValue().entrySet()) {
					Row r = newRow();
					r.setConnectionName(connEntry.getKey());
					r.setKey(e.getKey());
					r.setValue(e.getValue());
					addRow(r);
				}
			}
		}
	}

	public void save() {
		Map<String, Map<String, String>> config = new HashMap<>();
		for (Row row : rows) {
			if (!NEW.equals(row.getConnectionName()) && StringUtils.isNotBlank(row.getConnectionName())
					&& StringUtils.isNotBlank(row.getKey()) && row.getValue() != null) {
				String connectionName = row.getConnectionName();
				String key = row.getKey();
				String value = row.getValue();
				if (!config.containsKey(connectionName)) {
					config.put(connectionName, new HashMap<>());
				}
				config.get(connectionName).put(key, value);
			}
		}
		CustomConfigurationParameters.getInstance().setMap(config);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		composite.setLayout(new GridLayout(1, false));
		applyDialogFont(composite);

		TableViewer tableViewer = new TableViewer(composite, SWT.BORDER | SWT.FULL_SELECTION);
		table = tableViewer.getTable();
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		TableViewerColumn tvcConnection = new TableViewerColumn(tableViewer, SWT.NONE);
		tvcConnection.setEditingSupport(new ApiConnectionEditingSupport(tvcConnection.getViewer(), connections));
		tvcConnection.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Row row = (Row) cell.getElement();
				cell.setText(row.getConnectionName());
			}
		});
		TableColumn tblclmnConnection = tvcConnection.getColumn();
		tblclmnConnection.setWidth(200);
		tblclmnConnection.setText("API Connection");

		TableViewerColumn tvcKey = new TableViewerColumn(tableViewer, SWT.NONE);
		tvcKey.setEditingSupport(new StringPropertyEditingSupport(tableViewer.getTable(), tvcKey.getViewer(), "key"));
		tvcKey.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Row row = (Row) cell.getElement();
				cell.setText(row.getKey());
			}
		});
		TableColumn tblclmnKey = tvcKey.getColumn();
		tblclmnKey.setWidth(200);
		tblclmnKey.setText("Key");

		TableViewerColumn tvcValue = new TableViewerColumn(tableViewer, SWT.NONE);
		tvcValue.setEditingSupport(
				new StringPropertyEditingSupport(tableViewer.getTable(), tvcValue.getViewer(), "value"));
		tvcValue.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Row row = (Row) cell.getElement();
				cell.setText(row.getValue());
			}
		});
		TableColumn tblclmnValue = tvcValue.getColumn();
		tblclmnValue.setWidth(200);
		tblclmnValue.setText("Value");

		tableViewer.setContentProvider(new ObservableListContentProvider<Row>());
		handleNewRow();

		tableViewer.setInput(rows);

		return composite;
	}

	private void handleNewRow() {
		boolean found = false;
		for (Row row : rows) {
			if (NEW.equals(row.getConnectionName())) {
				found = true;
				break;
			}
		}

		if (!found) {
			Row newRow = newRow();
			newRow.setConnectionName(NEW);
			addRow(newRow);
		}
	}

	private boolean addRow(Row row) {
		boolean added = rows.add(row);
		if (added) {
			row.addPropertyChangeListener("connectionName", e -> {
				handleNewRow();
			});
		}
		return added;
	}

	private Row newRow() {
		Row row = new Row();
		return row;
	}

	@Override
	protected Point getInitialSize() {
		return new Point(610, 400);
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	public static class Row extends Bean {
		private String connectionName;
		private String key;
		private String value;

		public String getConnectionName() {
			return connectionName;
		}

		public void setConnectionName(String connectionName) {
			propertyChangeSupport.firePropertyChange("connectionName", this.connectionName,
					this.connectionName = connectionName);
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			propertyChangeSupport.firePropertyChange("key", this.key, this.key = key);
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			propertyChangeSupport.firePropertyChange("value", this.value, this.value = value);
		}
	}
}
