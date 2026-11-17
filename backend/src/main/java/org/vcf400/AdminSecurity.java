package org.vcf400;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSecurity {
    private final Settings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AdminSecurity(Settings settings) { this(settings, Clock.systemUTC()); }
    AdminSecurity(Settings settings, Clock clock) { this.settings = settings; this.clock = clock; }

    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derive(password, salt));
    }

    private byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("認証初期化失敗", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verify(String password) {
        String stored = settings.findById("ADMPSWRD").map(s -> s.VALUE).orElse("");
        if (!stored.startsWith("pbkdf2$")) return false;
        try {
            String[] parts = stored.split("\\$");
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), derive(password, Base64.getDecoder().decode(parts[1])));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return false;
        }
    }

    public String issue() {
        Instant now = clock.instant();
        tokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, now.plusSeconds(600));
        return token;
    }

    public void require(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        Instant expires = tokens.get(token);
        if (expires == null || !expires.isAfter(clock.instant())) {
            tokens.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public void revokeAll() { tokens.clear(); }
}
