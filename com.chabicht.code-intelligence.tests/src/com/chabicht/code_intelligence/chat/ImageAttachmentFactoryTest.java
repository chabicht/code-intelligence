package com.chabicht.code_intelligence.chat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.chabicht.code_intelligence.model.ChatConversation.ImageAttachment;

public class ImageAttachmentFactoryTest {

	@TempDir
	Path tempDir;

	private final ImageAttachmentFactory factory = new ImageAttachmentFactory();

	@Test
	void createsPngAttachmentFromFileAndPreservesBytesRegardlessExtension() throws IOException {
		byte[] imageBytes = createImageBytes(SWT.IMAGE_PNG, 3, 2);
		Path file = tempDir.resolve("misleading.txt");
		Files.write(file, imageBytes);

		ImageAttachment attachment = factory.fromFile(file.toFile());

		assertEquals("misleading.txt", attachment.getDisplayName());
		assertEquals(ImageAttachment.MEDIA_TYPE_PNG, attachment.getMediaType());
		assertEquals(3, attachment.getWidth());
		assertEquals(2, attachment.getHeight());
		assertEquals(imageBytes.length, attachment.getByteSize());
		assertArrayEquals(imageBytes, Base64.getDecoder().decode(attachment.getBase64Data()));
	}

	@Test
	void createsJpegAttachmentFromFileUsingByteSignature() throws IOException {
		byte[] imageBytes = createImageBytes(SWT.IMAGE_JPEG, 5, 4);
		Path file = tempDir.resolve("photo.bin");
		Files.write(file, imageBytes);

		ImageAttachment attachment = factory.fromFile(file.toFile());

		assertEquals("photo.bin", attachment.getDisplayName());
		assertEquals(ImageAttachment.MEDIA_TYPE_JPEG, attachment.getMediaType());
		assertEquals(5, attachment.getWidth());
		assertEquals(4, attachment.getHeight());
		assertEquals(imageBytes.length, attachment.getByteSize());
		assertArrayEquals(imageBytes, Base64.getDecoder().decode(attachment.getBase64Data()));
	}

	@Test
	void rejectsGifFilesWithClearMessage() throws IOException {
		Path file = tempDir.resolve("animation.gif");
		Files.write(file, "GIF89a".getBytes());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> factory.fromFile(file.toFile()));

		assertTrue(exception.getMessage().contains("Only PNG and JPEG images are supported."));
	}

	@Test
	void rejectsWebpFilesWithClearMessage() throws IOException {
		Path file = tempDir.resolve("image.webp");
		Files.write(file, new byte[] { 'R', 'I', 'F', 'F', 16, 0, 0, 0, 'W', 'E', 'B', 'P' });

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> factory.fromFile(file.toFile()));

		assertTrue(exception.getMessage().contains("Only PNG and JPEG images are supported."));
	}

	@Test
	void createsPngAttachmentFromPastedImageData() {
		ImageData imageData = createImageData(7, 6);

		ImageAttachment attachment = factory.fromImageData(imageData, "pasted-image.png");

		assertEquals("pasted-image.png", attachment.getDisplayName());
		assertEquals(ImageAttachment.MEDIA_TYPE_PNG, attachment.getMediaType());
		assertEquals(7, attachment.getWidth());
		assertEquals(6, attachment.getHeight());
		assertTrue(attachment.getByteSize() > 8);
		byte[] decodedBytes = Base64.getDecoder().decode(attachment.getBase64Data());
		assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' },
				java.util.Arrays.copyOf(decodedBytes, 8));
	}

	private static byte[] createImageBytes(int swtFormat, int width, int height) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] { createImageData(width, height) };
		loader.save(output, swtFormat);
		return output.toByteArray();
	}

	private static ImageData createImageData(int width, int height) {
		PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
		ImageData imageData = new ImageData(width, height, 24, palette);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int red = (x * 32) & 0xFF;
				int green = (y * 32) & 0xFF;
				int blue = ((x + y) * 16) & 0xFF;
				imageData.setPixel(x, y, palette.getPixel(new org.eclipse.swt.graphics.RGB(red, green, blue)));
			}
		}
		return imageData;
	}
}
