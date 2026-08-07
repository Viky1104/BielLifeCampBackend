package com.biel.lifecamp.system;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Runs reviewed Flyway migrations without starting Spring or runtime integrations.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
public final class SystemServiceMigrationApplication {
    private SystemServiceMigrationApplication() {
    }

    /**
     * Validates and migrates the system database, then exits.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        MigrationSettings settings = migrationSettings(System.getenv());
        Flyway flyway = Flyway.configure()
                .dataSource(settings.url(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(settings.baselineOnMigrate())
                .baselineVersion(MigrationVersion.fromVersion(settings.baselineVersion()))
                .load();
        MigrateResult result = flyway.migrate();
        System.out.printf(
                "Flyway migration completed: executed=%d, schema=%s%n",
                result.migrationsExecuted,
                result.schemaName);
    }

    static MigrationSettings migrationSettings(Map<String, String> environment) {
        return new MigrationSettings(
                required(environment, "DB_URL"),
                required(environment, "DB_USERNAME"),
                required(environment, "DB_PASSWORD"),
                Boolean.parseBoolean(environment.getOrDefault(
                        "SPRING_FLYWAY_BASELINE_ON_MIGRATE", "false")),
                environment.getOrDefault("SPRING_FLYWAY_BASELINE_VERSION", "1"));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }

    record MigrationSettings(
            String url,
            String username,
            String password,
            boolean baselineOnMigrate,
            String baselineVersion) {
    }
}
