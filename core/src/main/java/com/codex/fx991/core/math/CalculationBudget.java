package com.codex.fx991.core.math;

import java.util.concurrent.CancellationException;
import java.util.function.DoubleUnaryOperator;

/**
 * One thread-confined allowance for a calculation and all its nested numerical work.
 * Limits fail explicitly; they never relax a requested mathematical tolerance.
 */
public final class CalculationBudget {
    public static final long DEFAULT_MAX_EVALUATIONS = 1_000_000L;
    public static final long DEFAULT_TIMEOUT_NANOS = 2_000_000_000L;

    private static final ThreadLocal<Budget> CURRENT = new ThreadLocal<>();

    private CalculationBudget() { }

    /** Nested callers join the current allowance instead of resetting the clock. */
    public static Scope open() {
        return open(DEFAULT_MAX_EVALUATIONS, DEFAULT_TIMEOUT_NANOS);
    }

    /** Establishes root limits; nested scopes retain the existing calculation's limits. */
    public static Scope open(long maxEvaluations, long timeoutNanos) {
        if (maxEvaluations < 1 || timeoutNanos < 1) {
            throw new IllegalArgumentException("Calculation limits must be positive");
        }
        Budget existing = CURRENT.get();
        if (existing != null) {
            existing.checkpoint();
            return new Scope(existing, false);
        }
        Budget budget = new Budget(maxEvaluations, timeoutNanos);
        budget.checkpoint();
        CURRENT.set(budget);
        return new Scope(budget, true);
    }

    /** Can be used by other cooperative algorithms inside the same calculation. */
    public static void checkpoint() {
        checkInterrupted();
        Budget budget = CURRENT.get();
        if (budget != null) budget.checkpoint();
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Calculation canceled");
        }
    }

    private static CalculationException exhausted(String detail) {
        return new CalculationException(CalculationError.TIMEOUT, detail, 0);
    }

    private static final class Budget {
        private final long startedNanos = System.nanoTime();
        private final long timeoutNanos;
        private long remaining;

        private Budget(long maxEvaluations, long timeoutNanos) {
            this.remaining = maxEvaluations;
            this.timeoutNanos = timeoutNanos;
        }

        private void checkpoint() {
            checkInterrupted();
            if (System.nanoTime() - startedNanos >= timeoutNanos) {
                throw exhausted("Calculation time limit exceeded");
            }
        }

        private void ensureCapacity(long evaluations) {
            checkpoint();
            if (evaluations < 0 || evaluations > remaining) {
                throw exhausted("Calculation work limit exceeded");
            }
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Budget budget;
        private final boolean owner;
        private boolean closed;

        private Scope(Budget budget, boolean owner) {
            this.budget = budget;
            this.owner = owner;
        }

        public void checkpoint() { budget.checkpoint(); }

        void ensureCapacity(long evaluations) { budget.ensureCapacity(evaluations); }

        double evaluate(DoubleUnaryOperator function, double value) {
            budget.ensureCapacity(1);
            budget.remaining--;
            double result = function.applyAsDouble(value);
            // A callback may itself request cancellation or consume the deadline.
            budget.checkpoint();
            return result;
        }

        double finish(double result) {
            checkpoint();
            return result;
        }

        @Override public void close() {
            if (!closed && owner && CURRENT.get() == budget) CURRENT.remove();
            closed = true;
        }
    }
}
