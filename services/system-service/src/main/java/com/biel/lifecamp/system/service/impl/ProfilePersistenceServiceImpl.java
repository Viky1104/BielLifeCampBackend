package com.biel.lifecamp.system.service.impl;

import com.biel.lifecamp.system.common.exception.ProfileException;
import com.biel.lifecamp.system.common.id.LongIdGenerator;
import com.biel.lifecamp.system.dao.ProfileMapper;
import com.biel.lifecamp.system.model.dto.ProfileAvatarReplacementDTO;
import com.biel.lifecamp.system.service.ProfilePersistenceService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short database transactions for user profile mutations.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Service
public class ProfilePersistenceServiceImpl implements ProfilePersistenceService {
    private final ProfileMapper profileMapper;
    private final LongIdGenerator idGenerator;
    private final Clock clock;

    public ProfilePersistenceServiceImpl(
            ProfileMapper profileMapper, LongIdGenerator idGenerator, Clock clock) {
        this.profileMapper = profileMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Instant updateNickname(long employeeId, String nickname) {
        Instant now = clock.instant();
        if (profileMapper.updateNickname(employeeId, nickname, now) == 0) {
            insertOrRetryNickname(employeeId, nickname, now);
        }
        return now;
    }

    @Override
    @Transactional
    public ProfileAvatarReplacementDTO replaceAvatar(long employeeId, String objectKey) {
        Instant now = clock.instant();
        String oldObjectKey = profileMapper.selectAvatarObjectKeyForUpdate(employeeId);
        if (profileMapper.updateAvatarObjectKey(employeeId, objectKey, now) == 0) {
            insertOrRetryAvatar(employeeId, objectKey, now);
        }
        return new ProfileAvatarReplacementDTO(oldObjectKey, now);
    }

    private void insertOrRetryNickname(long employeeId, String nickname, Instant now) {
        try {
            ensureProfileInserted(profileMapper.insertProfile(
                    idGenerator.next(), employeeId, nickname, null, now));
        } catch (DuplicateKeyException exception) {
            if (profileMapper.updateNickname(employeeId, nickname, now) == 0) {
                throw profileIdentityMissing();
            }
        }
    }

    private void insertOrRetryAvatar(long employeeId, String objectKey, Instant now) {
        try {
            ensureProfileInserted(profileMapper.insertProfile(
                    idGenerator.next(), employeeId, null, objectKey, now));
        } catch (DuplicateKeyException exception) {
            if (profileMapper.updateAvatarObjectKey(employeeId, objectKey, now) == 0) {
                throw profileIdentityMissing();
            }
        }
    }

    private void ensureProfileInserted(int insertedRows) {
        if (insertedRows != 1) {
            throw profileIdentityMissing();
        }
    }

    private ProfileException profileIdentityMissing() {
        return new ProfileException(HttpStatus.CONFLICT,
                "PROFILE_IDENTITY_MISSING", "Active WeChat identity required");
    }
}
