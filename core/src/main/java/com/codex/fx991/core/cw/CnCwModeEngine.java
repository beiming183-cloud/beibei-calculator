package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.ComplexValue;
import com.codex.fx991.core.math.DistributionEngine;
import com.codex.fx991.core.math.FunctionTableEngine;
import com.codex.fx991.core.math.MatrixValue;
import com.codex.fx991.core.math.NumericAnalysis;
import com.codex.fx991.core.math.PolynomialEngine;
import com.codex.fx991.core.math.RatioEngine;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.StatisticsEngine;
import com.codex.fx991.core.math.VectorValue;
import com.codex.fx991.core.mode.ApplicationMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Text-field bridge from application landing commands to the independent
 * numerical engines. It gives every structured application a deterministic,
 * testable calculation path while dedicated table/coefficient editors are
 * developed separately.
 *
 * <p>Arguments are comma separated at top level. Commas inside parentheses
 * remain part of a scalar expression.</p>
 */
public final class CnCwModeEngine {
    private CnCwModeEngine() { }

    public enum ResultLayout {
        TEXT,
        KEY_VALUE,
        VECTOR,
        MATRIX,
        TABLE
    }

    /** One ordered label/value entry in a structured application result. */
    public static final class ResultItem {
        private final String label;
        private final String value;

        public ResultItem(String label, String value) {
            if (com.codex.fx991.core.Compat.isBlank(label)) {
                throw new IllegalArgumentException("label");
            }
            if (value == null) throw new IllegalArgumentException("value");
            this.label = label;
            this.value = value;
        }

        public String label() { return label; }
        public String value() { return value; }
    }

    /**
     * Core-owned application result protocol.
     *
     * <p>`display()` and `primaryValue()` remain source-compatible with the
     * Stage 2/3 bridge.  New renderers can instead consume layout/title/items
     * without parsing presentation strings.</p>
     */
    public static final class ModeResult {
        private final String display;
        private final Double primaryValue;
        private final ResultLayout layout;
        private final String title;
        private final List<ResultItem> items;
        /** Optional row-major grid payload used by MATRIX/VECTOR/TABLE layouts. */
        private final int rows;
        private final int columns;
        private final List<String> cells;

        public ModeResult(String display, Double primaryValue) {
            this(display, primaryValue, ResultLayout.TEXT, "",
                    com.codex.fx991.core.Compat.list());
        }

        public ModeResult(String display, Double primaryValue,
                          ResultLayout layout, String title, List<ResultItem> items) {
            this(display, primaryValue, layout, title, items,
                    0, 0, com.codex.fx991.core.Compat.list());
        }

        public ModeResult(String display, Double primaryValue,
                          ResultLayout layout, String title, List<ResultItem> items,
                          int rows, int columns, List<String> cells) {
            if (com.codex.fx991.core.Compat.isBlank(display)) {
                throw new IllegalArgumentException("display");
            }
            if (layout == null) throw new IllegalArgumentException("layout");
            if (items == null) throw new IllegalArgumentException("items");
            if (cells == null) throw new IllegalArgumentException("cells");
            if (rows < 0 || columns < 0) throw new IllegalArgumentException("grid size");
            if ((rows == 0) != (columns == 0)) throw new IllegalArgumentException("grid shape");
            if (rows > 0 && cells.size() != rows * columns) {
                throw new IllegalArgumentException("grid cells");
            }
            if (rows == 0 && !cells.isEmpty()) throw new IllegalArgumentException("grid cells");
            this.display = display;
            this.primaryValue = primaryValue;
            this.layout = layout;
            this.title = title == null ? "" : title;
            this.items = com.codex.fx991.core.Compat.copyList(items);
            this.rows = rows;
            this.columns = columns;
            this.cells = com.codex.fx991.core.Compat.copyList(cells);
        }

        public static ModeResult keyValue(String title, String display, Double primaryValue,
                                          ResultItem... entries) {
            List<ResultItem> items = new ArrayList<>();
            if (entries != null) {
                for (ResultItem entry : entries) {
                    if (entry == null) throw new IllegalArgumentException("entry");
                    items.add(entry);
                }
            }
            return new ModeResult(display, primaryValue, ResultLayout.KEY_VALUE,
                    title, items);
        }

        public static ModeResult grid(ResultLayout layout, String title, String display,
                                      Double primaryValue, int rows, int columns,
                                      List<String> cells, List<ResultItem> items) {
            if (layout != ResultLayout.MATRIX && layout != ResultLayout.VECTOR
                    && layout != ResultLayout.TABLE) {
                throw new IllegalArgumentException("grid layout");
            }
            return new ModeResult(display, primaryValue, layout, title, items,
                    rows, columns, cells);
        }

        public String display() { return display; }
        public Double primaryValue() { return primaryValue; }
        public ResultLayout layout() { return layout; }
        public String title() { return title; }
        public List<ResultItem> items() { return items; }
        public int rows() { return rows; }
        public int columns() { return columns; }
        public List<String> cells() { return cells; }
        public boolean hasGrid() { return rows > 0; }
    }

    private static ResultItem item(String label, double value) {
        return new ResultItem(label, format(value));
    }

    private static ResultItem item(String label, String value) {
        return new ResultItem(label, value);
    }

    public static ModeResult evaluate(ApplicationMode mode,
                                      String commandId,
                                      String source,
                                      ScalarExpressionEngine.EvaluationContext context) {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (com.codex.fx991.core.Compat.isBlank(commandId)) throw new IllegalArgumentException("commandId");
        List<String> fields = splitTopLevel(source);
        return switch (mode) {
            case STATISTICS -> statistics(commandId, fields, context);
            case DISTRIBUTION -> distribution(commandId, fields, context);
            case FUNCTION_TABLE -> functionTable(commandId, fields, context);
            case EQUATION -> equation(commandId, fields, context);
            case INEQUALITY -> inequality(fields, context);
            case MATRIX -> matrix(fields, context);
            case VECTOR -> vector(fields, context);
            case RATIO -> ratio(commandId, fields, context);
            default -> throw new IllegalArgumentException("No structured workflow for " + mode);
        };
    }

    public static List<String> splitTopLevel(String source) {
        if (com.codex.fx991.core.Compat.isBlank(source)) throw new IllegalArgumentException("Empty input");
        List<String> fields = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (value == ',' && depth == 0) {
                fields.add(requireField(source.substring(start, index)));
                start = index + 1;
            }
            if (depth < 0) throw new IllegalArgumentException("Unbalanced parentheses");
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced parentheses");
        fields.add(requireField(source.substring(start)));
        return com.codex.fx991.core.Compat.copyList(fields);
    }

    private static ModeResult statistics(String command,
                                         List<String> fields,
                                         ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (command.equals("one") || command.equals("one-freq")) {
            double[] x;
            double[] frequency = null;
            if (command.equals("one-freq")) {
                if (values.length < 2 || values.length % 2 != 0) {
                    throw new IllegalArgumentException("Enter x1,f1,x2,f2,...");
                }
                x = new double[values.length / 2];
                frequency = new double[x.length];
                for (int i = 0; i < x.length; i++) {
                    x[i] = values[i * 2];
                    frequency[i] = values[i * 2 + 1];
                }
            } else {
                x = values;
            }
            StatisticsEngine.OneVariableResults result = frequency == null
                    ? StatisticsEngine.oneVariable(x) : StatisticsEngine.oneVariable(x, frequency);
            String display = "n=" + format(result.n()) + "  x̄=" + format(result.mean())
                    + "\nσx=" + format(result.populationStdDev())
                    + "  sx=" + format(result.sampleStdDev());
            return ModeResult.keyValue(command.equals("one-freq") ? "一元统计（频数）" : "一元统计",
                    display, result.mean(),
                    item("n", result.n()),
                    item("x̄", result.mean()),
                    item("σx", result.populationStdDev()),
                    item("sx", result.sampleStdDev()));
        }

        boolean frequencyMode = command.endsWith("-freq") || command.equals("two-freq");
        String baseCommand = command.endsWith("-freq")
                ? command.substring(0, command.length() - "-freq".length()) : command;
        int stride = frequencyMode ? 3 : 2;
        if (values.length < stride * 2 || values.length % stride != 0) {
            throw new IllegalArgumentException(frequencyMode
                    ? "Enter x1,y1,f1,x2,y2,f2,..." : "Enter x1,y1,x2,y2,...");
        }
        int rows = values.length / stride;
        double[] x = new double[rows];
        double[] y = new double[rows];
        double[] frequency = frequencyMode ? new double[rows] : null;
        for (int i = 0; i < rows; i++) {
            x[i] = values[i * stride];
            y[i] = values[i * stride + 1];
            if (frequencyMode) frequency[i] = values[i * stride + 2];
        }

        StatisticsEngine.RegressionType regressionType = regressionType(baseCommand);
        if (regressionType != null) {
            StatisticsEngine.RegressionResult fit = frequencyMode
                    ? StatisticsEngine.regression(regressionType, x, y, frequency)
                    : StatisticsEngine.regression(regressionType, x, y);
            String title = regressionTitle(regressionType) + (frequencyMode ? "（频数）" : "");
            if (regressionType == StatisticsEngine.RegressionType.QUADRATIC) {
                String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                        + "\nc=" + format(fit.c());
                return ModeResult.keyValue(title, display, fit.a(),
                        item("a", fit.a()), item("b", fit.b()), item("c", fit.c()));
            }
            String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                    + "\nr=" + format(fit.r());
            return ModeResult.keyValue(title, display, fit.r(),
                    item("a", fit.a()), item("b", fit.b()), item("r", fit.r()));
        }
        if (!baseCommand.equals("two")) throw new IllegalArgumentException("Unknown statistics command");
        StatisticsEngine.TwoVariableResults result = frequencyMode
                ? StatisticsEngine.twoVariable(x, y, frequency) : StatisticsEngine.twoVariable(x, y);
        String display = "x̄=" + format(result.meanX()) + "  ȳ=" + format(result.meanY())
                + "\nσx=" + format(result.populationStdDevX())
                + "  σy=" + format(result.populationStdDevY());
        return ModeResult.keyValue(frequencyMode ? "双变量统计（频数）" : "双变量统计",
                display, result.meanX(),
                item("x̄", result.meanX()),
                item("ȳ", result.meanY()),
                item("σx", result.populationStdDevX()),
                item("σy", result.populationStdDevY()));
    }

    private static StatisticsEngine.RegressionType regressionType(String command) {
        return switch (command) {
            case "regression", "reg-linear" -> StatisticsEngine.RegressionType.LINEAR;
            case "reg-quadratic" -> StatisticsEngine.RegressionType.QUADRATIC;
            case "reg-logarithmic" -> StatisticsEngine.RegressionType.LOGARITHMIC;
            case "reg-e-exponential" -> StatisticsEngine.RegressionType.E_EXPONENTIAL;
            case "reg-ab-exponential" -> StatisticsEngine.RegressionType.AB_EXPONENTIAL;
            case "reg-power" -> StatisticsEngine.RegressionType.POWER;
            case "reg-inverse" -> StatisticsEngine.RegressionType.INVERSE;
            default -> null;
        };
    }

    private static String regressionTitle(StatisticsEngine.RegressionType type) {
        return switch (type) {
            case LINEAR -> "线性回归";
            case QUADRATIC -> "二次回归";
            case LOGARITHMIC -> "对数回归";
            case E_EXPONENTIAL -> "e 指数回归";
            case AB_EXPONENTIAL -> "ab^x 回归";
            case POWER -> "幂回归";
            case INVERSE -> "逆数回归";
        };
    }

    private static ModeResult distribution(String command,
                                           List<String> fields,
                                           ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        double result;
        String label;
        switch (command) {
            case "normal" -> {
                if (values.length == 3) {
                    result = DistributionEngine.normalPdf(values[0], values[1], values[2]);
                    label = "Normal PDF";
                } else if (values.length == 4) {
                    result = DistributionEngine.normalCdf(values[0], values[1], values[2], values[3]);
                    label = "Normal CDF";
                } else throw new IllegalArgumentException("PDF: x,μ,σ; CDF: lower,upper,μ,σ");
            }
            case "binomial" -> {
                requireCount(values, 3, "x,n,p");
                result = DistributionEngine.binomialPmf(integer(values[0]), integer(values[1]), values[2]);
                label = "Binomial PMF";
            }
            case "poisson" -> {
                requireCount(values, 2, "x,λ");
                result = DistributionEngine.poissonPmf(integer(values[0]), values[1]);
                label = "Poisson PMF";
            }
            default -> throw new IllegalArgumentException("Unknown distribution");
        }
        return new ModeResult(label + "\n" + format(result), result);
    }

    private static ModeResult functionTable(String command,
                                            List<String> fields,
                                            ScalarExpressionEngine.EvaluationContext context) {
        boolean twoFunctions = command.equals("fg");
        int required = twoFunctions ? 5 : 4;
        if (fields.size() != required) {
            throw new IllegalArgumentException(twoFunctions
                    ? "Enter f(x),g(x),start,end,step" : "Enter f(x),start,end,step");
        }
        ScalarExpressionEngine.CompiledExpression f = ScalarExpressionEngine.compile(fields.get(0));
        ScalarExpressionEngine.CompiledExpression g = twoFunctions
                ? ScalarExpressionEngine.compile(fields.get(1)) : null;
        int offset = twoFunctions ? 2 : 1;
        double startValue = scalar(fields.get(offset), context);
        double endValue = scalar(fields.get(offset + 1), context);
        double step = scalar(fields.get(offset + 2), context);
        List<FunctionTableEngine.Row> rows = FunctionTableEngine.generate(f, g,
                twoFunctions ? FunctionTableEngine.TableType.F_AND_G
                        : FunctionTableEngine.TableType.F_ONLY,
                startValue, endValue, step, context);
        FunctionTableEngine.Row first = rows.get(0);
        FunctionTableEngine.Row last = rows.get(rows.size() - 1);
        String firstText = format(first.x()) + ":" + format(first.f());
        String lastText = format(last.x()) + ":" + format(last.f());
        if (twoFunctions) {
            firstText += "," + format(first.g());
            lastText += "," + format(last.g());
        }
        String display = "rows=" + rows.size() + "  " + firstText + "\n… " + lastText;
        List<String> cells = new ArrayList<>();
        for (FunctionTableEngine.Row row : rows) {
            cells.add(format(row.x()));
            cells.add(format(row.f()));
            if (twoFunctions) cells.add(format(row.g()));
        }
        List<ResultItem> headings = new ArrayList<>();
        headings.add(item("x", "x"));
        headings.add(item("f(x)", "f(x)"));
        if (twoFunctions) headings.add(item("g(x)", "g(x)"));
        return ModeResult.grid(ResultLayout.TABLE, twoFunctions ? "函数表 f,g" : "函数表 f",
                display, first.f(), rows.size(), twoFunctions ? 3 : 2, cells, headings);
    }

    private static ModeResult equation(String command,
                                       List<String> fields,
                                       ScalarExpressionEngine.EvaluationContext context) {
        if (command.equals("polynomial")) {
            double[] coefficients = evaluateFields(fields, 0, context);
            List<ComplexValue> roots = PolynomialEngine.roots(coefficients);
            StringBuilder text = new StringBuilder();
            List<ResultItem> items = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                String label = "x" + (i + 1);
                String value = formatComplex(roots.get(i));
                if (i > 0) text.append(i == 1 ? "\n" : "  ");
                text.append(label).append("=").append(value);
                items.add(item(label, value));
            }
            return new ModeResult(text.toString(), roots.get(0).real(),
                    ResultLayout.KEY_VALUE, "多项式方程", items);
        }
        if (command.equals("simultaneous")) {
            double[] values = evaluateFields(fields, 0, context);
            if (values.length < 7) throw new IllegalArgumentException("Enter n, augmented coefficients");
            int size = integer(values[0]);
            if (size < 2 || size > 4 || values.length != 1 + size * (size + 1)) {
                throw new IllegalArgumentException("2..4 unknowns; n rows of coefficients and constants");
            }
            double[][] matrix = new double[size][size];
            double[] right = new double[size];
            int offset = 1;
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) matrix[row][column] = values[offset++];
                right[row] = values[offset++];
            }
            double[] solution = new MatrixValue(matrix).solve(right);
            List<ResultItem> items = new ArrayList<>();
            for (int index = 0; index < solution.length; index++) {
                items.add(item("x" + (index + 1), solution[index]));
            }
            return new ModeResult(formatVector("x", solution), solution[0],
                    ResultLayout.KEY_VALUE, "联立方程", items);
        }
        if (fields.size() != 2) throw new IllegalArgumentException("Enter f(x),initial guess");
        ScalarExpressionEngine.CompiledExpression expression =
                ScalarExpressionEngine.compile(fields.get(0));
        double initial = scalar(fields.get(1), context);
        NumericAnalysis.SolveResult solved = NumericAnalysis.solve(
                x -> expression.evaluate(context.withX(x)), initial, 1e-12, 100);
        if (!solved.converged()) throw new ArithmeticException("Cannot Solve");
        String display = "x=" + format(solved.solution())
                + "\nL-R=" + format(solved.remainder());
        return ModeResult.keyValue("SOLVE", display, solved.solution(),
                item("x", solved.solution()),
                item("L-R", solved.remainder()));
    }

    private static ModeResult inequality(List<String> fields,
                                         ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (values.length < 4) throw new IllegalArgumentException("Enter relation(1..4), coefficients");
        int relationCode = integer(values[0]);
        PolynomialEngine.Relation relation = switch (relationCode) {
            case 1 -> PolynomialEngine.Relation.GREATER;
            case 2 -> PolynomialEngine.Relation.LESS;
            case 3 -> PolynomialEngine.Relation.GREATER_OR_EQUAL;
            case 4 -> PolynomialEngine.Relation.LESS_OR_EQUAL;
            default -> throw new IllegalArgumentException("Relation: 1 >, 2 <, 3 ≥, 4 ≤");
        };
        double[] coefficients = new double[values.length - 1];
        System.arraycopy(values, 1, coefficients, 0, coefficients.length);
        PolynomialEngine.InequalitySolution solution =
                PolynomialEngine.solveInequality(relation, coefficients);
        return new ModeResult(formatIntervals(solution), null);
    }

    private static ModeResult matrix(List<String> fields,
                                     ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (values.length < 6) throw new IllegalArgumentException("Enter rows,cols,values...");
        int rows = integer(values[0]);
        int columns = integer(values[1]);
        if (rows < 1 || rows > 4 || columns < 1 || columns > 4
                || values.length != 2 + rows * columns) {
            throw new IllegalArgumentException("Matrix size 1..4 and matching values required");
        }
        double[][] data = new double[rows][columns];
        int offset = 2;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) data[row][column] = values[offset++];
        }
        MatrixValue matrix = new MatrixValue(data);
        List<String> cells = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells.add(format(matrix.get(row, column)));
            }
        }
        List<ResultItem> items = new ArrayList<>();
        if (rows == columns) {
            double determinant = matrix.determinant();
            items.add(item("det", determinant));
            String display = rows + "×" + columns + "  det=" + format(determinant)
                    + "\n[1,1]=" + format(matrix.get(0, 0));
            return ModeResult.grid(ResultLayout.MATRIX, "矩阵", display, determinant,
                    rows, columns, cells, items);
        }
        String display = rows + "×" + columns + " matrix\n[1,1]="
                + format(matrix.get(0, 0));
        return ModeResult.grid(ResultLayout.MATRIX, "矩阵", display, matrix.get(0, 0),
                rows, columns, cells, items);
    }

    private static ModeResult vector(List<String> fields,
                                     ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (values.length == 2 || values.length == 3) {
            VectorValue vector = new VectorValue(values);
            List<String> cells = new ArrayList<>();
            for (double value : values) cells.add(format(value));
            List<ResultItem> items = new ArrayList<>();
            items.add(item("|v|", vector.magnitude()));
            VectorValue unit = vector.unit();
            for (int index = 0; index < values.length; index++) {
                items.add(item("unit[" + (index + 1) + "]", unit.get(index)));
            }
            String display = "|v|=" + format(vector.magnitude())
                    + "\nunit[1]=" + format(unit.get(0));
            return ModeResult.grid(ResultLayout.VECTOR, "向量", display, vector.magnitude(),
                    1, values.length, cells, items);
        }
        if (values.length == 4 || values.length == 6) {
            int dimension = values.length / 2;
            double[] left = new double[dimension];
            double[] right = new double[dimension];
            System.arraycopy(values, 0, left, 0, dimension);
            System.arraycopy(values, dimension, right, 0, dimension);
            VectorValue a = new VectorValue(left);
            VectorValue b = new VectorValue(right);
            double dot = a.dot(b);
            double angle = Math.toDegrees(a.angleRadians(b));
            List<String> cells = new ArrayList<>();
            for (double value : left) cells.add(format(value));
            for (double value : right) cells.add(format(value));
            List<ResultItem> items = new ArrayList<>();
            items.add(item("dot", dot));
            items.add(item("angle", format(angle) + "°"));
            String display = "dot=" + format(dot) + "\nangle=" + format(angle) + "°";
            return ModeResult.grid(ResultLayout.VECTOR, "向量运算", display, dot,
                    2, dimension, cells, items);
        }
        throw new IllegalArgumentException("Enter 2/3 values, or two equal vectors");
    }

    private static ModeResult ratio(String command,
                                    List<String> fields,
                                    ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        requireCount(values, 3, "A,B,D or A,B,C");
        double answer = command.equals("a:b=x:d")
                ? RatioEngine.solveAtoBEqualsXtoD(values[0], values[1], values[2])
                : RatioEngine.solveAtoBEqualsCtoX(values[0], values[1], values[2]);
        return ModeResult.keyValue("比例", "X=" + format(answer), answer,
                item("X", answer));
    }

    private static double[] evaluateFields(List<String> fields, int start,
                                           ScalarExpressionEngine.EvaluationContext context) {
        double[] values = new double[fields.size() - start];
        for (int i = start; i < fields.size(); i++) values[i - start] = scalar(fields.get(i), context);
        return values;
    }

    private static double scalar(String source, ScalarExpressionEngine.EvaluationContext context) {
        return ScalarExpressionEngine.evaluate(source, context);
    }

    private static int integer(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer required");
        }
        return (int) value;
    }

    private static void requireCount(double[] values, int expected, String syntax) {
        if (values.length != expected) throw new IllegalArgumentException("Enter " + syntax);
    }

    private static String requireField(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Empty field");
        return trimmed;
    }

    static String format(double value) {
        if (!Double.isFinite(value)) return Double.toString(value);
        if (value == 0.0) return "0";
        BigDecimal rounded = BigDecimal.valueOf(value)
                .round(new MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros();
        double magnitude = Math.abs(value);
        String text = magnitude < 1e-9 || magnitude >= 1e10
                ? rounded.toString() : rounded.toPlainString();
        return text.startsWith("-") ? "−" + text.substring(1) : text;
    }

    private static String formatComplex(ComplexValue value) {
        if (value.imaginary() == 0.0) return format(value.real());
        if (value.real() == 0.0) return format(value.imaginary()) + "i";
        return format(value.real()) + (value.imaginary() < 0 ? "−" : "+")
                + format(Math.abs(value.imaginary())) + "i";
    }

    private static String formatVector(String prefix, double[] values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append(i == 1 ? "\n" : "  ");
            text.append(prefix).append(i + 1).append("=").append(format(values[i]));
        }
        return text.toString();
    }

    private static String formatIntervals(PolynomialEngine.InequalitySolution solution) {
        if (solution.isNoSolution()) return "无解";
        if (solution.isAllReals()) return "全体实数";
        StringBuilder text = new StringBuilder();
        for (PolynomialEngine.Interval interval : solution.intervals()) {
            if (text.length() > 0) text.append(" ∪ ");
            text.append(interval.includeLower() ? '[' : '(')
                    .append(Double.isInfinite(interval.lower()) ? "−∞" : format(interval.lower()))
                    .append(',')
                    .append(Double.isInfinite(interval.upper()) ? "+∞" : format(interval.upper()))
                    .append(interval.includeUpper() ? ']' : ')');
        }
        return text.toString();
    }
}
