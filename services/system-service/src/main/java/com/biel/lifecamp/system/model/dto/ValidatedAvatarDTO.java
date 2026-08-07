package com.biel.lifecamp.system.model.dto;

/**
 * Avatar content after real image decoding and format validation.
 *
 * @param bytes original validated bytes
 * @param contentType detected media type
 * @param extension safe object-key extension
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public record ValidatedAvatarDTO(byte[] bytes, String contentType, String extension) {
    public ValidatedAvatarDTO {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
