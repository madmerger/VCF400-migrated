package org.vcf400;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import org.vcf400.Requests.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class ApiConcurrencyTest {
    private static final String PASSWORD = UUID.randomUUID().toString().substring(0, 9);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("vcf.seed-admpswrd", () -> PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
    @Autowired ApiController controller;
    @Autowired BaselineSeeder seeder;
    @MockitoSpyBean AdminSecurity security;

    @BeforeEach void reset() { seeder.reset(seeder.baseline()); }

    @Test void passwordChangeCannotOvertakeVerifiedLoginAndLeaveItsTokenValid() throws Exception {
        String admin = (String) controller.auth(new AuthInput(PASSWORD)).get("token");
        String replacement = UUID.randomUUID().toString().substring(0, 9);
        CountDownLatch verified = new CountDownLatch(1), resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            boolean valid = (boolean) invocation.callRealMethod();
            verified.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));
            return valid;
        }).when(security).verify(PASSWORD);
        Running<Map<String, Object>> login = start(() -> controller.auth(new AuthInput(PASSWORD)));
        Running<Map<String, Object>> rotation = null;
        try {
            assertTrue(verified.await(5, TimeUnit.SECONDS));
            rotation = start(() -> controller.update("Bearer " + admin, new SettingsInput(null, replacement)));
            assertBlockedOn(rotation.thread(), security);
        } finally {
            resume.countDown();
            login.result().get(10, TimeUnit.SECONDS);
            if (rotation != null) rotation.result().get(10, TimeUnit.SECONDS);
        }
        String oldLoginToken = (String) login.result().get().get("token");
        assertNotNull(oldLoginToken);
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> security.require("Bearer " + oldLoginToken)).getStatusCode().value());
        assertFalse((boolean) controller.auth(new AuthInput(PASSWORD)).get("authenticated"));
        String newToken = (String) controller.auth(new AuthInput(replacement)).get("token");
        assertDoesNotThrow(() -> security.require("Bearer " + newToken));
    }

    private record Running<T>(Thread thread, FutureTask<T> result) {}

    private static <T> Running<T> start(Callable<T> action) {
        FutureTask<T> result = new FutureTask<>(action);
        Thread thread = new Thread(result);
        thread.start();
        return new Running<>(thread, result);
    }

    private static void assertBlockedOn(Thread thread, Object monitor) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
            assertNotNull(info);
            assertEquals(Thread.State.BLOCKED, info.getThreadState());
            assertNotNull(info.getLockInfo());
            assertEquals(System.identityHashCode(monitor), info.getLockInfo().getIdentityHashCode());
        });
    }
}
