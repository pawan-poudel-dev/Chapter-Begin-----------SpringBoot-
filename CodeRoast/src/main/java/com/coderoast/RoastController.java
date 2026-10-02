package com.coderoast;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class RoastController {

    public record Request(String code) {}

    private final RoastEngine engine;

    public RoastController(RoastEngine engine) { this.engine = engine; }

    @PostMapping("/roast")
    public RoastResult roast(@RequestBody Request req) {
        if (req.code() == null || req.code().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paste some code first. I can't roast silence.");
        if (req.code().length() > 20_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too much code. Even I have limits (20,000 chars).");
        return engine.roast(req.code());
    }
}
