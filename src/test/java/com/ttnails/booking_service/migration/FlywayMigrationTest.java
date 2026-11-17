package com.ttnails.booking_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S0-04 + S1-01 — chạy toàn bộ Flyway migration trên DB trống (H2 MODE=MySQL)
 * rồi kiểm tra schema của Sprint 0 và Sprint 1.
 *
 * <p>Test này bắt lỗi cú pháp migration ngay trên CI/local mà không cần Docker.
 * Trước khi deploy staging nên chạy thêm {@code flyway:migrate} trên MySQL thật
 * (xem docs/db/rollback-V1.md và docs/db/rollback-V2.md).</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_migration;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int tableCount(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table);
        return count != null ? count : 0;
    }

    private int columnCount(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                Integer.class, table, column);
        return count != null ? count : 0;
    }

    @Test
    void migrationCreatesBaselineSchema() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class);
        assertThat(applied).isNotNull().isGreaterThanOrEqualTo(2);

        for (String table : new String[]{"user", "user_roles", "services", "booking", "invalid_token"}) {
            assertThat(tableCount(table))
                    .as("bảng %s phải tồn tại sau V1__init.sql", table)
                    .isEqualTo(1);
        }
    }

    @Test
    void sprint1MigrationCreatesStaffAvailabilitySchema() {
        for (String table : new String[]{"branch", "staff", "staff_skill", "staff_shift",
                "time_off", "booking_item"}) {
            assertThat(tableCount(table))
                    .as("bảng %s phải tồn tại sau V2__sprint1_staff_availability.sql", table)
                    .isEqualTo(1);
        }

        for (String column : new String[]{"staff_id", "start_at", "end_at", "buffer_end_at",
                "price_snapshot", "currency"}) {
            assertThat(columnCount("booking", column))
                    .as("booking.%s phải tồn tại sau V2", column)
                    .isEqualTo(1);
        }

        for (String column : new String[]{"buffer_minutes", "currency", "active"}) {
            assertThat(columnCount("services", column))
                    .as("services.%s phải tồn tại sau V2", column)
                    .isEqualTo(1);
        }
    }

    @Test
    void sprint1MigrationSeedsDefaultBranch() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branch WHERE active = TRUE", Integer.class);
        assertThat(count).isEqualTo(1);

        String timezone = jdbcTemplate.queryForObject(
                "SELECT timezone FROM branch WHERE id = 1", String.class);
        assertThat(timezone).isEqualTo("Europe/Berlin");

        String open = jdbcTemplate.queryForObject("SELECT open_time FROM branch WHERE id = 1", String.class);
        String close = jdbcTemplate.queryForObject("SELECT close_time FROM branch WHERE id = 1", String.class);
        assertThat(open).startsWith("09:00");
        assertThat(close).startsWith("18:00");
    }
}
