package com.biel.lifecamp.system.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * User-managed profile update. Explicit {@code null} clears the nickname.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Schema(name = "UpdateProfileRequest", description = "Current user profile update")
public final class UpdateProfileReq {
    private String nickname;
    private boolean nicknamePresent;

    @Schema(description = "Nickname; null clears it", nullable = true, example = "小营友")
    public String getNickname() {
        return nickname;
    }

    /**
     * Captures both the value and whether the JSON member was supplied.
     *
     * @param nickname nickname or {@code null}
     */
    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    @JsonIgnore
    public boolean isNicknamePresent() {
        return nicknamePresent;
    }
}
