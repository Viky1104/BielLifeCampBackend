package com.biel.lifecamp.system.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biel.lifecamp.system.common.exception.ProfileException;
import com.biel.lifecamp.system.model.dto.ValidatedAvatarDTO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Real image decoding tests for avatar validation.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
class ProfileAvatarValidatorTest {
    private final ProfileAvatarValidator validator = new ProfileAvatarValidator();

    @Test
    void detectsDecodedPngInsteadOfTrustingDeclaredContentType() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "fake.jpg", "image/jpeg", output.toByteArray());

        ValidatedAvatarDTO result = validator.validate(avatar);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void rejectsMalformedImageBytes() {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        assertThatThrownBy(() -> validator.validate(avatar))
                .isInstanceOf(ProfileException.class)
                .extracting(exception -> ((ProfileException) exception).code())
                .isEqualTo("PROFILE_AVATAR_INVALID");
    }

    @Test
    void rejectsPayloadOverTwoMegabytes() {
        byte[] oversized = new byte[ProfileAvatarValidator.MAX_AVATAR_BYTES + 1];
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", oversized);

        assertThatThrownBy(() -> validator.validate(avatar))
                .isInstanceOf(ProfileException.class)
                .extracting(exception -> ((ProfileException) exception).code())
                .isEqualTo("PROFILE_AVATAR_TOO_LARGE");
    }
}
