package com.codex.fx991.core.math;

/**
 * Classifies a numerically near-real simple root using an exact sign bracket.
 * Presentation must never decide whether a small component is mathematically zero.
 */
final class PolynomialRootRefiner {
    private PolynomialRootRefiner() { }

    static ComplexValue refine(Rational[] coefficients, ComplexValue[] roots, int index) {
        CalculationBudget.checkpoint();
        ComplexValue candidate = roots[index];
        if (candidate.imaginary() == 0.0) return candidate;
        double center = candidate.real();
        double radius = Math.max(64.0 * Math.ulp(center), 2.0 * Math.abs(candidate.imaginary()));
        if (!Double.isFinite(radius) || radius > 1e-8 * Math.max(1.0, Math.abs(center))) return candidate;
        // Do not collapse a conjugate pair or two nearby candidate roots into one.
        for (int other = 0; other < roots.length; other++) {
            if (other != index && Math.abs(roots[other].real() - center) <= 4.0 * radius) return candidate;
        }
        double lower = center - radius, upper = center + radius;
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower >= upper) return candidate;
        Rational lo = decimal(lower), hi = decimal(upper);
        // Strict derivative sign proves at most one real root in the bracket.
        // Opposite endpoint signs prove at least one. Both tests use rational
        // arithmetic, so x^2 + epsilon cannot acquire a false real root by rounding.
        if (!strictlyMonotone(coefficients, lo, hi)) return candidate;
        int lowerSign = sign(coefficients, lo), upperSign = sign(coefficients, hi);
        if (lowerSign == 0) return new ComplexValue(lower, 0.0);
        if (upperSign == 0) return new ComplexValue(upper, 0.0);
        if (lowerSign == upperSign) return candidate;
        for (int iteration = 0; iteration < 96; iteration++) {
            CalculationBudget.checkpoint();
            double middle = lower + (upper - lower) * 0.5;
            if (middle == lower || middle == upper) break;
            int middleSign = sign(coefficients, decimal(middle));
            if (middleSign == 0) return new ComplexValue(middle, 0.0);
            if (middleSign == lowerSign) lower = middle;
            else upper = middle;
        }
        return new ComplexValue(lower + (upper - lower) * 0.5, 0.0);
    }

    private static Rational decimal(double value) { return Rational.parseDecimal(Double.toString(value)); }

    private static int sign(Rational[] coefficients, Rational value) {
        Rational result = Rational.ZERO;
        for (Rational coefficient : coefficients) result = result.multiply(value).add(coefficient);
        return result.numerator().signum();
    }

    private static boolean strictlyMonotone(Rational[] coefficients, Rational lo, Rational hi) {
        int degree = coefficients.length - 1;
        Rational lower = coefficients[0].multiply(new Rational(degree));
        Rational upper = lower;
        for (int i = 1; i < degree; i++) {
            CalculationBudget.checkpoint();
            Rational a = lower.multiply(lo), b = lower.multiply(hi);
            Rational c = upper.multiply(lo), d = upper.multiply(hi);
            Rational coefficient = coefficients[i].multiply(new Rational(degree - i));
            lower = min(min(a, b), min(c, d)).add(coefficient);
            upper = max(max(a, b), max(c, d)).add(coefficient);
        }
        return lower.numerator().signum() > 0 || upper.numerator().signum() < 0;
    }

    private static Rational min(Rational a, Rational b) { return a.compareTo(b) <= 0 ? a : b; }
    private static Rational max(Rational a, Rational b) { return a.compareTo(b) >= 0 ? a : b; }
}
