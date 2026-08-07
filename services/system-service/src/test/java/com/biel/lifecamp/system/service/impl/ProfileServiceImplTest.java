package com.biel.lifecamp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.biel.lifecamp.system.model.dto.response.ProfileUpdateResp;
import com.biel.lifecamp.system.service.ProfilePersistenceService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Profile orchestration and compensation tests.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
class ProfileServiceImplTest {
    private ProfileMapper profileMapper;
    private ProfilePersistenceService persistenceService;
    private ProfileAvatarValidator avatarValidator;
    private ProfileObjectStorage objectStorage;
    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(ProfileMapper.class);
        persistenceService = mock(ProfilePersistenceService.class);
        avatarValidator = mock(ProfileAvatarValidator.class);
        objectStorage = mock(ProfileObjectStorage.class);
        ProfileStorageProperties properties = new ProfileStorageProperties();
        properties.setAvatarPrefix("private/avatars/");
        service = new ProfileServiceImpl(profileMapper, persistenceService,
                avatarValidator, objectStorage, properties);
    }

    @Test
    void stripsNicknameAndCountsUnicodeCodePoints() {
        Instant now = Instant.parse("2026-08-05T05:00:00Z");
        when(persistenceService.updateNickname(1001L, "营地😀"))
                .thenReturn(now);

        ProfileUpdateResp response = service.updateNickname(1001L, true, "  营地😀  ");

        assertThat(response.nickname()).isEqualTo("营地😀");
        assertThat(response.updatedAt()).isEqualTo(now);
    }

    @Test
    void rejectsMissingOrControlCharacterNickname() {
        assertThatThrownBy(() -> service.updateNickname(1001L, false, null))
                .isInstanceOf(ProfileException.class)
                .extracting(exception -> ((ProfileException) exception).code())
                .isEqualTo("PROFILE_NICKNAME_INVALID");
        assertThatThrownBy(() -> service.updateNickname(1001L, true, "name\nnext"))
                .isInstanceOf(ProfileException.class)
                .extracting(exception -> ((ProfileException) exception).code())
                .isEqualTo("PROFILE_NICKNAME_INVALID");
        verify(persistenceService, never()).updateNickname(1001L, null);
    }

    @Test
    void keepsCurrentProfileAvailableWhenAvatarSigningFails() {
        when(profileMapper.selectCurrentProfile(1001L)).thenReturn(new CurrentProfileDTO(
                "信息技术中心", "开发工程师", "昵称", "private/key.png", null));
        when(objectStorage.signedUrl("private/key.png"))
                .thenThrow(new ProfileStorageAccessException("disabled"));

        CurrentProfileViewDTO result = service.current(1001L);

        assertThat(result.organizationName()).isEqualTo("信息技术中心");
        assertThat(result.nickname()).isEqualTo("昵称");
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void deletesNewObjectWhenDatabasePersistenceFails() {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[]{1});
        when(avatarValidator.validate(avatar)).thenReturn(
                new ValidatedAvatarDTO(new byte[]{1}, "image/png", "png"));
        when(objectStorage.signedUrl(anyString())).thenReturn("https://signed.example/new");
        when(persistenceService.replaceAvatar(org.mockito.ArgumentMatchers.eq(1001L), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.uploadAvatar(1001L, avatar))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).delete(key.capture());
        assertThat(key.getValue()).startsWith("private/avatars/").endsWith(".png");
    }

    @Test
    void deletesOldObjectOnlyAfterSuccessfulReplacement() {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[]{1});
        when(avatarValidator.validate(avatar)).thenReturn(
                new ValidatedAvatarDTO(new byte[]{1}, "image/png", "png"));
        when(objectStorage.signedUrl(anyString())).thenReturn("https://signed.example/new");
        when(persistenceService.replaceAvatar(org.mockito.ArgumentMatchers.eq(1001L), anyString()))
                .thenReturn(new ProfileAvatarReplacementDTO(
                        "private/avatars/old.png", Instant.parse("2026-08-05T05:00:00Z")));

        service.uploadAvatar(1001L, avatar);

        verify(objectStorage).delete("private/avatars/old.png");
    }
}
