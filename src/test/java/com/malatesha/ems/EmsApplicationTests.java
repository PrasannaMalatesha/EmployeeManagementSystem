package com.malatesha.ems;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full application against a real Postgres via Testcontainers and
 * verifies Flyway migrations apply cleanly. The most valuable test in M0 —
 * if this passes, the wiring is sound end to end.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EmsApplicationTests {

    @Test
    void contextLoads() {
    }
}
