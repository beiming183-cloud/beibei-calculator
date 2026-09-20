package com.codex.fx991.core.math;

import java.util.Objects;

/** Immutable complex value used by complex mode and polynomial roots. */
public final class ComplexValue {
    public static final ComplexValue ZERO = new ComplexValue(0.0, 0.0);
    public static final ComplexValue ONE = new ComplexValue(1.0, 0.0);
    public static final ComplexValue I = new ComplexValue(0.0, 1.0);

    private final double real;
    private final double imaginary;

    public ComplexValue(double real, double imaginary) {
        this.real = clean(real);
        this.imaginary = clean(imaginary);
    }

    public static ComplexValue polar(double radius, double angleRadians) {
        return new ComplexValue(radius * AngleTrig.cosRadians(angleRadians), radius * AngleTrig.sinRadians(angleRadians));
    }

    public double real() { return real; }
    public double imaginary() { return imaginary; }
    public double abs() { return Math.hypot(real, imaginary); }
    public double argument() { return Math.atan2(imaginary, real); }

    public ComplexValue add(ComplexValue other) {
        return new ComplexValue(real + other.real, imaginary + other.imaginary);
    }

    public ComplexValue subtract(ComplexValue other) {
        return new ComplexValue(real - other.real, imaginary - other.imaginary);
    }

    public ComplexValue multiply(ComplexValue other) {
        return new ComplexValue(
                real * other.real - imaginary * other.imaginary,
                real * other.imaginary + imaginary * other.real);
    }

    public ComplexValue multiply(double scalar) {
        return new ComplexValue(real * scalar, imaginary * scalar);
    }

    public ComplexValue divide(ComplexValue other) {
        double scale = Math.max(Math.abs(other.real), Math.abs(other.imaginary));
        if (scale == 0.0) throw new ArithmeticException("Division by zero");
        double scaledReal = other.real / scale;
        double scaledImaginary = other.imaginary / scale;
        double denominator = scaledReal * scaledReal + scaledImaginary * scaledImaginary;
        double leftReal = real / scale;
        double leftImaginary = imaginary / scale;
        return new ComplexValue(
                (leftReal * scaledReal + leftImaginary * scaledImaginary) / denominator,
                (leftImaginary * scaledReal - leftReal * scaledImaginary) / denominator);
    }

    public ComplexValue negate() { return new ComplexValue(-real, -imaginary); }
    public ComplexValue conjugate() { return new ComplexValue(real, -imaginary); }

    public ComplexValue exp() {
        double magnitude = Math.exp(real);
        return new ComplexValue(magnitude * AngleTrig.cosRadians(imaginary), magnitude * AngleTrig.sinRadians(imaginary));
    }

    public ComplexValue log() {
        if (real == 0.0 && imaginary == 0.0) throw new ArithmeticException("Logarithm of zero");
        return new ComplexValue(Math.log(abs()), argument());
    }

    public ComplexValue pow(int exponent) { return pow((long) exponent); }

    public ComplexValue pow(long exponent) {
        if (exponent == 0) {
            if (real == 0.0 && imaginary == 0.0) throw new ArithmeticException("Zero to zero power");
            return ONE;
        }
        if (exponent == Long.MIN_VALUE) {
            return ONE.divide(powPositive(Long.MAX_VALUE).multiply(this));
        }
        if (exponent < 0) return ONE.divide(powPositive(-exponent));
        return powPositive(exponent);
    }

    public ComplexValue pow(double exponent) {
        if (abs() == 0.0) {
            if (exponent > 0.0) return ZERO;
            throw new ArithmeticException("Zero to non-positive power");
        }
        if (exponent == 0.5) return sqrt();
        if (exponent == -0.5) return ONE.divide(sqrt());
        return log().multiply(exponent).exp();
    }

    public ComplexValue sqrt() {
        if (imaginary == 0.0 && real >= 0.0) return new ComplexValue(Math.sqrt(real), 0.0);
        double magnitude = abs();
        // Compute the larger component first; |z| - |Re(z)| loses the smaller
        // component entirely for inputs such as 1 + 1e-10 i.
        double larger = Math.sqrt(magnitude * 0.5 + Math.abs(real) * 0.5);
        double realPart = real >= 0.0 ? larger : Math.abs(imaginary) / (2.0 * larger);
        double imaginaryPart = real >= 0.0 ? imaginary / (2.0 * larger)
                : Math.copySign(larger, imaginary);
        return new ComplexValue(realPart, imaginaryPart);
    }

    private ComplexValue powPositive(long exponent) {
        ComplexValue result = ONE;
        ComplexValue factor = this;
        long remaining = exponent;
        while (remaining > 0) {
            if ((remaining & 1L) != 0L) result = result.multiply(factor);
            factor = factor.multiply(factor);
            remaining >>>= 1;
        }
        return result;
    }

    public boolean approximatelyEquals(ComplexValue other, double tolerance) {
        return subtract(other).abs() <= tolerance * Math.max(1.0, Math.max(abs(), other.abs()));
    }

    private static double clean(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    @Override public boolean equals(Object object) {
        if (!(object instanceof ComplexValue other)) return false;
        return Double.compare(real, other.real) == 0
                && Double.compare(imaginary, other.imaginary) == 0;
    }

    @Override public int hashCode() { return Objects.hash(real, imaginary); }

    @Override public String toString() {
        if (imaginary == 0.0) return Double.toString(real);
        if (real == 0.0) return imaginary + "i";
        return real + (imaginary < 0 ? "" : "+") + imaginary + "i";
    }
}
