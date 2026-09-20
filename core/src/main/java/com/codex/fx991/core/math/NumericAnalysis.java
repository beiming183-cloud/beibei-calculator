package com.codex.fx991.core.math;

import java.util.function.DoubleUnaryOperator;

/** Independent numerical analysis functions used by derivative, integral, sum, and SOLVE. */
public final class NumericAnalysis {
    private static final double[] NODES = {0.9914553711208126, 0.9491079123427585, 0.8648644233597691,
            0.7415311855993945, 0.5860872354676911, 0.4058451513773972,
            0.2077849550078985, 0.0};
    private static final double[] KRONROD_WEIGHTS = {0.02293532201052922, 0.06309209262997855,
            0.1047900103222502, 0.1406532597155259, 0.1690047266392679,
            0.1903505780647854, 0.2044329400752989, 0.2094821410847278};
    private static final double[] GAUSS_WEIGHTS = {0.1294849661688697, 0.2797053914892767,
            0.3818300505051189, 0.4179591836734694};

    private NumericAnalysis() {}

    public static double derivative(DoubleUnaryOperator function, double x, double tolerance) {
        try (CalculationBudget.Scope budget = CalculationBudget.open()) {
            return derivative(function, x, tolerance, budget);
        }
    }

    private static double derivative(DoubleUnaryOperator function, double x, double tolerance,
                                     CalculationBudget.Scope budget) {
        requireTolerance(tolerance);
        double scale = Math.max(1.0, Math.abs(x));
        double h = Math.cbrt(Math.ulp(scale)) * scale;
        if (h == 0.0 || !Double.isFinite(h)) h = 1e-5 * scale;
        double previous = Double.NaN;
        for (int iteration = 0; iteration < 12; iteration++) {
            double current = (budget.evaluate(function, x + h) - budget.evaluate(function, x - h)) / (2.0 * h);
            if (Double.isFinite(previous)
                    && Math.abs(current - previous) <= tolerance * Math.max(1.0, Math.abs(current))) {
                return budget.finish(current);
            }
            previous = current;
            h *= 0.5;
        }
        if (!Double.isFinite(previous)) {
            throw new CalculationException(CalculationError.TIMEOUT,
                    "Derivative did not converge", 0);
        }
        return budget.finish(previous);
    }

    /** Adaptive Gauss-Kronrod 7/15 integration. */
    public static double integrate(
            DoubleUnaryOperator function, double lower, double upper, double tolerance) {
        try (CalculationBudget.Scope budget = CalculationBudget.open()) {
            requireTolerance(tolerance);
            if (lower == upper) return budget.finish(0.0);
            if (lower > upper) {
                return budget.finish(-integrateSegment(function, upper, lower, tolerance, 0, budget));
            }
            return budget.finish(integrateSegment(function, lower, upper, tolerance, 0, budget));
        }
    }

    private static double integrateSegment(
            DoubleUnaryOperator function, double lower, double upper, double tolerance, int depth,
            CalculationBudget.Scope budget) {
        budget.checkpoint();
        QuadratureEstimate estimate = gaussKronrod15(function, lower, upper, budget);
        if (!Double.isFinite(estimate.kronrod())) throw new ArithmeticException("Non-finite integral");
        double allowed = tolerance * Math.max(1.0, Math.abs(estimate.kronrod()));
        if (estimate.error() <= allowed) return estimate.kronrod();
        if (depth >= 20) {
            throw new CalculationException(CalculationError.TIMEOUT,
                    "Integral did not converge", 0);
        }
        double middle = (lower + upper) * 0.5;
        return integrateSegment(function, lower, middle, tolerance * 0.5, depth + 1, budget)
                + integrateSegment(function, middle, upper, tolerance * 0.5, depth + 1, budget);
    }

    private static QuadratureEstimate gaussKronrod15(
            DoubleUnaryOperator function, double lower, double upper,
            CalculationBudget.Scope budget) {
        double center = (lower + upper) * 0.5;
        double half = (upper - lower) * 0.5;
        double centerValue = budget.evaluate(function, center);
        double kronrod = KRONROD_WEIGHTS[7] * centerValue;
        double gauss = GAUSS_WEIGHTS[3] * centerValue;
        for (int i = 0; i < 7; i++) {
            double offset = half * NODES[i];
            double pair = budget.evaluate(function, center - offset) + budget.evaluate(function, center + offset);
            kronrod += KRONROD_WEIGHTS[i] * pair;
            if (i == 1) gauss += GAUSS_WEIGHTS[0] * pair;
            else if (i == 3) gauss += GAUSS_WEIGHTS[1] * pair;
            else if (i == 5) gauss += GAUSS_WEIGHTS[2] * pair;
        }
        kronrod *= half;
        gauss *= half;
        return new QuadratureEstimate(kronrod, Math.abs(kronrod - gauss));
    }

    public static double sum(DoubleUnaryOperator function, long start, long end) {
        try (CalculationBudget.Scope budget = CalculationBudget.open()) {
            return sum(function, start, end, budget);
        }
    }

    private static double sum(DoubleUnaryOperator function, long start, long end,
                              CalculationBudget.Scope budget) {
        if (start > end) throw new IllegalArgumentException("start > end");
        if (start <= -10_000_000_000L || end >= 10_000_000_000L) {
            throw new IllegalArgumentException("Sum bounds outside manual range");
        }
        budget.ensureCapacity(end - start + 1);
        double result = 0.0;
        double compensation = 0.0;
        for (long value = start; value <= end; value++) {
            double term = budget.evaluate(function, value) - compensation;
            double next = result + term;
            compensation = (next - result) - term;
            result = next;
            if (value == Long.MAX_VALUE) break;
        }
        return budget.finish(result);
    }

    public static SolveResult solve(
            DoubleUnaryOperator function, double initialValue, double tolerance, int maxIterations) {
        try (CalculationBudget.Scope budget = CalculationBudget.open()) {
            return solve(function, initialValue, tolerance, maxIterations, budget);
        }
    }

    private static SolveResult solve(DoubleUnaryOperator function, double initialValue,
                                     double tolerance, int maxIterations, CalculationBudget.Scope budget) {
        requireTolerance(tolerance);
        double x = initialValue;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            double fx = budget.evaluate(function, x);
            if (!Double.isFinite(fx)) throw new ArithmeticException("Non-finite function value");
            if (Math.abs(fx) <= tolerance) return new SolveResult(x, fx, iteration + 1, true);
            double derivative = derivative(function, x, Math.max(tolerance * 0.1, 1e-12));
            if (!Double.isFinite(derivative) || Math.abs(derivative) < 1e-15) {
                double step = Math.max(1e-6, Math.abs(x) * 1e-4);
                double nextFx = budget.evaluate(function, x + step);
                derivative = (nextFx - fx) / step;
                if (Math.abs(derivative) < 1e-15) break;
            }
            double next = x - fx / derivative;
            if (!Double.isFinite(next)) break;
            if (Math.abs(next - x) <= tolerance * Math.max(1.0, Math.abs(next))) {
                x = next;
                double remainder = budget.evaluate(function, x);
                return new SolveResult(x, remainder, iteration + 1, Math.abs(remainder) <= Math.sqrt(tolerance));
            }
            x = next;
        }
        double remainder = budget.evaluate(function, x);
        return new SolveResult(x, remainder, maxIterations, false);
    }

    private static void requireTolerance(double tolerance) {
        if (!(tolerance >= 1e-22) || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("Tolerance must be finite and >= 1e-22");
        }
    }

    private record QuadratureEstimate(double kronrod, double error) {}
    public record SolveResult(double solution, double remainder, int iterations, boolean converged) {}
}
