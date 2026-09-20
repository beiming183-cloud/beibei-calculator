package com.codex.fx991.core.math;

/** Weighted quadratic least squares in scaled coordinates, without normal equations. */
final class QuadraticRegression {
    private final double center, scale, yOrigin, yScale;
    private final double quadratic, linear, constant;

    private QuadraticRegression(double center, double scale, double yOrigin, double yScale,
                                double quadratic, double linear, double constant) {
        this.center = center; this.scale = scale; this.yOrigin = yOrigin; this.yScale = yScale;
        this.quadratic = quadratic; this.linear = linear; this.constant = constant;
    }

    static StatisticsEngine.RegressionResult fit(double[] x, double[] y, double[] frequency) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, maxWeight = 0;
        int rows = 0, first = -1;
        for (int i = 0; i < x.length; i++) {
            CalculationBudget.checkpoint();
            if (frequency[i] == 0) continue;
            if (first < 0) first = i;
            rows++;
            min = Math.min(min, x[i]); max = Math.max(max, x[i]);
            maxWeight = Math.max(maxWeight, frequency[i]);
        }
        if (rows < 3 || min == max) throw new ArithmeticException("Quadratic regression is singular");
        double scale = (max - min) / 2, center = min + scale;
        finite(scale); finite(center);
        if (scale == 0) throw new ArithmeticException("Quadratic regression scale");
        double origin = y[first], yScale = 0;
        for (int i = 0; i < y.length; i++) {
            CalculationBudget.checkpoint();
            if (frequency[i] > 0) yScale = Math.max(yScale, Math.abs(y[i] - origin));
        }
        finite(yScale);
        if (yScale == 0) yScale = 1;
        double[][] design = new double[rows][3];
        double[] rhs = new double[rows];
        for (int i = 0, row = 0; i < x.length; i++) {
            CalculationBudget.checkpoint();
            if (frequency[i] == 0) continue;
            double weight = Math.sqrt(frequency[i] / maxWeight);
            if (weight == 0) throw new ArithmeticException("Quadratic weight range");
            double t = (x[i] - center) / scale;
            design[row][0] = t * t * weight;
            design[row][1] = t * weight;
            design[row][2] = weight;
            rhs[row] = ((y[i] - origin) / yScale) * weight;
            row++;
        }
        double[] coefficients = solveQr(design, rhs);
        QuadraticRegression model = new QuadraticRegression(center, scale, origin, yScale,
                coefficients[0], coefficients[1], coefficients[2]);
        double a = (model.quadratic * yScale / scale) / scale;
        double localSlope = model.linear * yScale / scale;
        double b = localSlope - 2 * a * center;
        double c = (origin + model.constant * yScale) - localSlope * center + (a * center) * center;
        finite(a); finite(b); finite(c);
        return new StatisticsEngine.RegressionResult(StatisticsEngine.RegressionType.QUADRATIC,
                a, b, c, Double.NaN, model);
    }

    /** Column-pivoted Householder QR, always three columns; loops cooperate with cancellation. */
    private static double[] solveQr(double[][] matrix, double[] rhs) {
        int rows = matrix.length;
        int[] order = {0, 1, 2};
        double referenceNorm = 0;
        for (int column = 0; column < 3; column++) referenceNorm = Math.max(referenceNorm, norm(matrix, 0, column));
        double threshold = referenceNorm * 64 * Math.ulp(1.0) * Math.max(1, Math.sqrt(rows));
        for (int k = 0; k < 3; k++) {
            int pivot = k;
            double largest = norm(matrix, k, k);
            for (int column = k + 1; column < 3; column++) {
                double candidate = norm(matrix, k, column);
                if (candidate > largest) { largest = candidate; pivot = column; }
            }
            if (largest <= threshold) throw new ArithmeticException("Quadratic regression is rank deficient");
            if (pivot != k) {
                int old = order[k]; order[k] = order[pivot]; order[pivot] = old;
                for (double[] row : matrix) {
                    CalculationBudget.checkpoint();
                    double value = row[k]; row[k] = row[pivot]; row[pivot] = value;
                }
            }
            double alpha = -Math.copySign(largest, matrix[k][k]);
            double[] reflector = new double[rows - k];
            reflector[0] = matrix[k][k] - alpha;
            double length = Math.abs(reflector[0]);
            for (int row = k + 1; row < rows; row++) {
                CalculationBudget.checkpoint();
                reflector[row-k] = matrix[row][k];
                length = Math.hypot(length, reflector[row-k]);
            }
            for (int i = 0; i < reflector.length; i++) reflector[i] /= length;
            for (int column = k; column < 3; column++) {
                double dot = 0;
                for (int row = k; row < rows; row++) {
                    CalculationBudget.checkpoint();
                    dot += reflector[row-k] * matrix[row][column];
                }
                for (int row = k; row < rows; row++) matrix[row][column] -= 2 * reflector[row-k] * dot;
            }
            double dot = 0;
            for (int row = k; row < rows; row++) dot += reflector[row-k] * rhs[row];
            for (int row = k; row < rows; row++) {
                CalculationBudget.checkpoint();
                rhs[row] -= 2 * reflector[row-k] * dot;
            }
            matrix[k][k] = alpha;
        }
        double[] pivoted = new double[3], result = new double[3];
        for (int row = 2; row >= 0; row--) {
            double value = rhs[row];
            for (int column = row + 1; column < 3; column++) value -= matrix[row][column] * pivoted[column];
            pivoted[row] = value / matrix[row][row];
            finite(pivoted[row]);
            result[order[row]] = pivoted[row];
        }
        return result;
    }

    private static double norm(double[][] matrix, int start, int column) {
        double result = 0;
        for (int row = start; row < matrix.length; row++) {
            CalculationBudget.checkpoint();
            result = Math.hypot(result, matrix[row][column]);
        }
        return result;
    }

    double estimateY(double x) {
        double t = (x - center) / scale;
        double value = yOrigin + yScale * ((quadratic * t + linear) * t + constant);
        finite(value);
        return value;
    }

    double[] estimateX(double y) {
        double target = (y - yOrigin) / yScale;
        finite(target);
        double[] roots = realRoots(quadratic, linear, constant - target);
        for (int i = 0; i < roots.length; i++) { roots[i] = center + scale * roots[i]; finite(roots[i]); }
        return roots;
    }

    static double[] realRoots(double a, double b, double c) {
        if (a == 0) {
            if (b == 0) {
                if (c == 0) throw new ArithmeticException("Indeterminate regression inverse");
                return new double[0];
            }
            double root = -c / b; finite(root); return new double[]{root};
        }
        double scale = Math.max(Math.abs(a), Math.max(Math.abs(b), Math.abs(c)));
        finite(scale);
        a /= scale; b /= scale; c /= scale;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return new double[0];
        double q = -0.5 * (b + Math.copySign(Math.sqrt(discriminant), b));
        if (q == 0) return new double[]{0, 0};
        double first = q / a, second = c / q;
        finite(first); finite(second);
        return b >= 0 ? new double[]{second, first} : new double[]{first, second};
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Quadratic regression range");
    }
}
