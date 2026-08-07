package com.biel.lifecamp.system.service;

import com.biel.lifecamp.system.model.dto.ProfileAvatarReplacementDTO;
import java.time.Instant;

/**
 * Short transactional persistence boundary for profile mutations.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public interface ProfilePersistenceService {
    /**
     * Persists a normalized nickname.
     *
     * @param employeeId employee identifier
     * @param nickname normalized nickname or {@code null}
     * @return update time
     */
    Instant updateNickname(long employeeId, String nickname);

    /**
     * Atomically replaces the avatar object key.
     *
     * @param employeeId employee identifier
     * @param objectKey new private object key
     * @return replacement metadata
     */
    ProfileAvatarReplacementDTO replaceAvatar(long employeeId, String objectKey);
}
