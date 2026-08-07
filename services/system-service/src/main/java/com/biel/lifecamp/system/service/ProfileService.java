package com.biel.lifecamp.system.service;

import com.biel.lifecamp.system.model.dto.CurrentProfileViewDTO;
import com.biel.lifecamp.system.model.dto.response.AvatarUpdateResp;
import com.biel.lifecamp.system.model.dto.response.ProfileUpdateResp;
import org.springframework.web.multipart.MultipartFile;

/**
 * Current-user profile query and mutation service.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public interface ProfileService {
    /**
     * Loads profile display fields for the current employee.
     *
     * @param employeeId employee identifier
     * @return current profile view
     */
    CurrentProfileViewDTO current(long employeeId);

    /**
     * Validates and updates the nickname.
     *
     * @param employeeId employee identifier
     * @param nicknamePresent whether the JSON member was supplied
     * @param nickname requested nickname or {@code null}
     * @return normalized update result
     */
    ProfileUpdateResp updateNickname(
            long employeeId, boolean nicknamePresent, String nickname);

    /**
     * Validates, uploads, and persists a new avatar.
     *
     * @param employeeId employee identifier
     * @param avatar multipart avatar
     * @return signed URL and update time
     */
    AvatarUpdateResp uploadAvatar(long employeeId, MultipartFile avatar);
}
