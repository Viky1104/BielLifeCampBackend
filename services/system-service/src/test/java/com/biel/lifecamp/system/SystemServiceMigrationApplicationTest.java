package com.biel.lifecamp.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemServiceMigrationApplicationTest {
    @Test
    void migrationSettingsComeOnlyFromExplicitDatabaseEnvironment() {
        SystemServiceMigrationApplication.MigrationSettings settings =
                SystemServiceMigrationApplication.migrationSettings(Map.of(
                        "DB_URL", "jdbc:mysql://db.example.test/system",
                        "DB_USERNAME", "system_user",
                        "DB_PASSWORD", "secret",
                        "SPRING_FLYWAY_BASELINE_ON_MIGRATE", "true",
                        "SPRING_FLYWAY_BASELINE_VERSION", "6"));

        assertThat(settings.url()).isEqualTo("jdbc:mysql://db.example.test/system");
        assertThat(settings.username()).isEqualTo("system_user");
        assertThat(settings.password()).isEqualTo("secret");
        assertThat(settings.baselineOnMigrate()).isTrue();
        assertThat(settings.baselineVersion()).isEqualTo("6");
    }

    @Test
    void migrationSettingsRejectMissingDatabaseCredentials() {
        assertThatThrownBy(() -> SystemServiceMigrationApplication.migrationSettings(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }
}
