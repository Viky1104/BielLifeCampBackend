package com.biel.lifecamp.system.model.dto;

/**
 * Profile fields safe to expose to the current authenticated user.
 *
 * @param organizationName organization name snapshot
 * @param positionName position name snapshot
 * @param nickname user-managed nickname
 * @param avatarUrl one-hour signed avatar URL, or {@code null}
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public record CurrentProfileViewDTO(
        String organizationName,
        String positionName,
        String nickname,
        String avatarUrl) {
}
