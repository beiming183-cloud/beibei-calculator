package com.codex.fx991.core;

import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.CalculationBudget;
import com.codex.fx991.core.math.CalculationException;
import com.codex.fx991.core.math.NumericAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Resource limits must stop work, never silently return a less accurate answer. */
public final class NumericBudgetSuite {
    private int checks;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        new NumericBudgetSuite().run();
    }

    private void run() {
        test("already-canceled sum", this::alreadyCanceledSum);
        test("canceled during sum", this::canceledDuringSum);
        test("already-canceled integral", this::alreadyCanceledIntegral);
        test("already-canceled derivative", this::alreadyCanceledDerivative);
        test("already-canceled solve", this::alreadyCanceledSolve);
        test("canceled integral callback", this::canceledIntegralCallback);
        test("oversized sum is rejected before evaluating", this::oversizedSum);
        test("nested calls share resource allowance", this::nestedSum);
        test("sibling calls share root allowance", this::siblingCalls);
        test("wall time expiration is a controlled error", this::wallTimeExpiration);
        test("cancellation releases the actual single worker", this::workerCancellation);
        test("ordinary accuracy", this::ordinaryAccuracy);
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("PASS " + checks + " numeric budget checks");
    }

    private void alreadyCanceledSum() {
        AtomicInteger calls = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            expectCancellation(() -> NumericAnalysis.sum(x -> { calls.incrementAndGet(); return x; }, 1, 10000));
            check(calls.get() == 0, "canceled sum called function " + calls.get() + " times");
            check(Thread.currentThread().isInterrupted(), "cancellation must preserve interrupt flag");
        } finally { Thread.interrupted(); }
    }

    private void canceledDuringSum() {
        AtomicInteger calls = new AtomicInteger();
        try {
            expectCancellation(() -> NumericAnalysis.sum(x -> {
                if (calls.incrementAndGet() == 3) Thread.currentThread().interrupt();
                return x;
            }, 1, 10000));
            check(calls.get() == 3, "sum continued callbacks after interruption");
        } finally { Thread.interrupted(); }
    }

    private void alreadyCanceledIntegral() {
        Thread.currentThread().interrupt();
        try { expectCancellation(() -> NumericAnalysis.integrate(x -> x * x, 0, 1, 1e-10)); }
        finally { Thread.interrupted(); }
    }

    private void alreadyCanceledDerivative() {
        Thread.currentThread().interrupt();
        try { expectCancellation(() -> NumericAnalysis.derivative(x -> x * x, 3, 1e-10)); }
        finally { Thread.interrupted(); }
    }

    private void oversizedSum() {
        AtomicInteger calls = new AtomicInteger();
        expectTimeout(() -> NumericAnalysis.sum(x -> { calls.incrementAndGet(); return x; }, 1, 1_000_001));
        check(calls.get() == 0, "oversized sum must fail before spending callback work");
    }

    private void alreadyCanceledSolve() {
        Thread.currentThread().interrupt();
        try { expectCancellation(() -> NumericAnalysis.solve(x -> x * x - 4, 3, 1e-10, 100)); }
        finally { Thread.interrupted(); }
    }

    private void canceledIntegralCallback() {
        AtomicInteger calls = new AtomicInteger();
        try {
            expectCancellation(() -> NumericAnalysis.integrate(x -> {
                calls.incrementAndGet(); Thread.currentThread().interrupt(); return x;
            }, 0, 1, 1e-10));
            check(calls.get() == 1, "integral continued sampling after cancellation");
        } finally { Thread.interrupted(); }
    }

    private void siblingCalls() {
        AtomicInteger calls = new AtomicInteger();
        try (CalculationBudget.Scope ignored = CalculationBudget.open(10, 1_000_000_000)) {
            NumericAnalysis.sum(x -> { calls.incrementAndGet(); return x; }, 1, 6);
            expectTimeout(() -> NumericAnalysis.sum(x -> { calls.incrementAndGet(); return x; }, 1, 5));
            check(calls.get() == 6, "second sibling must not reset the work allowance");
        }
        near(15, NumericAnalysis.sum(x -> x, 1, 5), 0, "budget cleanup after timeout");
    }

    private void wallTimeExpiration() {
        expectTimeout(() -> {
            try (CalculationBudget.Scope ignored = CalculationBudget.open(100, 5_000_000)) {
                NumericAnalysis.sum(x -> {
                    long started = System.nanoTime();
                    long remaining;
                    while ((remaining = 20_000_000 - (System.nanoTime() - started)) > 0) {
                        LockSupport.parkNanos(remaining);
                    }
                    return x;
                }, 1, 1);
            }
        });
        near(3, NumericAnalysis.sum(x -> x, 1, 2), 0, "time budget cleaned up");
    }

    private void workerCancellation() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try {
            Future<?> first = worker.submit(() -> NumericAnalysis.sum(x -> {
                calls.incrementAndGet();
                if (x == 1) {
                    entered.countDown();
                    try { release.await(1, TimeUnit.SECONDS); }
                    catch (InterruptedException canceled) { Thread.currentThread().interrupt(); }
                }
                return x;
            }, 1, 10000));
            check(entered.await(1, TimeUnit.SECONDS), "worker did not start");
            first.cancel(true);
            release.countDown();
            Future<Double> next = worker.submit(() -> NumericAnalysis.sum(x -> x, 1, 3));
            near(6, next.get(1, TimeUnit.SECONDS), 0, "worker can calculate after canceled job");
            check(calls.get() == 1, "canceled worker evaluated " + calls.get() + " callbacks");
        } catch (Exception failure) {
            throw new AssertionError("cancel/next-task worker failed", failure);
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    private void nestedSum() {
        AtomicInteger calls = new AtomicInteger();
        expectTimeout(() -> NumericAnalysis.sum(x -> NumericAnalysis.sum(y -> {
            calls.incrementAndGet(); return x + y;
        }, 1, 1000), 1, 1001));
        check(calls.get() < 1_000_000, "nested sums reset their budget");
    }

    private void ordinaryAccuracy() {
        near(50005000, NumericAnalysis.sum(x -> x, 1, 10000), 0, "sum");
        near(6, NumericAnalysis.derivative(x -> x * x, 3, 1e-10), 1e-6, "derivative");
        near(2, NumericAnalysis.integrate(Math::sin, 0, Math.PI, 1e-10), 1e-9, "integral");
        near(-2, NumericAnalysis.integrate(Math::sin, Math.PI, 0, 1e-10), 1e-9, "reversed integral");
        near(4, NumericAnalysis.solve(x -> x * x - 16, 3, 1e-10, 100).solution(), 1e-8, "solve");
    }

    private void test(String name, Runnable test) {
        try { test.run(); }
        catch (AssertionError | RuntimeException error) { failures.add(name + ": " + error); }
        finally { Thread.interrupted(); }
    }

    private void expectCancellation(Runnable operation) {
        try { operation.run(); }
        catch (CancellationException expected) { checks++; return; }
        throw new AssertionError("expected cancellation, operation completed");
    }

    private void expectTimeout(Runnable operation) {
        try { operation.run(); }
        catch (CalculationException expected) {
            check(expected.error() == CalculationError.TIMEOUT, "expected controlled TIMEOUT");
            return;
        }
        throw new AssertionError("expected resource TIMEOUT, operation completed");
    }

    private void near(double expected, double actual, double tolerance, String name) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                name + ": expected " + expected + ", actual " + actual);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
