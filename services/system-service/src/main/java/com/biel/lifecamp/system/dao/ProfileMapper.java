package com.biel.lifecamp.system.dao;

import com.biel.lifecamp.system.model.dto.CurrentProfileDTO;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for user-managed profile fields.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Mapper
public interface ProfileMapper {
    /**
     * Loads EHR display fields and optional user-managed profile fields.
     *
     * @param employeeId employee identifier
     * @return profile projection, or {@code null} when the employee does not exist
     */
    CurrentProfileDTO selectCurrentProfile(long employeeId);

    /**
     * Updates the nickname of an existing profile.
     *
     * @param employeeId employee identifier
     * @param nickname normalized nickname or {@code null}
     * @param now update time
     * @return affected rows
     */
    int updateNickname(@Param("employeeId") long employeeId,
                       @Param("nickname") String nickname,
                       @Param("now") Instant now);

    /**
     * Creates a profile for the employee's active WeChat identity.
     *
     * @param id profile identifier
     * @param employeeId employee identifier
     * @param nickname normalized nickname or {@code null}
     * @param avatarObjectKey avatar key or {@code null}
     * @param now creation time
     * @return inserted rows
     */
    int insertProfile(@Param("id") long id,
                      @Param("employeeId") long employeeId,
                      @Param("nickname") String nickname,
                      @Param("avatarObjectKey") String avatarObjectKey,
                      @Param("now") Instant now);

    /**
     * Locks and returns the currently persisted avatar key.
     *
     * @param employeeId employee identifier
     * @return current key, or {@code null}
     */
    String selectAvatarObjectKeyForUpdate(long employeeId);

    /**
     * Replaces the avatar key of an existing profile.
     *
     * @param employeeId employee identifier
     * @param avatarObjectKey new object key
     * @param now update time
     * @return affected rows
     */
    int updateAvatarObjectKey(@Param("employeeId") long employeeId,
                              @Param("avatarObjectKey") String avatarObjectKey,
                              @Param("now") Instant now);
}
