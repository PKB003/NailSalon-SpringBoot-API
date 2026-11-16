package com.ttnails.booking_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S0-04 — chạy Flyway trên DB trống (H2 MODE=MySQL) rồi kiểm tra đủ bảng.
 *
 * <p>Test này bắt lỗi cú pháp migration ngay trên CI/local mà không cần Docker.
 * Trước khi deploy staging nên chạy thêm `flyway:migrate` trên MySQL thật
 * (xem docs/db/rollback-V1.md).</p>
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

    @Test
    void migrationCreatesBaselineSchema() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class);
        assertThat(applied).isNotNull().isGreaterThanOrEqualTo(1);

        for (String table : new String[]{"user", "user_roles", "services", "booking", "invalid_token"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                    Integer.class, table);
            assertThat(count)
                    .as("bảng %s phải tồn tại sau V1__init.sql", table)
                    .isEqualTo(1);
        }
    }
}
