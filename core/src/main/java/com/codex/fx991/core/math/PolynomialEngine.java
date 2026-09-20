package com.codex.fx991.core.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Degree 2..4 polynomial equation, extrema, and inequality engine. */
public final class PolynomialEngine {
    private PolynomialEngine() {}

    public enum Relation { GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL }

    /** Coefficients are ordered highest degree first. */
    public static List<ComplexValue> roots(double... coefficients) {
        CalculationBudget.checkpoint();
        double[] normalized = trimLeadingZeros(coefficients);
        int degree = normalized.length - 1;
        if (degree < 1 || degree > 4) throw new IllegalArgumentException("Degree must be 1..4");
        if (degree == 1) return com.codex.fx991.core.Compat.list(new ComplexValue(-normalized[1] / normalized[0], 0.0));
        if (normalized[degree] == 0.0) {
            List<ComplexValue> result = new ArrayList<>(roots(Arrays.copyOf(normalized, degree)));
            result.add(ComplexValue.ZERO);
            result.sort(Comparator.comparingDouble(ComplexValue::real).thenComparingDouble(ComplexValue::imaginary));
            return result;
        }
        if (degree == 2) return quadraticRoots(normalized[0], normalized[1], normalized[2]);

        // Degree is at most four: exact rational GCD against the derivative is
        // small and bounded, and separates repeated factors without mistaking
        // merely close roots for identical ones.
        Rational[] exact = new Rational[normalized.length];
        for (int i = 0; i < exact.length; i++) exact[i] = Rational.parseDecimal(Double.toString(normalized[i]));
        List<ComplexValue> repeated = repeatedFactorRoots(exact);
        if (repeated != null) return repeated;
        double leading = normalized[0];
        for (int i = 0; i < normalized.length; i++) {
            normalized[i] /= leading;
            if (!Double.isFinite(normalized[i])) throw new ArithmeticException("Polynomial range");
        }
        double radius = 1.0;
        for (int i = 1; i < normalized.length; i++) radius = Math.max(radius, 1.0 + Math.abs(normalized[i]));
        ComplexValue[] roots = new ComplexValue[degree];
        double phase = 0.37;
        for (int i = 0; i < degree; i++) {
            roots[i] = ComplexValue.polar(radius, phase + 2.0 * Math.PI * i / degree);
        }
        boolean converged = false;
        for (int iteration = 0; iteration < 300; iteration++) {
            CalculationBudget.checkpoint();
            double maxDelta = 0.0;
            ComplexValue[] next = Arrays.copyOf(roots, degree);
            for (int i = 0; i < degree; i++) {
                ComplexValue denominator = ComplexValue.ONE;
                for (int j = 0; j < degree; j++) if (i != j) denominator = denominator.multiply(roots[i].subtract(roots[j]));
                if (denominator.abs() < 1e-18) denominator = denominator.add(new ComplexValue(1e-12, 1e-12));
                ComplexValue delta = evaluate(normalized, roots[i]).divide(denominator);
                next[i] = roots[i].subtract(delta);
                maxDelta = Math.max(maxDelta, delta.abs() / Math.max(1.0, next[i].abs()));
            }
            roots = next;
            if (maxDelta < 1e-13) { converged = true; break; }
        }
        if (!converged) throw new CalculationException(CalculationError.TIMEOUT,
                "Polynomial roots did not converge", 0);
        List<ComplexValue> result = new ArrayList<>();
        for (int index = 0; index < roots.length; index++) {
            ComplexValue root = PolynomialRootRefiner.refine(exact, roots, index);
            requireRootResidual(normalized, root);
            result.add(root);
        }
        result.sort(Comparator.comparingDouble(ComplexValue::real)
                .thenComparingDouble(ComplexValue::imaginary));
        return result;
    }

    private static List<ComplexValue> repeatedFactorRoots(Rational[] polynomial) {
        CalculationBudget.checkpoint();
        if (polynomial.length <= 2) return null;
        Rational[] derivative = new Rational[polynomial.length - 1];
        for (int i = 0; i < derivative.length; i++) {
            derivative[i] = polynomial[i].multiply(new Rational(derivative.length - i));
        }
        Rational[] left = polynomial, right = derivative;
        while (right.length > 0) {
            CalculationBudget.checkpoint();
            Rational[] remainder = dividePolynomial(left, right)[1];
            left = right;
            right = remainder;
        }
        if (left.length <= 1) return null;
        Rational leading = left[0];
        Rational[] divisor = new Rational[left.length];
        for (int i = 0; i < divisor.length; i++) divisor[i] = left[i].divide(leading);
        Rational[] quotient = dividePolynomial(polynomial, divisor)[0];
        List<ComplexValue> result = new ArrayList<>();
        appendFactorRoots(result, quotient);
        appendFactorRoots(result, divisor);
        result.sort(Comparator.comparingDouble(ComplexValue::real).thenComparingDouble(ComplexValue::imaginary));
        return result;
    }

    private static void appendFactorRoots(List<ComplexValue> output, Rational[] factor) {
        List<ComplexValue> repeated = repeatedFactorRoots(factor);
        if (repeated != null) { output.addAll(repeated); return; }
        double[] coefficients = new double[factor.length];
        for (int i = 0; i < factor.length; i++) {
            coefficients[i] = new java.math.BigDecimal(factor[i].numerator())
                    .divide(new java.math.BigDecimal(factor[i].denominator()), java.math.MathContext.DECIMAL128)
                    .doubleValue();
        }
        output.addAll(roots(coefficients));
    }

    /** Returns the exact quotient and remainder in descending coefficient order. */
    private static Rational[][] dividePolynomial(Rational[] dividend, Rational[] divisor) {
        if (dividend.length < divisor.length) return new Rational[][] {new Rational[0], dividend};
        Rational[] work = dividend.clone();
        Rational[] quotient = new Rational[dividend.length - divisor.length + 1];
        for (int i = 0; i < quotient.length; i++) {
            CalculationBudget.checkpoint();
            quotient[i] = work[i].divide(divisor[0]);
            for (int j = 0; j < divisor.length; j++) {
                work[i + j] = work[i + j].subtract(quotient[i].multiply(divisor[j]));
            }
        }
        int first = 0;
        while (first < work.length && work[first].numerator().signum() == 0) first++;
        return new Rational[][] {quotient, Arrays.copyOfRange(work, first, work.length)};
    }

    private static void requireRootResidual(double[] coefficients, ComplexValue root) {
        double scale = 0.0;
        for (double coefficient : coefficients) scale = scale * root.abs() + Math.abs(coefficient);
        double residual = evaluate(coefficients, root).abs();
        if (!Double.isFinite(root.real()) || !Double.isFinite(root.imaginary())
                || !Double.isFinite(scale) || !Double.isFinite(residual)
                || residual > 1e-12 * scale) {
            throw new CalculationException(CalculationError.TIMEOUT, "Unreliable polynomial root", 0);
        }
    }

    private static List<ComplexValue> quadraticRoots(double a, double b, double c) {
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant >= 0.0) {
            double root = Math.sqrt(discriminant);
            double q = -0.5 * (b + Math.copySign(root, b));
            double first = q / a;
            double second = q == 0.0 ? -b / (2.0 * a) : c / q;
            List<ComplexValue> result = new ArrayList<>(com.codex.fx991.core.Compat.list(
                    new ComplexValue(first, 0.0), new ComplexValue(second, 0.0)));
            result.sort(Comparator.comparingDouble(ComplexValue::real));
            return result;
        }
        double real = -b / (2.0 * a);
        double imaginary = Math.sqrt(-discriminant) / (2.0 * Math.abs(a));
        return com.codex.fx991.core.Compat.list(new ComplexValue(real, -imaginary), new ComplexValue(real, imaginary));
    }

    public static List<Extremum> extrema(double... coefficients) {
        double[] normalized = trimLeadingZeros(coefficients);
        int degree = normalized.length - 1;
        if (degree < 2 || degree > 3) return com.codex.fx991.core.Compat.list();
        double[] derivative = new double[degree];
        for (int i = 0; i < degree; i++) derivative[i] = normalized[i] * (degree - i);
        List<Extremum> result = new ArrayList<>();
        for (ComplexValue root : roots(derivative)) {
            if (Math.abs(root.imaginary()) > 1e-9) continue;
            double x = root.real();
            double secondDerivative = secondDerivative(normalized, x);
            if (Math.abs(secondDerivative) < 1e-12) continue;
            result.add(new Extremum(x, evaluateReal(normalized, x), secondDerivative > 0.0));
        }
        result.sort(Comparator.comparingDouble(Extremum::x));
        return result;
    }

    public static InequalitySolution solveInequality(Relation relation, double... coefficients) {
        double[] normalized = trimLeadingZeros(coefficients);
        List<Double> boundaries = new ArrayList<>();
        for (ComplexValue root : roots(normalized)) {
            if (root.imaginary() == 0.0) {
                double value = root.real();
                if (boundaries.isEmpty() || !sameBoundary(value, boundaries.get(boundaries.size() - 1))) {
                    boundaries.add(value);
                }
            }
        }
        boundaries.sort(Double::compare);
        List<Interval> intervals = new ArrayList<>();
        for (int segment = 0; segment <= boundaries.size(); segment++) {
            double lower = segment == 0 ? Double.NEGATIVE_INFINITY : boundaries.get(segment - 1);
            double upper = segment == boundaries.size() ? Double.POSITIVE_INFINITY : boundaries.get(segment);
            double sample;
            if (!Double.isFinite(lower) && !Double.isFinite(upper)) sample = 0.0;
            else if (!Double.isFinite(lower)) sample = upper - Math.max(1.0, Math.abs(upper));
            else if (!Double.isFinite(upper)) sample = lower + Math.max(1.0, Math.abs(lower));
            else sample = lower + (upper - lower) * 0.5;
            if (test(relation, evaluateReal(normalized, sample))) {
                boolean inclusive = relation == Relation.GREATER_OR_EQUAL || relation == Relation.LESS_OR_EQUAL;
                boolean includeLower = Double.isFinite(lower) && inclusive;
                boolean includeUpper = Double.isFinite(upper) && inclusive;
                intervals.add(new Interval(lower, includeLower, upper, includeUpper));
            }
        }
        // Include isolated even-multiplicity roots for non-strict relations.
        if (relation == Relation.GREATER_OR_EQUAL || relation == Relation.LESS_OR_EQUAL) {
            for (double root : boundaries) {
                if (!contains(intervals, root)) {
                    intervals.add(new Interval(root, true, root, true));
                }
            }
        }
        intervals.sort(Comparator.comparingDouble(Interval::lower));
        return new InequalitySolution(intervals);
    }

    private static boolean sameBoundary(double left, double right) {
        return Math.abs(left - right) <= 8.0 * Math.max(Math.ulp(left), Math.ulp(right));
    }

    public static double evaluateReal(double[] coefficients, double x) {
        double result = 0.0;
        for (double coefficient : coefficients) {
            result = com.codex.fx991.core.Compat.multiplyAdd(result, x, coefficient);
        }
        return result;
    }

    private static ComplexValue evaluate(double[] coefficients, ComplexValue x) {
        ComplexValue result = ComplexValue.ZERO;
        for (double coefficient : coefficients) result = result.multiply(x).add(new ComplexValue(coefficient, 0.0));
        return result;
    }

    private static double secondDerivative(double[] coefficients, double x) {
        int degree = coefficients.length - 1;
        double result = 0.0;
        for (int i = 0; i < degree - 1; i++) {
            int exponent = degree - i;
            result = com.codex.fx991.core.Compat.multiplyAdd(
                    result, x, coefficients[i] * exponent * (exponent - 1));
        }
        return result;
    }

    private static boolean test(Relation relation, double value) {
        return switch (relation) {
            case GREATER -> value > 0.0;
            case LESS -> value < 0.0;
            case GREATER_OR_EQUAL -> value >= 0.0;
            case LESS_OR_EQUAL -> value <= 0.0;
        };
    }

    private static boolean contains(List<Interval> intervals, double value) {
        for (Interval interval : intervals) {
            boolean afterLower = value > interval.lower || (value == interval.lower && interval.includeLower);
            boolean beforeUpper = value < interval.upper || (value == interval.upper && interval.includeUpper);
            if (afterLower && beforeUpper) return true;
        }
        return false;
    }

    private static double[] trimLeadingZeros(double[] coefficients) {
        if (coefficients == null || coefficients.length < 2) throw new IllegalArgumentException("No polynomial");
        for (double coefficient : coefficients) {
            if (!Double.isFinite(coefficient)) throw new IllegalArgumentException("Non-finite coefficient");
        }
        int first = 0;
        while (first < coefficients.length - 1 && coefficients[first] == 0.0) first++;
        return Arrays.copyOfRange(coefficients, first, coefficients.length);
    }

    public record Extremum(double x, double y, boolean minimum) {}
    public record Interval(double lower, boolean includeLower, double upper, boolean includeUpper) {}
    public record InequalitySolution(List<Interval> intervals) {
        public InequalitySolution { intervals = com.codex.fx991.core.Compat.copyList(intervals); }
        public boolean isNoSolution() { return intervals.isEmpty(); }
        public boolean isAllReals() {
            return intervals.size() == 1
                    && intervals.get(0).lower() == Double.NEGATIVE_INFINITY
                    && intervals.get(0).upper() == Double.POSITIVE_INFINITY;
        }
    }
}
