package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;

/** Manual-shaped checks for structured application bridge workflows. */
public final class CnCwModeEngineSuite {
    private int checks;
    private final ScalarExpressionEngine.EvaluationContext context =
            ScalarExpressionEngine.EvaluationContext.standard();

    public static void main(String[] args) { new CnCwModeEngineSuite().run(); }

    private void run() {
        splitKeepsFunctionArguments();
        statisticsWorkflows();
        distributionWorkflows();
        tableWorkflow();
        equationWorkflows();
        inequalityWorkflow();
        matrixVectorAndRatioWorkflows();
        System.out.println("PASS " + checks + " structured mode checks");
    }

    private void splitKeepsFunctionArguments() {
        var fields = CnCwModeEngine.splitTopLevel("log(2,16),0,2,1");
        equal(4, fields.size(), "top-level field count");
        equal("log(2,16)", fields.get(0), "nested comma retained");
    }

    private void statisticsWorkflows() {
        var one = evaluate(ApplicationMode.STATISTICS, "one", "1,2,3,4");
        near(2.5, one.primaryValue(), 0.0, "one-variable mean");
        check(one.display().contains("n=4"), "one-variable count rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, one.layout(),
                "one-variable statistics publishes key/value layout");
        equal("一元统计", one.title(), "one-variable result title");
        equal(4, one.items().size(), "one-variable structured item count");
        equal("n", one.items().get(0).label(), "one-variable first item label");
        equal("4", one.items().get(0).value(), "one-variable count value");
        equal("x̄", one.items().get(1).label(), "one-variable mean item label");
        equal("2.5", one.items().get(1).value(), "one-variable mean item value");

        var regression = evaluate(ApplicationMode.STATISTICS, "regression", "1,3,2,5,3,7");
        near(1.0, regression.primaryValue(), 1e-12, "perfect linear correlation");
        check(regression.display().contains("a=2"), "linear slope rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, regression.layout(),
                "regression publishes key/value layout");
        equal("线性回归", regression.title(), "regression result title");
        equal(3, regression.items().size(), "regression structured item count");
        equal("r", regression.items().get(2).label(), "regression correlation item label");

        var two = evaluate(ApplicationMode.STATISTICS, "two", "1,2,3,4");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, two.layout(),
                "two-variable statistics publishes key/value layout");
        equal("双变量统计", two.title(), "two-variable result title");
        equal(4, two.items().size(), "two-variable structured item count");
        equal("x̄", two.items().get(0).label(), "two-variable x mean label");
        equal("ȳ", two.items().get(1).label(), "two-variable y mean label");
    }

    private void distributionWorkflows() {
        var binomial = evaluate(ApplicationMode.DISTRIBUTION, "binomial", "2,5,0.5");
        near(0.3125, binomial.primaryValue(), 1e-15, "binomial PMF");
        var normal = evaluate(ApplicationMode.DISTRIBUTION, "normal", "0,0,1");
        near(0.3989422804014327, normal.primaryValue(), 1e-12, "normal PDF");
        var poisson = evaluate(ApplicationMode.DISTRIBUTION, "poisson", "3,2.5");
        near(0.213763017249736, poisson.primaryValue(), 2e-14, "Poisson PMF");
    }

    private void tableWorkflow() {
        var table = evaluate(ApplicationMode.FUNCTION_TABLE, "single", "x^2,-1,1,0.5");
        near(1.0, table.primaryValue(), 0.0, "table first f value");
        check(table.display().contains("rows=5"), "table row limit calculation");
    }

    private void equationWorkflows() {
        var polynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,2,-3");
        check(polynomial.display().contains("x1="), "polynomial roots rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, polynomial.layout(),
                "polynomial publishes key/value layout");
        equal("多项式方程", polynomial.title(), "polynomial result title");
        equal(2, polynomial.items().size(), "quadratic publishes two roots");
        equal("x1", polynomial.items().get(0).label(), "first polynomial root label");
        equal("x2", polynomial.items().get(1).label(), "second polynomial root label");

        var complexPolynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,0,1");
        equal(2, complexPolynomial.items().size(), "complex quadratic keeps both roots");
        check(complexPolynomial.items().get(0).value().contains("i"),
                "first complex root remains structured as complex text");
        check(complexPolynomial.items().get(1).value().contains("i"),
                "second complex root remains structured as complex text");

        var simultaneous = evaluate(ApplicationMode.EQUATION, "simultaneous",
                "2,1,1,5,2,-1,1");
        near(2.0, simultaneous.primaryValue(), 1e-12, "two-variable system first root");
        check(simultaneous.display().contains("x2=3"), "system second root rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, simultaneous.layout(),
                "simultaneous equations publish key/value layout");
        equal("联立方程", simultaneous.title(), "simultaneous result title");
        equal(2, simultaneous.items().size(), "two-variable system publishes two entries");
        equal("x1", simultaneous.items().get(0).label(), "system first variable label");
        equal("2", simultaneous.items().get(0).value(), "system first variable value");
        equal("x2", simultaneous.items().get(1).label(), "system second variable label");
        equal("3", simultaneous.items().get(1).value(), "system second variable value");

        var solve = evaluate(ApplicationMode.EQUATION, "solve", "x^2-16,1");
        near(4.0, solve.primaryValue(), 1e-9, "SOLVE workflow");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, solve.layout(),
                "SOLVE publishes key/value layout");
        equal("SOLVE", solve.title(), "SOLVE result title");
        equal(2, solve.items().size(), "SOLVE publishes solution and residual");
        equal("x", solve.items().get(0).label(), "SOLVE solution label");
        equal("L-R", solve.items().get(1).label(), "SOLVE residual label");
    }

    private void inequalityWorkflow() {
        var result = evaluate(ApplicationMode.INEQUALITY, "quadratic", "3,1,2,-3");
        check(result.display().contains("−3") || result.display().contains("-3"),
                "inequality left boundary");
        check(result.display().contains("1"), "inequality right boundary");
    }

    private void matrixVectorAndRatioWorkflows() {
        var matrix = evaluate(ApplicationMode.MATRIX, "calculate", "2,2,2,1,1,1");
        near(1.0, matrix.primaryValue(), 0.0, "matrix determinant");
        equal(CnCwModeEngine.ResultLayout.MATRIX, matrix.layout(),
                "matrix publishes matrix layout");
        equal("矩阵", matrix.title(), "matrix result title");
        equal(2, matrix.rows(), "matrix row count");
        equal(2, matrix.columns(), "matrix column count");
        equal(4, matrix.cells().size(), "matrix cell count");
        equal("2", matrix.cells().get(0), "matrix [1,1] structured cell");
        equal("1", matrix.items().get(0).value(), "matrix determinant structured item");

        var rectangular = evaluate(ApplicationMode.MATRIX, "calculate", "2,3,1,2,3,4,5,6");
        equal(CnCwModeEngine.ResultLayout.MATRIX, rectangular.layout(),
                "rectangular matrix keeps matrix layout");
        equal(2, rectangular.rows(), "rectangular matrix rows");
        equal(3, rectangular.columns(), "rectangular matrix columns");
        equal("6", rectangular.cells().get(5), "rectangular matrix last cell");
        equal(0, rectangular.items().size(), "rectangular matrix has no determinant item");

        var singleVector = evaluate(ApplicationMode.VECTOR, "calculate", "3,4,0");
        near(5.0, singleVector.primaryValue(), 0.0, "single-vector magnitude");
        equal(CnCwModeEngine.ResultLayout.VECTOR, singleVector.layout(),
                "single vector publishes vector layout");
        equal(1, singleVector.rows(), "single vector uses one grid row");
        equal(3, singleVector.columns(), "single vector dimension");
        equal("3", singleVector.cells().get(0), "single vector first component");
        equal("|v|", singleVector.items().get(0).label(), "single vector magnitude label");
        equal("5", singleVector.items().get(0).value(), "single vector magnitude value");

        var vector = evaluate(ApplicationMode.VECTOR, "calculate", "1,2,3,4");
        near(11.0, vector.primaryValue(), 0.0, "two-dimensional dot product");
        equal(CnCwModeEngine.ResultLayout.VECTOR, vector.layout(),
                "two-vector operation publishes vector layout");
        equal(2, vector.rows(), "two-vector operation uses two rows");
        equal(2, vector.columns(), "two-vector operation dimension");
        equal("dot", vector.items().get(0).label(), "dot-product item label");
        equal("11", vector.items().get(0).value(), "dot-product item value");
        equal("angle", vector.items().get(1).label(), "vector-angle item label");
        check(vector.items().get(1).value().endsWith("°"), "vector angle keeps degree unit");

        var ratio = evaluate(ApplicationMode.RATIO, "a:b=x:d", "3,8,12");
        near(4.5, ratio.primaryValue(), 0.0, "ratio unknown");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, ratio.layout(),
                "ratio publishes structured key-value layout");
        equal("X", ratio.items().get(0).label(), "ratio result label");
        equal("4.5", ratio.items().get(0).value(), "ratio result value");
    }

    private CnCwModeEngine.ModeResult evaluate(ApplicationMode mode, String command, String source) {
        return CnCwModeEngine.evaluate(mode, command, source, context);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
