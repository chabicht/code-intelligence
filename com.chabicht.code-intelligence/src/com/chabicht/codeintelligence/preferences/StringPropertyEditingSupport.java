package com.chabicht.codeintelligence.preferences;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.widgets.Table;

import com.chabicht.code_intelligence.Activator;

public class StringPropertyEditingSupport extends EditingSupport {

	private ColumnViewer viewer;
	private String property;
	private Table table;

	public StringPropertyEditingSupport(Table table, ColumnViewer viewer, String property) {
		super(viewer);
		this.table = table;
		this.viewer = viewer;
		this.property = property;
	}

	@Override
	protected CellEditor getCellEditor(Object element) {
		return new TextCellEditor(table);
	}

	@Override
	protected boolean canEdit(Object element) {
		return true;
	}

	@Override
	protected Object getValue(Object element) {
		try {
			return StringUtils.stripToEmpty(BeanUtils.getSimpleProperty(element, property));
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			Activator.logError(e.getMessage(), e);
			return "";
		}
	}

	@Override
	protected void setValue(Object element, Object value) {
		if (value instanceof String s) {
			try {
				BeanUtils.setProperty(element, property, value);
				viewer.refresh();
			} catch (IllegalAccessException | InvocationTargetException e) {
				Activator.logError(e.getMessage(), e);
			}
		}
	}

}
