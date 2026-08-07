package com.biel.lifecamp.system.model.dto;

/**
 * Current employee profile projection from local persistence.
 *
 * @param organizationName organization name snapshot
 * @param positionName position name snapshot
 * @param nickname user-managed nickname
 * @param avatarObjectKey private object-storage key
 * @param legacyAvatarUrl legacy public avatar URL
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public record CurrentProfileDTO(
        String organizationName,
        String positionName,
        String nickname,
        String avatarObjectKey,
        String legacyAvatarUrl) {
}
