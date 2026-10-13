package com.ticketrush;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) { this.service = service; }

    @PostMapping("/simulate")
    public SimulationResult simulate(@RequestParam Strategy strategy,
                                     @RequestParam(defaultValue = "50") int stock,
                                     @RequestParam(defaultValue = "500") int bots) {
        if (stock < 1 || stock > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must be between 1 and 1000");
        if (bots < 1 || bots > 5000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bots must be between 1 and 5000");
        return service.run(strategy, stock, bots);
    }
}
