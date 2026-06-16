package com.chabicht.code_intelligence.chat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;

import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;

public final class ImageAttachmentFactory {

	private static final String UNSUPPORTED_FORMAT_MESSAGE = "Only PNG and JPEG images are supported.";
	private static final byte[] PNG_SIGNATURE = new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	public ImageAttachment fromFile(File file) {
		if (file == null) {
			throw new IllegalArgumentException("Image file must not be null.");
		}

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file.toPath());
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read image file: " + file.getName(), e);
		}

		String mediaType = detectSupportedMediaType(bytes);
		ImageData imageData = decodeImageData(bytes);
		return createAttachment(file.getName(), mediaType, bytes, imageData.width, imageData.height);
	}

	public ImageAttachment fromImageData(ImageData imageData, String displayName) {
		if (imageData == null) {
			throw new IllegalArgumentException("Image data must not be null.");
		}
		String effectiveDisplayName = StringUtils.defaultIfBlank(displayName, "pasted-image.png");
		byte[] bytes = encodePng(imageData);
		ImageData decodedImageData = decodeImageData(bytes);
		return createAttachment(effectiveDisplayName, ImageAttachment.MEDIA_TYPE_PNG, bytes, decodedImageData.width,
				decodedImageData.height);
	}

	private static ImageAttachment createAttachment(String displayName, String mediaType, byte[] bytes, int width,
			int height) {
		return new ImageAttachment(displayName, mediaType, Base64.getEncoder().encodeToString(bytes), width, height,
				bytes.length);
	}

	private static byte[] encodePng(ImageData imageData) {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageLoader imageLoader = new ImageLoader();
			imageLoader.data = new ImageData[] { imageData };
			imageLoader.save(output, SWT.IMAGE_PNG);
			return output.toByteArray();
		} catch (SWTException e) {
			throw new IllegalArgumentException("Could not encode pasted image data.", e);
		}
	}

	private static ImageData decodeImageData(byte[] bytes) {
		try {
			ImageData[] data = new ImageLoader().load(new ByteArrayInputStream(bytes));
			if (data.length == 0) {
				throw new IllegalArgumentException(UNSUPPORTED_FORMAT_MESSAGE);
			}
			return data[0];
		} catch (SWTException | IllegalArgumentException e) {
			throw new IllegalArgumentException(UNSUPPORTED_FORMAT_MESSAGE, e);
		}
	}

	private static String detectSupportedMediaType(byte[] bytes) {
		if (hasPngSignature(bytes)) {
			return ImageAttachment.MEDIA_TYPE_PNG;
		}
		if (hasJpegSignature(bytes)) {
			return ImageAttachment.MEDIA_TYPE_JPEG;
		}
		throw new IllegalArgumentException(UNSUPPORTED_FORMAT_MESSAGE);
	}

	private static boolean hasPngSignature(byte[] bytes) {
		if (bytes.length < PNG_SIGNATURE.length) {
			return false;
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++) {
			if (bytes[i] != PNG_SIGNATURE[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasJpegSignature(byte[] bytes) {
		return bytes.length >= 4 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
				&& (bytes[bytes.length - 2] & 0xFF) == 0xFF && (bytes[bytes.length - 1] & 0xFF) == 0xD9;
	}
}
