package com.adi.naukri.orchestrator;

import com.adi.naukri.automation.*;
import com.adi.naukri.report.AccountStatus;
import com.adi.naukri.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.adi.naukri.api.AccountInput;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that JobOrchestrator.stop() immediately calls abort() on the running automator
 * and that the resulting job status is RUN_STOPPED (not FAILED) within a couple of seconds
 * — well before the 60-second fake sleep the blocking automator would otherwise take.
 *
 * Author: Adikarthik Gupta C B
 */
class JobOrchestratorAbortTest {

    @TempDir
    Path tempDir;

    private JobOrchestrator orchestrator;

    @AfterEach
    void tearDown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
            orchestrator = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-ABORT-1: stop() calls abort() on the automator and job becomes RUN_STOPPED
    //             well before the slow sleep finishes.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void stop_calls_abort_and_job_status_becomes_RUN_STOPPED_quickly() throws Exception {
        // Flags / latches
        AtomicBoolean abortCalled = new AtomicBoolean(false);
        CountDownLatch runnerStarted = new CountDownLatch(1);  // fires when fake automator is "running"
        CountDownLatch abortLatch   = new CountDownLatch(1);   // fires when abort() is called

        /**
         * Fake automator that:
         *  1. Signals it has started (runnerStarted).
         *  2. Blocks on a 60-second sleep — simulating a slow Playwright call.
         *  3. Implements abort() by interrupting the sleep and setting abortCalled.
         */
        Automator slowFake = new Automator() {
            private volatile Thread runnerThread;

            @Override
            public List<StepResult> run(
                                     String email,
                                      String name,
                                     String password,
                                     AutomationRunMode mode,
                                     AutomatorConfig cfg,
                                    PlaywrightSession session,
                                     ManualLoginGate gate,
                                    StepListener listener) {
                runnerThread = Thread.currentThread();
                runnerStarted.countDown();
                try {
                    Thread.sleep(60_000); // would block for a full minute without abort()
                } catch (InterruptedException e) {
                    // abort() interrupted us — restore interrupt flag and exit
                    Thread.currentThread().interrupt();
                }
                // Return a failed result so the orchestrator sees a non-OK status.
                return List.of(StepResult.failure(AutomationStep.LOGIN,
                        "STOPPED_BY_ABORT", 0L));
            }

            @Override
            public void abort() {
                abortCalled.set(true);
                abortLatch.countDown();
                Thread t = runnerThread;
                if (t != null) {
                    t.interrupt(); // simulate ctx.close() unblocking the blocked Playwright call
                }
            }
        };

        JobEventBus   bus    = new JobEventBus();
        ReportWriter  writer = new ReportWriter();
        RetryPolicy   policy = new RetryPolicy();

        orchestrator = new JobOrchestrator(
                slowFake, policy, writer, bus, () -> null, new RunRegistry());

        // Collect events
        List<JobEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch stopped = new CountDownLatch(1);
        bus.subscribe(null, e -> {
            events.add(e);
            if (e instanceof JobEvent.RunStopped) stopped.countDown();
        });

        JobRequest req = new JobRequest(
                 List.of(new AccountInput("slow@test.com", null)),
                 "pw",
                 false,
                 false,
                 tempDir.toString(),
                 null,
                 null
         );

        JobHandle handle = orchestrator.start(req);

        // Wait until the fake automator is running, THEN immediately call stop()
        assertTrue(runnerStarted.await(5, TimeUnit.SECONDS),
                "Fake automator never signalled it started");

        long stopCalledAt = System.currentTimeMillis();
        orchestrator.stop(handle.jobId());

        // abort() must have been called
        assertTrue(abortLatch.await(3, TimeUnit.SECONDS),
                "abort() was never called after stop()");
        assertTrue(abortCalled.get(), "abortCalled flag should be true");

        // RUN_STOPPED must arrive well within the 60-second sleep timeout
        assertTrue(stopped.await(10, TimeUnit.SECONDS),
                "RUN_STOPPED event not received within 10 seconds");

        long elapsed = System.currentTimeMillis() - stopCalledAt;
        assertTrue(elapsed < 15_000,
                "stop() took too long (" + elapsed + " ms); expected < 15 s");

        // Confirm the event sequence contains RUN_STOPPED (not only RUN_COMPLETED)
        assertTrue(events.stream().anyMatch(e -> e instanceof JobEvent.RunStopped),
                "Expected RunStopped event in stream");
        assertFalse(events.stream().anyMatch(e -> e instanceof JobEvent.RunCompleted),
                "Did not expect RunCompleted when run was stopped");

        handle.future().get(5, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-ABORT-2: abort() is idempotent when no run is in progress
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void abort_is_idempotent_when_no_run_in_progress() {
        // NaukriAutomator.abort() should not throw when called outside a run
        NaukriAutomator automator = new NaukriAutomator();
        assertDoesNotThrow(automator::abort, "abort() must not throw when idle");
        assertDoesNotThrow(automator::abort, "abort() must not throw on second call");
    }
}
