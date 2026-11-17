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
import static org.mockito.ArgumentMatchers.any;

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
    @MockitoSpyBean Votes votes;
    @MockitoSpyBean Settings settings;
    @Autowired VisitorService visitors;

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

    @Test void closeWaitsForVoteThatAlreadyReadOpenSetting() throws Exception {
        String admin = (String) controller.auth(new AuthInput(PASSWORD)).get("token");
        CountDownLatch checked = new CountDownLatch(1), resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            checked.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(votes).existsById(1002);
        Running<Map<String, Object>> vote = start(() -> controller.vote(new VoteInput(1002, "EXH001", 1), "MM2024"));
        Running<Map<String, Object>> close = null;
        try {
            assertTrue(checked.await(5, TimeUnit.SECONDS));
            close = start(() -> controller.update("Bearer " + admin, new SettingsInput("N", null)));
            assertBlockedOn(close.thread(), visitors);
            assertEquals("Y", settings.findById("ALWVOTE").orElseThrow().VALUE);
        } finally {
            resume.countDown();
            vote.result().get(10, TimeUnit.SECONDS);
            if (close != null) close.result().get(10, TimeUnit.SECONDS);
        }
        assertEquals(true, vote.result().get().get("saved"));
        assertEquals("N", settings.findById("ALWVOTE").orElseThrow().VALUE);
        long countAtClose = votes.count();
        assertEquals(2, countAtClose);
        assertEquals(false, controller.vote(new VoteInput(1003, "EXH001", 1), "MM2024").get("saved"));
        assertEquals(countAtClose, votes.count());
    }

    @Test void voteWaitsForCloseAndDoesNotPersistAfterIt() throws Exception {
        String admin = (String) controller.auth(new AuthInput(PASSWORD)).get("token");
        CountDownLatch closing = new CountDownLatch(1), resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            closing.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(settings).saveAndFlush(any(org.vcf400.Models.Setting.class));
        Running<Map<String, Object>> close = start(() -> controller.update("Bearer " + admin, new SettingsInput("N", null)));
        Running<Map<String, Object>> vote = null;
        try {
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            vote = start(() -> controller.vote(new VoteInput(1002, "EXH001", 1), "MM2024"));
            assertBlockedOn(vote.thread(), visitors);
        } finally {
            resume.countDown();
            close.result().get(10, TimeUnit.SECONDS);
            if (vote != null) vote.result().get(10, TimeUnit.SECONDS);
        }
        assertEquals(false, vote.result().get().get("saved"));
        assertEquals("ENDOFCON", vote.result().get().get("screen"));
        assertEquals(1, votes.count());
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
