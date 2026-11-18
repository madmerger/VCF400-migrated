package org.vcf400;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSecurityTest {
    @Test void tokenExpiresAndCanBeRevoked() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        AdminSecurity security = new AdminSecurity(mock(Settings.class), clock);
        String token = security.issue();
        assertDoesNotThrow(() -> security.require("Bearer " + token));
        when(clock.instant()).thenReturn(now.plusSeconds(600));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> security.require("Bearer " + token));
        String newToken = security.issue();
        security.revokeAll();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> security.require("Bearer " + newToken));
    }

    @Test void unsetPasswordDisablesAuthentication() {
        Settings settings = mock(Settings.class);
        when(settings.findById("ADMPSWRD")).thenReturn(Optional.empty());
        AdminSecurity security = new AdminSecurity(settings);
        assertFalse(security.verify(""));
        assertFalse(security.verify(java.util.UUID.randomUUID().toString()));
    }
}
