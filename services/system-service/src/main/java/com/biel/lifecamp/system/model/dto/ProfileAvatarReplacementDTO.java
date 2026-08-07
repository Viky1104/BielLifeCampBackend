package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * Result of atomically replacing the persisted avatar object key.
 *
 * @param oldObjectKey object key replaced by this update
 * @param updatedAt update time
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public record ProfileAvatarReplacementDTO(String oldObjectKey, Instant updatedAt) {
}
