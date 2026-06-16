package com.chabicht.code_intelligence.chat;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;

/**
 * Shows a local preview of an image attachment.
 */
public class ImageAttachmentDialog extends Dialog {

	private final ImageAttachment imageAttachment;

	public ImageAttachmentDialog(Shell parentShell, ImageAttachment imageAttachment) {
		super(parentShell);
		this.imageAttachment = imageAttachment;
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Image Attachment: " + imageAttachment.getDisplayName());
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(1, false));

		ScrolledComposite scrolledComposite = new ScrolledComposite(container, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		Composite imageContainer = new Composite(scrolledComposite, SWT.NONE);
		imageContainer.setLayout(new FillLayout());
		Label imageLabel = new Label(imageContainer, SWT.NONE);
		Image image = createSwtImage(parent.getShell());
		imageLabel.setImage(image);
		imageLabel.addDisposeListener(event -> {
			if (!image.isDisposed()) {
				image.dispose();
			}
		});

		Point size = new Point(image.getBounds().width, image.getBounds().height);
		imageContainer.setSize(size);
		scrolledComposite.setContent(imageContainer);
		scrolledComposite.setMinSize(size);

		return container;
	}

	@Override
	protected Point getInitialSize() {
		return new Point(800, 600);
	}

	private Image createSwtImage(Shell shell) {
		byte[] bytes = Base64.getDecoder().decode(imageAttachment.getBase64Data());
		ImageData[] imageData = new ImageLoader().load(new ByteArrayInputStream(bytes));
		return new Image(shell.getDisplay(), imageData[0]);
	}
}
