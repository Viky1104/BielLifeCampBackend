package com.biel.lifecamp.system.service.impl;

import com.biel.lifecamp.system.common.exception.ProfileException;
import com.biel.lifecamp.system.common.exception.ProfileStorageAccessException;
import com.biel.lifecamp.system.config.properties.ProfileStorageProperties;
import com.biel.lifecamp.system.dao.ProfileMapper;
import com.biel.lifecamp.system.manager.ProfileAvatarValidator;
import com.biel.lifecamp.system.manager.ProfileObjectStorage;
import com.biel.lifecamp.system.model.dto.CurrentProfileDTO;
import com.biel.lifecamp.system.model.dto.CurrentProfileViewDTO;
import com.biel.lifecamp.system.model.dto.ProfileAvatarReplacementDTO;
import com.biel.lifecamp.system.model.dto.ValidatedAvatarDTO;
import com.biel.lifecamp.system.model.dto.response.AvatarUpdateResp;
import com.biel.lifecamp.system.model.dto.response.ProfileUpdateResp;
import com.biel.lifecamp.system.service.ProfilePersistenceService;
import com.biel.lifecamp.system.service.ProfileService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * User profile orchestration with object-storage compensation.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Service
public class ProfileServiceImpl implements ProfileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileServiceImpl.class);
    private static final int MAX_NICKNAME_CODE_POINTS = 32;
    private final ProfileMapper profileMapper;
    private final ProfilePersistenceService persistenceService;
    private final ProfileAvatarValidator avatarValidator;
    private final ProfileObjectStorage objectStorage;
    private final ProfileStorageProperties storageProperties;

    public ProfileServiceImpl(
            ProfileMapper profileMapper,
            ProfilePersistenceService persistenceService,
            ProfileAvatarValidator avatarValidator,
            ProfileObjectStorage objectStorage,
            ProfileStorageProperties storageProperties) {
        this.profileMapper = profileMapper;
        this.persistenceService = persistenceService;
        this.avatarValidator = avatarValidator;
        this.objectStorage = objectStorage;
        this.storageProperties = storageProperties;
    }

    @Override
    public CurrentProfileViewDTO current(long employeeId) {
        CurrentProfileDTO profile = profileMapper.selectCurrentProfile(employeeId);
        if (profile == null) {
            return new CurrentProfileViewDTO(null, null, null, null);
        }
        return new CurrentProfileViewDTO(
                profile.organizationName(), profile.positionName(), profile.nickname(),
                resolveAvatarUrl(employeeId, profile));
    }

    @Override
    public ProfileUpdateResp updateNickname(
            long employeeId, boolean nicknamePresent, String nickname) {
        String normalized = normalizeNickname(nicknamePresent, nickname);
        Instant updatedAt = persistenceService.updateNickname(employeeId, normalized);
        return new ProfileUpdateResp(normalized, updatedAt);
    }

    @Override
    public AvatarUpdateResp uploadAvatar(long employeeId, MultipartFile avatar) {
        ValidatedAvatarDTO validated = avatarValidator.validate(avatar);
        String objectKey = nextObjectKey(validated.extension());
        String signedUrl = uploadAndSign(objectKey, validated);
        ProfileAvatarReplacementDTO replacement;
        try {
            replacement = persistenceService.replaceAvatar(employeeId, objectKey);
        } catch (RuntimeException exception) {
            deleteBestEffort(employeeId, objectKey, "new avatar after database failure");
            throw exception;
        }
        if (StringUtils.hasText(replacement.oldObjectKey())
                && !objectKey.equals(replacement.oldObjectKey())) {
            deleteBestEffort(employeeId, replacement.oldObjectKey(), "replaced avatar");
        }
        return new AvatarUpdateResp(signedUrl, replacement.updatedAt());
    }

    private String normalizeNickname(boolean nicknamePresent, String nickname) {
        if (!nicknamePresent) {
            throw ProfileException.invalid(
                    "PROFILE_NICKNAME_INVALID", "nickname member is required");
        }
        if (nickname == null) {
            return null;
        }
        String normalized = nickname.strip();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints == 0 || codePoints > MAX_NICKNAME_CODE_POINTS
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw ProfileException.invalid(
                    "PROFILE_NICKNAME_INVALID", "Nickname must contain 1 to 32 characters");
        }
        return normalized;
    }

    private String resolveAvatarUrl(long employeeId, CurrentProfileDTO profile) {
        if (!StringUtils.hasText(profile.avatarObjectKey())) {
            return StringUtils.hasText(profile.legacyAvatarUrl())
                    ? profile.legacyAvatarUrl() : null;
        }
        try {
            return objectStorage.signedUrl(profile.avatarObjectKey());
        } catch (ProfileStorageAccessException exception) {
            LOGGER.warn("Profile avatar signing unavailable, employeeId={}", employeeId);
            return null;
        }
    }

    private String uploadAndSign(String objectKey, ValidatedAvatarDTO avatar) {
        boolean uploaded = false;
        try {
            objectStorage.upload(objectKey, avatar.bytes(), avatar.contentType());
            uploaded = true;
            return objectStorage.signedUrl(objectKey);
        } catch (ProfileStorageAccessException exception) {
            if (uploaded) {
                deleteBestEffort(null, objectKey, "unsigned new avatar");
            }
            throw ProfileException.storageUnavailable();
        }
    }

    private String nextObjectKey(String extension) {
        String prefix = storageProperties.getAvatarPrefix();
        String normalizedPrefix = StringUtils.hasText(prefix)
                ? prefix.replaceAll("^/+|/+$", "") : "profiles/avatars";
        return normalizedPrefix + "/" + UUID.randomUUID() + "." + extension;
    }

    private void deleteBestEffort(Long employeeId, String objectKey, String reason) {
        try {
            objectStorage.delete(objectKey);
        } catch (ProfileStorageAccessException exception) {
            LOGGER.warn("Profile avatar cleanup failed, employeeId={}, reason={}",
                    employeeId, reason);
        }
    }
}
