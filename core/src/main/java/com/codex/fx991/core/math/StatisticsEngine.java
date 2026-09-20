package com.codex.fx991.core.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Statistical and regression functions specified by the CN CW Statistics application. */
public final class StatisticsEngine {
    private StatisticsEngine() {}

    public enum RegressionType {
        LINEAR, QUADRATIC, LOGARITHMIC, E_EXPONENTIAL, AB_EXPONENTIAL, POWER, INVERSE
    }

    public static OneVariableResults oneVariable(double[] x) {
        double[] frequency = new double[x.length];
        Arrays.fill(frequency, 1.0);
        return oneVariable(x, frequency);
    }

    public static OneVariableResults oneVariable(double[] x, double[] frequency) {
        validateSeries(x, frequency);
        double n = sum(frequency);
        if (n <= 0.0) throw new IllegalArgumentException("No samples");
        double sumX = 0.0, sumX2 = 0.0;
        List<WeightedValue> sorted = new ArrayList<>();
        for (int i = 0; i < x.length; i++) {
            requireFrequency(frequency[i]);
            sumX = com.codex.fx991.core.Compat.multiplyAdd(x[i], frequency[i], sumX);
            sumX2 = com.codex.fx991.core.Compat.multiplyAdd(
                    x[i] * x[i], frequency[i], sumX2);
            if (frequency[i] > 0.0) sorted.add(new WeightedValue(x[i], frequency[i]));
        }
        sorted.sort(Comparator.comparingDouble(WeightedValue::value));
        CenteredSeries centered = centeredSeries(x, frequency, n);
        double mean = centered.mean();
        double populationVariance = centered.sumSquares() / n;
        double sampleVariance = n > 1.0 ? populationVariance * n / (n - 1.0) : Double.NaN;
        return new OneVariableResults(
                n, sumX, sumX2, mean,
                populationVariance, Math.sqrt(populationVariance),
                sampleVariance, Math.sqrt(sampleVariance),
                sorted.get(0).value(), weightedQuantile(sorted, n, 0.25),
                weightedQuantile(sorted, n, 0.5), weightedQuantile(sorted, n, 0.75),
                sorted.get(sorted.size() - 1).value());
    }

    public static TwoVariableResults twoVariable(double[] x, double[] y) {
        double[] frequency = new double[x.length];
        Arrays.fill(frequency, 1.0);
        return twoVariable(x, y, frequency);
    }

    public static TwoVariableResults twoVariable(double[] x, double[] y, double[] frequency) {
        if (x.length != y.length) throw new IllegalArgumentException("x/y length differs");
        validateSeries(x, frequency);
        validateValues(y);
        double n = sum(frequency);
        if (n <= 0.0) throw new IllegalArgumentException("No samples");
        double sx = 0, sy = 0, sx2 = 0, sy2 = 0, sxy = 0, sx3 = 0, sx2y = 0, sx4 = 0;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < x.length; i++) {
            requireFrequency(frequency[i]);
            double f = frequency[i];
            sx = com.codex.fx991.core.Compat.multiplyAdd(x[i], f, sx);
            sy = com.codex.fx991.core.Compat.multiplyAdd(y[i], f, sy);
            sx2 = com.codex.fx991.core.Compat.multiplyAdd(x[i] * x[i], f, sx2);
            sy2 = com.codex.fx991.core.Compat.multiplyAdd(y[i] * y[i], f, sy2);
            sxy = com.codex.fx991.core.Compat.multiplyAdd(x[i] * y[i], f, sxy);
            sx3 = com.codex.fx991.core.Compat.multiplyAdd(x[i] * x[i] * x[i], f, sx3);
            sx2y = com.codex.fx991.core.Compat.multiplyAdd(x[i] * x[i] * y[i], f, sx2y);
            sx4 = com.codex.fx991.core.Compat.multiplyAdd(
                    x[i] * x[i] * x[i] * x[i], f, sx4);
            if (f > 0.0) {
                minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
                minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
            }
        }
        CenteredSeries centeredX = centeredSeries(x, frequency, n);
        CenteredSeries centeredY = centeredSeries(y, frequency, n);
        double meanX = centeredX.mean(), meanY = centeredY.mean();
        double pvx = centeredX.sumSquares() / n;
        double pvy = centeredY.sumSquares() / n;
        double svx = n > 1 ? pvx * n / (n - 1) : Double.NaN;
        double svy = n > 1 ? pvy * n / (n - 1) : Double.NaN;
        return new TwoVariableResults(n, sx, sy, sx2, sy2, sxy, sx3, sx2y, sx4,
                meanX, meanY, pvx, pvy, Math.sqrt(pvx), Math.sqrt(pvy),
                svx, svy, Math.sqrt(svx), Math.sqrt(svy), minX, maxX, minY, maxY);
    }

    public static RegressionResult regression(
            RegressionType type, double[] x, double[] y, double[] frequency) {
        if (x.length != y.length) throw new IllegalArgumentException("x/y length differs");
        validateSeries(x, frequency);
        validateValues(y);
        double count = 0.0;
        for (double value : frequency) {
            requireFrequency(value);
            count += value;
        }
        if (count <= 0.0) throw new IllegalArgumentException("No samples");
        return switch (type) {
            case LINEAR -> linearFit(x, y, frequency, type);
            case QUADRATIC -> quadraticFit(x, y, frequency);
            case LOGARITHMIC -> transformedFit(x, y, frequency, type, Transform.LOG_X);
            case E_EXPONENTIAL -> transformedFit(x, y, frequency, type, Transform.LOG_Y_E);
            case AB_EXPONENTIAL -> transformedFit(x, y, frequency, type, Transform.LOG_Y_AB);
            case POWER -> transformedFit(x, y, frequency, type, Transform.LOG_X_LOG_Y);
            case INVERSE -> transformedFit(x, y, frequency, type, Transform.INVERSE_X);
        };
    }

    public static RegressionResult regression(RegressionType type, double[] x, double[] y) {
        double[] frequency = new double[x.length];
        Arrays.fill(frequency, 1.0);
        return regression(type, x, y, frequency);
    }

    private static RegressionResult linearFit(
            double[] x, double[] y, double[] frequency, RegressionType type) {
        double n = sum(frequency);
        CenteredSeries xSeries = centeredSeries(x, frequency, n);
        CenteredSeries ySeries = centeredSeries(y, frequency, n);
        double centeredX = xSeries.sumSquares(), centeredY = ySeries.sumSquares();
        double centeredXY = 0.0;
        for (int i = 0; i < x.length; i++) {
            double f = frequency[i];
            if (f == 0.0) continue;
            centeredXY += xSeries.center(x[i]) * ySeries.center(y[i]) * f;
        }
        if (centeredX == 0.0 || !Double.isFinite(centeredX)
                || !Double.isFinite(centeredY) || !Double.isFinite(centeredXY)) {
            throw new ArithmeticException("Regression is singular or outside range");
        }
        double slope = centeredXY / centeredX;
        double intercept = ySeries.mean() - slope * xSeries.mean();
        double correlation = centeredY <= 0.0 ? Double.NaN
                : (centeredXY / Math.sqrt(centeredX)) / Math.sqrt(centeredY);
        if (Double.isFinite(correlation)) correlation = Math.max(-1.0, Math.min(1.0, correlation));
        if (!Double.isFinite(slope) || !Double.isFinite(intercept)) {
            throw new ArithmeticException("Regression range");
        }
        return new RegressionResult(type, slope, intercept, Double.NaN, correlation);
    }

    /** Shift before summation so large offsets do not erase small variations. */
    private static CenteredSeries centeredSeries(double[] values, double[] frequency, double n) {
        int first = 0;
        while (first < frequency.length && frequency[first] == 0.0) first++;
        if (first == frequency.length || !Double.isFinite(n)) throw new ArithmeticException("Sample range");
        double origin = values[first];
        double offset = 0.0;
        for (int i = 0; i < values.length; i++) {
            if (frequency[i] != 0.0) offset += (values[i] - origin) * (frequency[i] / n);
        }
        double squares = 0.0;
        for (int i = 0; i < values.length; i++) {
            if (frequency[i] == 0.0) continue;
            double centered = (values[i] - origin) - offset;
            squares += centered * centered * frequency[i];
        }
        if (!Double.isFinite(offset) || !Double.isFinite(squares)) throw new ArithmeticException("Statistics range");
        return new CenteredSeries(origin, offset, squares);
    }

    private record CenteredSeries(double origin, double offset, double sumSquares) {
        double mean() { return origin + offset; }
        double center(double value) { return (value - origin) - offset; }
    }

    private static RegressionResult quadraticFit(double[] x, double[] y, double[] frequency) {
        return QuadraticRegression.fit(x, y, frequency);
    }

    private enum Transform { LOG_X, LOG_Y_E, LOG_Y_AB, LOG_X_LOG_Y, INVERSE_X }

    private static RegressionResult transformedFit(
            double[] x, double[] y, double[] frequency, RegressionType type, Transform transform) {
        double[] tx = new double[x.length];
        double[] ty = new double[y.length];
        for (int i = 0; i < x.length; i++) {
            switch (transform) {
                case LOG_X -> { requirePositive(x[i]); tx[i] = Math.log(x[i]); ty[i] = y[i]; }
                case LOG_Y_E, LOG_Y_AB -> { requirePositive(y[i]); tx[i] = x[i]; ty[i] = Math.log(y[i]); }
                case LOG_X_LOG_Y -> {
                    requirePositive(x[i]); requirePositive(y[i]);
                    tx[i] = Math.log(x[i]); ty[i] = Math.log(y[i]);
                }
                case INVERSE_X -> { if (x[i] == 0.0) throw new ArithmeticException("x=0"); tx[i] = 1.0 / x[i]; ty[i] = y[i]; }
            }
        }
        RegressionResult fit = linearFit(tx, ty, frequency, type);
        return switch (transform) {
            case LOG_X, INVERSE_X -> new RegressionResult(type, fit.b(), fit.a(), Double.NaN, fit.r());
            case LOG_Y_E -> new RegressionResult(type, Math.exp(fit.b()), fit.a(), Double.NaN, fit.r());
            case LOG_Y_AB -> new RegressionResult(type, Math.exp(fit.b()), Math.exp(fit.a()), Double.NaN, fit.r());
            case LOG_X_LOG_Y -> new RegressionResult(type, Math.exp(fit.b()), fit.a(), Double.NaN, fit.r());
        };
    }

    public record OneVariableResults(
            double n, double sumX, double sumX2, double mean,
            double populationVariance, double populationStdDev,
            double sampleVariance, double sampleStdDev,
            double min, double q1, double median, double q3, double max) {}

    /**
     * Immutable two-variable statistics result carrier.
     *
     * Kept as an ordinary class rather than a large Java record because R8 8.x
     * can reject javac's generated record stack-map metadata when many double
     * components are present. The public constructor and record-style accessor
     * names intentionally remain unchanged for source compatibility.
     */
    public static final class TwoVariableResults {
        private final double n;
        private final double sumX;
        private final double sumY;
        private final double sumX2;
        private final double sumY2;
        private final double sumXY;
        private final double sumX3;
        private final double sumX2Y;
        private final double sumX4;
        private final double meanX;
        private final double meanY;
        private final double populationVarianceX;
        private final double populationVarianceY;
        private final double populationStdDevX;
        private final double populationStdDevY;
        private final double sampleVarianceX;
        private final double sampleVarianceY;
        private final double sampleStdDevX;
        private final double sampleStdDevY;
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;

        public TwoVariableResults(
                double n, double sumX, double sumY, double sumX2, double sumY2,
                double sumXY, double sumX3, double sumX2Y, double sumX4,
                double meanX, double meanY,
                double populationVarianceX, double populationVarianceY,
                double populationStdDevX, double populationStdDevY,
                double sampleVarianceX, double sampleVarianceY,
                double sampleStdDevX, double sampleStdDevY,
                double minX, double maxX, double minY, double maxY) {
            this.n = n;
            this.sumX = sumX;
            this.sumY = sumY;
            this.sumX2 = sumX2;
            this.sumY2 = sumY2;
            this.sumXY = sumXY;
            this.sumX3 = sumX3;
            this.sumX2Y = sumX2Y;
            this.sumX4 = sumX4;
            this.meanX = meanX;
            this.meanY = meanY;
            this.populationVarianceX = populationVarianceX;
            this.populationVarianceY = populationVarianceY;
            this.populationStdDevX = populationStdDevX;
            this.populationStdDevY = populationStdDevY;
            this.sampleVarianceX = sampleVarianceX;
            this.sampleVarianceY = sampleVarianceY;
            this.sampleStdDevX = sampleStdDevX;
            this.sampleStdDevY = sampleStdDevY;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        public double n() { return n; }
        public double sumX() { return sumX; }
        public double sumY() { return sumY; }
        public double sumX2() { return sumX2; }
        public double sumY2() { return sumY2; }
        public double sumXY() { return sumXY; }
        public double sumX3() { return sumX3; }
        public double sumX2Y() { return sumX2Y; }
        public double sumX4() { return sumX4; }
        public double meanX() { return meanX; }
        public double meanY() { return meanY; }
        public double populationVarianceX() { return populationVarianceX; }
        public double populationVarianceY() { return populationVarianceY; }
        public double populationStdDevX() { return populationStdDevX; }
        public double populationStdDevY() { return populationStdDevY; }
        public double sampleVarianceX() { return sampleVarianceX; }
        public double sampleVarianceY() { return sampleVarianceY; }
        public double sampleStdDevX() { return sampleStdDevX; }
        public double sampleStdDevY() { return sampleStdDevY; }
        public double minX() { return minX; }
        public double maxX() { return maxX; }
        public double minY() { return minY; }
        public double maxY() { return maxY; }
    }

    /**
     * Coefficient accessors and the five-argument constructor retain their API.
     * A fitted quadratic also retains immutable local coordinates for prediction;
     * rebuilding it from rounded display coefficients would lose that accuracy.
     */
    public static final class RegressionResult {
        private final RegressionType type;
        private final double a, b, c, r;
        private final QuadraticRegression quadraticModel;

        public RegressionResult(RegressionType type, double a, double b, double c, double r) {
            this(type, a, b, c, r, null);
        }

        RegressionResult(RegressionType type, double a, double b, double c, double r,
                         QuadraticRegression quadraticModel) {
            this.type = type; this.a = a; this.b = b; this.c = c; this.r = r;
            this.quadraticModel = quadraticModel;
        }

        public RegressionType type() { return type; }
        public double a() { return a; }
        public double b() { return b; }
        public double c() { return c; }
        public double r() { return r; }

        @Override public boolean equals(Object other) {
            return other instanceof RegressionResult value && type == value.type
                    && Double.compare(a, value.a) == 0 && Double.compare(b, value.b) == 0
                    && Double.compare(c, value.c) == 0 && Double.compare(r, value.r) == 0;
        }

        @Override public int hashCode() { return java.util.Objects.hash(type, a, b, c, r); }

        @Override public String toString() {
            return "RegressionResult[type=" + type + ", a=" + a + ", b=" + b + ", c=" + c + ", r=" + r + "]";
        }

        public double estimateY(double x) {
            return switch (type) {
                case LINEAR -> a * x + b;
                case QUADRATIC -> quadraticModel == null ? (a * x + b) * x + c : quadraticModel.estimateY(x);
                case LOGARITHMIC -> a + b * Math.log(x);
                case E_EXPONENTIAL -> a * Math.exp(b * x);
                case AB_EXPONENTIAL -> a * Math.pow(b, x);
                case POWER -> a * Math.pow(x, b);
                case INVERSE -> a + b / x;
            };
        }

        public double[] estimateX(double y) {
            return switch (type) {
                case LINEAR -> new double[] {(y - b) / a};
                case QUADRATIC -> {
                    yield quadraticModel == null ? QuadraticRegression.realRoots(a, b, c - y)
                            : quadraticModel.estimateX(y);
                }
                case LOGARITHMIC -> new double[] {Math.exp((y - a) / b)};
                case E_EXPONENTIAL -> new double[] {Math.log(y / a) / b};
                case AB_EXPONENTIAL -> new double[] {Math.log(y / a) / Math.log(b)};
                case POWER -> new double[] {Math.pow(y / a, 1.0 / b)};
                case INVERSE -> new double[] {b / (y - a)};
            };
        }
    }

    private record WeightedValue(double value, double frequency) {}

    private static double weightedQuantile(List<WeightedValue> sorted, double total, double quantile) {
        if (sorted.size() == 1) return sorted.get(0).value();
        double position = quantile * (total + 1.0);
        if (position <= 1.0) return sorted.get(0).value();
        if (position >= total) return sorted.get(sorted.size() - 1).value();
        double lowerIndex = Math.floor(position);
        double fraction = position - lowerIndex;
        double lower = weightedOrderStatistic(sorted, lowerIndex);
        double upper = weightedOrderStatistic(sorted, lowerIndex + 1.0);
        return lower + fraction * (upper - lower);
    }

    private static double weightedOrderStatistic(List<WeightedValue> sorted, double oneBasedIndex) {
        double cumulative = 0.0;
        for (WeightedValue value : sorted) {
            cumulative += value.frequency();
            if (cumulative >= oneBasedIndex) return value.value();
        }
        return sorted.get(sorted.size() - 1).value();
    }

    private static void validateSeries(double[] values, double[] frequency) {
        if (values == null || frequency == null || values.length == 0 || values.length != frequency.length) {
            throw new IllegalArgumentException("Invalid series");
        }
        validateValues(values);
    }

    private static void validateValues(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite sample");
        }
    }

    private static void requireFrequency(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value != Math.rint(value)) {
            throw new IllegalArgumentException("Frequency must be a non-negative integer");
        }
    }

    private static void requirePositive(double value) {
        if (!(value > 0.0)) throw new ArithmeticException("Expected positive value");
    }

    private static double sum(double[] values) {
        double result = 0.0;
        for (double value : values) result += value;
        return result;
    }
}
