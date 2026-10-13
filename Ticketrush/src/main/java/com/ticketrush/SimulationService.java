package com.ticketrush;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    static final int MISSED = 0, VALID = 1, OVERSOLD = 2;

    /** One drop at a time, so overlapping requests cannot distort each other's timings. */
    public synchronized SimulationResult run(Strategy strategy, int stock, int bots) {
        TicketStrategy shop = strategy.create(stock);
        int[] outcomes = new int[bots];
        AtomicInteger issued = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(bots, 100));
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < bots; i++) {
                final int id = i;
                futures.add(pool.submit((Callable<Void>) () -> {
                    startGun.await();                       // every bot waits for the same signal
                    if (shop.tryBuy()) {
                        outcomes[id] = issued.incrementAndGet() > stock ? OVERSOLD : VALID;
                    }
                    return null;
                }));
            }
            long start = System.nanoTime();
            startGun.countDown();                           // GO
            for (Future<Void> f : futures) f.get();
            long millis = (System.nanoTime() - start) / 1_000_000;

            int total = issued.get();
            return new SimulationResult(strategy.name(), stock, bots, total,
                    Math.max(0, total - stock), millis, outcomes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Simulation failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
