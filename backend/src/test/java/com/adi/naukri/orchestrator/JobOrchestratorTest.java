package com.adi.naukri.orchestrator;

import com.adi.naukri.automation.*;
import com.adi.naukri.report.AccountResult;
import com.adi.naukri.report.AccountStatus;
import com.adi.naukri.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.adi.naukri.api.AccountInput;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for JobOrchestrator.
 *
 * Uses a FAKE Automator — no real browser launched.
 *
 * Author: Adikarthik Gupta C B
 */
class JobOrchestratorTest {

    @TempDir
    Path tempDir;

    private JobEventBus   bus;
    private ReportWriter  reportWriter;
    private RetryPolicy   retryPolicy;
    private JobOrchestrator orchestrator;

    /** Fake automator that instantly returns all-OK step results. */
    private static Automator fakeOk() {
    return (email, name, password, mode, cfg, session, gate, listener) -> {
            List<StepResult> steps = new ArrayList<>();
            for (AutomationStep s : AutomationStep.values()) {
                listener.onStepStarted(s);
                StepResult r = StepResult.success(s, 10L);
                listener.onStep(r);
                steps.add(r);
            }
            return steps;
        };
    }

    /**
     * Fake automator that blocks on gate (simulates manualLogin=true).
     * The gate.waitForResume() call will block until the test signals it via
     * the supplied latch.
     */
    private static Automator fakeBlockingGate(CountDownLatch releaseSignal,
                                               CountDownLatch awaitingLatch) {
        return (email, name, password, mode, cfg, session, gate, listener) -> {
            // Signal that we're at the gate
            awaitingLatch.countDown();
            // Block until gate is released (test calls continueNow)
            gate.waitForResume(email, Duration.ofSeconds(30), () -> releaseSignal.getCount() == 0);
            // After gate unblocked, do remaining steps
            List<StepResult> steps = new ArrayList<>();
            listener.onStepStarted(AutomationStep.LOGIN);
            StepResult r = StepResult.success(AutomationStep.LOGIN, 5L);
            listener.onStep(r);
            steps.add(r);
            return steps;
        };
    }

    /**
     * Creates a fresh orchestrator with its own bus and assigns it to {@code this.orchestrator}.
     * Shuts down any previously assigned orchestrator first.
     */
    private void makeOrchestrator(Automator automator) throws Exception {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
        bus         = new JobEventBus();    // fresh bus per test
        orchestrator = new JobOrchestrator(
                automator, retryPolicy, reportWriter, bus, () -> null, new RunRegistry());
    }

    @BeforeEach
    void setUp() throws Exception {
        reportWriter = new ReportWriter();
        retryPolicy  = new RetryPolicy();
        makeOrchestrator(fakeOk());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (orchestrator != null) {
            orchestrator.shutdown();
            orchestrator = null;
        }
    }

    // -----------------------------------------------------------------------
    // TC-1: Three accounts, all OK → correct event sequence + report.csv
    // -----------------------------------------------------------------------
    @Test
    void threeAccountsAllOk_publishesFullEventSequenceAndWritesReport() throws Exception {
        List<AccountInput> emails = List.of(
        new AccountInput("A", "a@a.com"),
        new AccountInput("B", "b@b.com"),
        new AccountInput("C", "c@c.com"));
        JobRequest req = new JobRequest(
        emails, "secret", false, false,
        tempDir.toString(), null, null);

        List<JobEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        bus.subscribe(null, e -> {
            events.add(e);
            if (e instanceof JobEvent.RunCompleted) done.countDown();
        });

        JobHandle handle = orchestrator.start(req);
        assertTrue(done.await(10, TimeUnit.SECONDS), "RunCompleted not received in time");
        handle.future().get(2, TimeUnit.SECONDS);

        // RunStarted exists
        assertTrue(events.stream().anyMatch(e -> e instanceof JobEvent.RunStarted),
                "Expected RunStarted");
        // 3 AccountStarted events
        long accountStartedCount = events.stream()
                .filter(e -> e instanceof JobEvent.AccountStarted).count();
        assertEquals(3, accountStartedCount, "Expected 3 AccountStarted events");
        // 3 AccountCompleted(OK)
        long accountOkCount = events.stream()
                .filter(e -> e instanceof JobEvent.AccountCompleted ac
                        && ac.status() == AccountStatus.OK)
                .count();
        assertEquals(3, accountOkCount, "Expected 3 AccountCompleted(OK)");
        // RunCompleted
        assertTrue(events.stream().anyMatch(e -> e instanceof JobEvent.RunCompleted),
                "Expected RunCompleted");
        // RunCompleted summary
        JobEvent.RunCompleted rc = events.stream()
                .filter(e -> e instanceof JobEvent.RunCompleted)
                .map(e -> (JobEvent.RunCompleted) e)
                .findFirst().orElseThrow();
        assertEquals(3, rc.summary().ok());

        // C2 fix: STEP_STARTED events must be present (at least one per account × steps)
        long stepStartedCount = events.stream()
                .filter(e -> e instanceof JobEvent.StepStarted).count();
        assertTrue(stepStartedCount >= 3, "Expected at least one StepStarted per account, got " + stepStartedCount);

        // report.csv must exist somewhere under tempDir
        assertTrue(
            java.nio.file.Files.walk(tempDir)
                .anyMatch(p -> p.getFileName().toString().equals("report.csv")),
            "report.csv not found under output folder");
    }

    // -----------------------------------------------------------------------
    // TC-2: Stop mid-run → remaining accounts SKIPPED, RunStopped emitted
    // -----------------------------------------------------------------------
    @Test
    void stopMidRun_remainingAccountsSkippedAndRunStoppedEmitted() throws Exception {
        // firstAccountRunning: fired when a@a.com enters the automator (worker blocked here)
        CountDownLatch firstAccountRunning = new CountDownLatch(1);
        // releaseFirst: test releases it after calling stop()
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Automator blockingFake = (email, name, password, mode, cfg, session, gate, listener) -> {
            List<StepResult> steps = new ArrayList<>();
            listener.onStepStarted(AutomationStep.LOGIN);
            StepResult r = StepResult.success(AutomationStep.LOGIN, 5L);
            listener.onStep(r);
            steps.add(r);
            if (email.equals("a@a.com")) {
                firstAccountRunning.countDown();     // tell test we're inside account a
                try { releaseFirst.await(5, TimeUnit.SECONDS); }  // block until test calls stop()
                catch (InterruptedException ignored) {}
            }
            return steps;
        };

        // Fresh orchestrator + bus — no cross-contamination with other tests
        makeOrchestrator(blockingFake);

        List<JobEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch stopped = new CountDownLatch(1);
        bus.subscribe(null, e -> {
            events.add(e);
            if (e instanceof JobEvent.RunStopped) stopped.countDown();
        });

        List<AccountInput> accounts = List.of(
              new AccountInput("User1", "a@a.com"),
              new AccountInput("User2", "b@b.com"),
              new AccountInput("User3", "c@c.com")
             );

            JobRequest req = new JobRequest(
    accounts,
    "pwd",
    false,
    false,
    tempDir.toString(),
    null,      // resumeFolderPath
    null       // baseUrlOverride
);
        JobHandle handle = orchestrator.start(req);

        // Wait until worker is inside a@a.com, THEN call stop() before releasing
        assertTrue(firstAccountRunning.await(5, TimeUnit.SECONDS),
                "Worker never reached a@a.com");
        orchestrator.stop(handle.jobId());
        releaseFirst.countDown();   // now let a@a.com finish — next iteration will see stop flag

        assertTrue(stopped.await(5, TimeUnit.SECONDS), "RunStopped not emitted");

        // At least one SKIPPED account
        long skippedCount = events.stream()
                .filter(e -> e instanceof JobEvent.AccountCompleted ac
                        && ac.status() == AccountStatus.SKIPPED)
                .count();
        assertTrue(skippedCount >= 1, "Expected at least one SKIPPED, got " + skippedCount);
    }

    // -----------------------------------------------------------------------
    // TC-3: Gate blocks → AwaitManualLogin emitted; continueNow() resumes
    // -----------------------------------------------------------------------
    @Test
    void manualLoginGate_publishesAwaitManualLoginAndResumesOnContinueNow() throws Exception {
        CountDownLatch releaseSignal  = new CountDownLatch(1);
        CountDownLatch awaitingLatch  = new CountDownLatch(1);

        makeOrchestrator(fakeBlockingGate(releaseSignal, awaitingLatch));

        List<JobEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        bus.subscribe(null, e -> {
            events.add(e);
            if (e instanceof JobEvent.RunCompleted || e instanceof JobEvent.RunStopped)
                completed.countDown();
        });

        JobRequest req = new JobRequest(
        List.of(new AccountInput("Gated", "gated@x.com")), "pwd", false, true,
        tempDir.toString(), null, null);
        JobHandle handle = orchestrator.start(req);

        // Wait until the orchestrator publishes AwaitManualLogin
        assertTrue(awaitingLatch.await(5, TimeUnit.SECONDS),
                "Automator did not reach the gate");

        // Give bus a moment to emit the event
        Thread.sleep(200);
        assertTrue(events.stream().anyMatch(e -> e instanceof JobEvent.AwaitManualLogin),
                "Expected AwaitManualLogin event");

        // Now release the gate via continueNow
        releaseSignal.countDown();
        orchestrator.continueNow(handle.jobId());

        assertTrue(completed.await(10, TimeUnit.SECONDS),
                "Run did not complete after continueNow");
    }

    // -----------------------------------------------------------------------
    // TC-4: Concurrent second start() → throws IllegalStateException
    // -----------------------------------------------------------------------
    @Test
    void concurrentSecondStart_throwsIllegalStateException() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        Automator blockingFake = (email, name, password, mode, cfg, session, gate, listener) -> {
            try { blocker.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return List.of(StepResult.success(AutomationStep.LOGIN, 5L));
        };

        makeOrchestrator(blockingFake);

        JobRequest req = new JobRequest(
        List.of(new AccountInput("X", "x@x.com")), "pwd", false, false,
        tempDir.toString(), null, null);
        // Start first run — it will block on blocker latch
        orchestrator.start(req);

        // Second start must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> orchestrator.start(req),
                "Expected IllegalStateException for concurrent start");

        blocker.countDown(); // release to let worker thread clean up
    }

    // -----------------------------------------------------------------------
    // TC-5: Smoke — single email happy path
    // -----------------------------------------------------------------------
    @Test
    void singleEmailHappyPath_smoke() throws Exception {
        List<JobEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        bus.subscribe(null, e -> {
            events.add(e);
            if (e instanceof JobEvent.RunCompleted) done.countDown();
        });

        JobRequest req = new JobRequest(
        List.of(new AccountInput("Smoke", "smoke@test.com")), "pass", false, false,
        tempDir.toString(), null, null);
        JobHandle handle = orchestrator.start(req);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        handle.future().get(2, TimeUnit.SECONDS);

        assertTrue(events.stream().anyMatch(e -> e instanceof JobEvent.RunCompleted));
        JobEvent.RunCompleted rc = events.stream()
                .filter(e -> e instanceof JobEvent.RunCompleted)
                .map(e -> (JobEvent.RunCompleted) e)
                .findFirst().orElseThrow();
        assertEquals(1, rc.summary().ok());
    }
}
