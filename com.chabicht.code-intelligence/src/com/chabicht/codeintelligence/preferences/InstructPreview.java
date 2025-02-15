package com.chabicht.codeintelligence.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

public class InstructPreview extends Composite {

	private Browser bPreview;

	public InstructPreview(Composite parent, int style) {
		super(parent, style);
		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		setLayout(gridLayout);

		bPreview = new Browser(this, SWT.NONE);
		bPreview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
	}

	public boolean setText(String html) {
		int scrollPosition = getPreviewScrollPosition();
		boolean res = bPreview.setText(html);
		scrollPreviewTo(scrollPosition);
		return res;
	}

	private int getPreviewScrollPosition() {
		Object result = bPreview.evaluate("return window.pageYOffset;");
		int scrollPosition = 0;
		if (result instanceof Number) {
			scrollPosition = ((Number) result).intValue();
		}
		return scrollPosition;
	}

	private void scrollPreviewTo(int scrollPosition) {
		bPreview.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {
				bPreview.execute("window.scrollTo(0, " + scrollPosition + ");");
				bPreview.removeProgressListener(this);
			}
		});
	}
}
