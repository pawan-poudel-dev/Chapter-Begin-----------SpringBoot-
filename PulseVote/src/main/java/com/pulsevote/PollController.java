package com.pulsevote;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    public record CreatePoll(String question, List<String> options) {}

    private final PollRepository repo;
    private final Map<Long, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public PollController(PollRepository repo) { this.repo = repo; }

    @PostMapping
    public Poll create(@RequestBody CreatePoll req) {
        if (req.question() == null || req.question().isBlank() || req.options() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question and options required");
        List<String> opts = req.options().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (opts.size() < 2 || opts.size() > 6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 2 to 6 options");
        Poll poll = new Poll(req.question().trim());
        opts.forEach(o -> poll.getOptions().add(new PollOption(o)));
        return repo.save(poll);
    }

    @GetMapping("/{id}")
    public Poll get(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{id}/vote/{optionId}")
    @Transactional
    public Poll vote(@PathVariable Long id, @PathVariable Long optionId) {
        Poll poll = get(id);
        poll.getOptions().stream().filter(o -> o.getId().equals(optionId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such option"))
            .addVote();
        repo.save(poll);
        broadcast(id, poll);
        return poll;
    }

    @GetMapping("/{id}/stream")
    public SseEmitter stream(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        List<SseEmitter> list = listeners.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    private void broadcast(Long id, Poll poll) {
        for (SseEmitter e : listeners.getOrDefault(id, List.of())) {
            try { e.send(SseEmitter.event().name("update").data(poll)); }
            catch (IOException | IllegalStateException ex) { listeners.get(id).remove(e); }
        }
    }
}
