package com.clubbouncer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BouncerController {

    public record EnterResponse(boolean allowed, int remaining, long retryAfterMillis, String message) {}
    public record StatusResponse(int remaining, int limit, int windowSeconds) {}

    private static final List<String> WELCOME = List.of(
        "Welcome in.", "You're on the list.", "Enjoy the night.", "Go ahead, the music's good.");

    private final BouncerService bouncer;

    public BouncerController(BouncerService bouncer) { this.bouncer = bouncer; }

    @GetMapping("/enter")
    public ResponseEntity<EnterResponse> enter(@RequestParam Algorithm algo,
                                               @RequestParam(defaultValue = "guest") String guest) {
        Decision d = bouncer.enter(algo, clean(guest));
        if (d.allowed()) {
            String msg = WELCOME.get(ThreadLocalRandom.current().nextInt(WELCOME.size()));
            return ResponseEntity.ok(new EnterResponse(true, d.remaining(), 0, msg));
        }
        long seconds = Math.max(1, (long) Math.ceil(d.retryAfterMillis() / 1000.0));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
            .body(new EnterResponse(false, 0, d.retryAfterMillis(), "Not tonight. Back of the line."));
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam Algorithm algo, @RequestParam(defaultValue = "guest") String guest) {
        return new StatusResponse(bouncer.remaining(algo, clean(guest)), bouncer.limit(), bouncer.windowSeconds());
    }

    /** Guest names come from the browser, so keep them short and plain. */
    private static String clean(String guest) {
        String g = guest.replaceAll("[^A-Za-z0-9_-]", "");
        if (g.length() > 24) g = g.substring(0, 24);
        return g.isEmpty() ? "anonymous" : g;
    }
}
