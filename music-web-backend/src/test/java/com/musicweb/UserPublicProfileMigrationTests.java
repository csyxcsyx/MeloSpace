package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class UserPublicProfileMigrationTests {

    @Test
    void v2MigrationAppliesToH2InMySqlMode() throws Exception {
        String databaseName = "profile_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `user` (
                        id BIGINT PRIMARY KEY,
                        nickname VARCHAR(50) NOT NULL,
                        status TINYINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE playlist (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        visibility VARCHAR(20) NOT NULL,
                        updated_at DATETIME NOT NULL
                    )
                    """);

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V2__user_public_profile.sql")
            );

            statement.execute("INSERT INTO `user` (id, nickname, status, bio) VALUES (1, '用户', 1, '公开简介')");
            try (ResultSet bio = statement.executeQuery("SELECT bio FROM `user` WHERE id = 1")) {
                assertThat(bio.next()).isTrue();
                assertThat(bio.getString(1)).isEqualTo("公开简介");
            }
            try (ResultSet indexes = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.indexes
                    WHERE index_name IN (
                        'idx_playlist_user_visibility_updated_at',
                        'idx_user_status_nickname'
                    )
                    """)) {
                assertThat(indexes.next()).isTrue();
                assertThat(indexes.getLong(1)).isEqualTo(2);
            }
        }
    }
}
