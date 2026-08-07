package com.biel.lifecamp.system.manager;

import com.biel.lifecamp.system.common.exception.ProfileException;
import com.biel.lifecamp.system.model.dto.ValidatedAvatarDTO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates avatar size, real image format, dimensions, and decodability.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Component
public final class ProfileAvatarValidator {
    static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 16_777_216L;

    /**
     * Validates an uploaded JPEG, PNG, or WebP image.
     *
     * @param avatar multipart avatar
     * @return validated immutable metadata
     */
    public ValidatedAvatarDTO validate(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw invalidAvatar();
        }
        if (avatar.getSize() > MAX_AVATAR_BYTES) {
            throw ProfileException.avatarTooLarge();
        }
        byte[] bytes = readBounded(avatar);
        return decode(bytes);
    }

    private byte[] readBounded(MultipartFile avatar) {
        try (InputStream input = avatar.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_AVATAR_BYTES + 1);
            if (bytes.length > MAX_AVATAR_BYTES) {
                throw ProfileException.avatarTooLarge();
            }
            if (bytes.length == 0) {
                throw invalidAvatar();
            }
            return bytes;
        } catch (IOException exception) {
            throw invalidAvatar();
        }
    }

    private ValidatedAvatarDTO decode(byte[] bytes) {
        ImageReader reader = null;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (imageInput == null) {
                throw invalidAvatar();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidAvatar();
            }
            reader = readers.next();
            reader.setInput(imageInput, true, true);
            ImageFormat format = ImageFormat.from(reader.getFormatName());
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0
                    || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                throw invalidAvatar();
            }
            BufferedImage decoded = reader.read(0);
            if (decoded == null) {
                throw invalidAvatar();
            }
            return new ValidatedAvatarDTO(bytes, format.contentType, format.extension);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidAvatar();
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private ProfileException invalidAvatar() {
        return ProfileException.invalid(
                "PROFILE_AVATAR_INVALID", "Avatar must be a valid JPEG, PNG, or WebP image");
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        private static ImageFormat from(String value) {
            String normalized = value.toUpperCase(Locale.ROOT);
            if ("JPG".equals(normalized) || "JPEG".equals(normalized)) {
                return JPEG;
            }
            if ("PNG".equals(normalized)) {
                return PNG;
            }
            if ("WEBP".equals(normalized)) {
                return WEBP;
            }
            throw new IllegalArgumentException("Unsupported image format");
        }
    }
}
