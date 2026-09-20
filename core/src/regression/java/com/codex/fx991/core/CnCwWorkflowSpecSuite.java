package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;

/** Regression coverage for Stage 5 core-owned workflow input specifications/state. */
public final class CnCwWorkflowSpecSuite {
    private int checks;
    private final ScalarExpressionEngine.EvaluationContext context =
            ScalarExpressionEngine.EvaluationContext.standard();

    public static void main(String[] args) {
        new CnCwWorkflowSpecSuite().run();
    }

    private void run() {
        statisticsSpecs();
        functionTableSpecsAndSessions();
        inequalitySpecs();
        ratioSpecsAndSessions();
        equationSpecs();
        matrixAndVectorSpecs();
        statisticsSessions();
        inequalitySessions();
        equationSessions();
        matrixAndVectorSessions();
        unsupportedWorkflowReturnsNull();
        System.out.println("PASS " + checks + " workflow-spec/session checks");
    }

    private void statisticsSpecs() {
        var one = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one");
        equal(CnCwWorkflowSpec.InputLayout.SERIES, one.layout(), "one-variable series layout");
        equal("x", one.fields().get(0).label(), "one-variable x label");
        equal(1, one.minColumns(), "one-variable one column");
        check(one.hasVariableRows(), "one-variable rows are expandable");

        var two = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "two");
        equal(CnCwWorkflowSpec.InputLayout.PAIRED_SERIES, two.layout(), "two-variable paired layout");
        equal(2, two.fields().size(), "two-variable has x/y columns");
        equal("y", two.fields().get(1).label(), "two-variable y label");
        equal(2, two.minRows(), "two-variable requires two observations");

        var regression = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "regression");
        equal("线性回归", regression.title(), "regression title");
        equal(CnCwWorkflowSpec.InputLayout.PAIRED_SERIES, regression.layout(), "regression paired layout");
    }


    private void functionTableSpecsAndSessions() {
        var fSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "f");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, fSpec.layout(),
                "function-table f uses fixed fields");
        equal(4, fSpec.fields().size(), "function-table f field count");
        equal("步长", fSpec.fields().get(3).label(), "function-table step label");
        var f = CnCwWorkflowSession.create(fSpec);
        f.setCell(0, 0, "x^2");
        f.setCell(0, 1, "0");
        f.setCell(0, 2, "2");
        f.setCell(0, 3, "1");
        var fResult = f.evaluate(context);
        equal(com.codex.fx991.core.cw.CnCwModeEngine.ResultLayout.TABLE, fResult.layout(),
                "function-table f returns TABLE layout");
        equal(3, fResult.rows(), "function-table f row count");
        equal(2, fResult.columns(), "function-table f column count");
        equal(6, fResult.cells().size(), "function-table f complete cell payload");
        equal("4", fResult.cells().get(5), "function-table f last value");

        var fgSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "fg");
        equal(5, fgSpec.fields().size(), "function-table fg field count");
        var fg = CnCwWorkflowSession.create(fgSpec);
        fg.setCell(0, 0, "x");
        fg.setCell(0, 1, "x+10");
        fg.setCell(0, 2, "1");
        fg.setCell(0, 3, "2");
        fg.setCell(0, 4, "1");
        var fgResult = fg.evaluate(context);
        equal(2, fgResult.rows(), "function-table fg row count");
        equal(3, fgResult.columns(), "function-table fg column count");
        equal("12", fgResult.cells().get(5), "function-table fg last g value");
    }

    private void inequalitySpecs() {
        var quadratic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quadratic");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, quadratic.layout(),
                "quadratic inequality fixed fields");
        equal(4, quadratic.fields().size(), "quadratic relation plus three coefficients");
        equal(CnCwWorkflowSpec.FieldKind.CHOICE, quadratic.fields().get(0).kind(),
                "inequality relation is a choice");
        equal(4, quadratic.fields().get(0).choices().size(), "four inequality relations");
        equal(">", quadratic.fields().get(0).displayValue("1"), "relation 1 display");
        equal("≤", quadratic.fields().get(0).displayValue("4"), "relation 4 display");

        var cubic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "cubic");
        equal(5, cubic.fields().size(), "cubic relation plus four coefficients");
        var quartic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quartic");
        equal(6, quartic.fields().size(), "quartic relation plus five coefficients");
        equal("x^4", quartic.fields().get(1).label(), "quartic leading coefficient label");
    }

    private void ratioSpecsAndSessions() {
        var xdSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "a:b=x:d");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, xdSpec.layout(),
                "A:B=X:D uses fixed fields");
        equal(3, xdSpec.fields().size(), "A:B=X:D has three known values");
        equal("A", xdSpec.fields().get(0).label(), "ratio A label");
        equal("B", xdSpec.fields().get(1).label(), "ratio B label");
        equal("D", xdSpec.fields().get(2).label(), "ratio D label");
        var xd = CnCwWorkflowSession.create(xdSpec);
        xd.setCell(0, 0, "2");
        xd.setCell(0, 1, "4");
        xd.setCell(0, 2, "10");
        equal("2,4,10", xd.legacySource(), "A:B=X:D serialization");
        var xdResult = xd.evaluate(context);
        near(5.0, xdResult.primaryValue(), 0.0, "A:B=X:D delegates to ratio engine");
        equal(com.codex.fx991.core.cw.CnCwModeEngine.ResultLayout.KEY_VALUE,
                xdResult.layout(), "ratio result is structured key-value");
        equal("X", xdResult.items().get(0).label(), "ratio result X label");

        var cxSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "a:b=c:x");
        equal("C", cxSpec.fields().get(2).label(), "A:B=C:X uses C as third known value");
        var cx = CnCwWorkflowSession.create(cxSpec);
        cx.setCell(0, 0, "2");
        cx.setCell(0, 1, "4");
        cx.setCell(0, 2, "3");
        near(6.0, cx.evaluate(context).primaryValue(), 0.0,
                "A:B=C:X delegates to ratio engine");
    }

    private void equationSpecs() {
        var polynomial = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "polynomial");
        equal(CnCwWorkflowSpec.InputLayout.COEFFICIENTS, polynomial.layout(), "polynomial coefficients layout");
        equal(3, polynomial.minRows(), "quadratic minimum coefficient count");
        equal(5, polynomial.maxRows(), "quartic maximum coefficient count");

        var simultaneous = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "simultaneous");
        equal(2, simultaneous.minRows(), "simultaneous minimum dimension");
        equal(4, simultaneous.maxRows(), "simultaneous maximum dimension");
        equal("元数", simultaneous.fields().get(0).label(), "simultaneous dimension field");

        var solve = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "solve");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, solve.layout(), "solve fixed fields");
        equal(2, solve.fields().size(), "solve expression and initial guess");
        equal("f(x)", solve.fields().get(0).label(), "solve expression label");
        equal("初值", solve.fields().get(1).label(), "solve initial label");
    }

    private void matrixAndVectorSpecs() {
        var matrix = CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate");
        equal(CnCwWorkflowSpec.InputLayout.GRID, matrix.layout(), "matrix grid layout");
        equal(1, matrix.minRows(), "matrix minimum rows");
        equal(4, matrix.maxRows(), "matrix maximum rows");
        equal(1, matrix.minColumns(), "matrix minimum columns");
        equal(4, matrix.maxColumns(), "matrix maximum columns");

        var vector = CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate");
        equal(CnCwWorkflowSpec.InputLayout.VECTOR_SET, vector.layout(), "vector-set layout");
        equal(1, vector.minRows(), "one vector minimum");
        equal(2, vector.maxRows(), "two vectors maximum");
        equal(2, vector.minColumns(), "2D minimum");
        equal(3, vector.maxColumns(), "3D maximum");
    }

    private void statisticsSessions() {
        var oneSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one");
        var one = CnCwWorkflowSession.create(oneSpec);
        equal(1, one.rows(), "one-variable session starts with one row");
        equal(1, one.columns(), "one-variable session has one column");
        one.setSelectedCell("1");
        check(one.appendRow(), "one-variable adds second row");
        one.setSelectedCell("2");
        check(one.appendRow(), "one-variable adds third row");
        one.setSelectedCell("3");
        equal("1,2,3", one.legacySource(), "one-variable serializes row-major");
        near(2.0, one.evaluate(context).primaryValue(), 0.0,
                "one-variable session delegates to statistics engine");
        check(one.move(-1, 0), "one-variable selection moves up");
        equal(1, one.selectedRow(), "one-variable focus row after move");
        check(one.removeSelectedRow(), "one-variable removes selected row");
        equal(2, one.rows(), "one-variable row count after remove");

        var pairedSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "regression");
        var paired = CnCwWorkflowSession.create(pairedSpec);
        equal(2, paired.rows(), "paired session starts with required observations");
        equal(2, paired.columns(), "paired session has x/y columns");
        paired.setCell(0, 0, "1");
        paired.setCell(0, 1, "3");
        paired.setCell(1, 0, "2");
        paired.setCell(1, 1, "5");
        check(paired.appendRow(), "paired session can append observation");
        paired.setCell(2, 0, "3");
        paired.setCell(2, 1, "7");
        equal("1,3,2,5,3,7", paired.legacySource(), "paired statistics serialization");
        near(1.0, paired.evaluate(context).primaryValue(), 1e-12,
                "paired session delegates to regression engine");

        var incomplete = CnCwWorkflowSession.create(oneSpec);
        check(!incomplete.isComplete(), "blank statistics row is incomplete");
        boolean failedClosed = false;
        try {
            incomplete.legacySource();
        } catch (IllegalStateException expected) {
            failedClosed = true;
        }
        check(failedClosed, "incomplete statistics session cannot serialize silently");
    }

    private void inequalitySessions() {
        var quadratic = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quadratic"));
        equal("1", quadratic.cell(0, 0), "inequality defaults to greater relation code");
        equal(">", quadratic.snapshot().displayCell(0, 0), "inequality displays relation symbol");
        check(quadratic.selectedIsChoice(), "relation cell is a choice");
        check(quadratic.cycleSelectedChoice(1), "relation cycles right");
        equal("2", quadratic.cell(0, 0), "second relation serializes as code 2");
        equal("<", quadratic.selectedDisplayCell(), "second relation displays less-than");
        quadratic.cycleSelectedChoice(1);
        quadratic.cycleSelectedChoice(1);
        equal("4", quadratic.cell(0, 0), "fourth relation code");
        quadratic.cycleSelectedChoice(1);
        equal("1", quadratic.cell(0, 0), "choice cycles around");
        quadratic.setCell(0, 1, "1");
        quadratic.setCell(0, 2, "0");
        quadratic.setCell(0, 3, "-1");
        equal("1,1,0,-1", quadratic.legacySource(),
                "inequality serializes relation code then coefficients");
        check(quadratic.evaluate(context).display().contains("∞"),
                "inequality session delegates to polynomial inequality engine");
    }

    private void equationSessions() {
        var polynomial = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "polynomial"));
        polynomial.setCell(0, 0, "1");
        polynomial.setCell(1, 0, "2");
        polynomial.setCell(2, 0, "-3");
        equal("1,2,-3", polynomial.legacySource(), "polynomial coefficients serialize directly");
        equal(2, polynomial.evaluate(context).items().size(), "quadratic session returns two roots");
        check(polynomial.resizeGrid(4, 1), "polynomial can resize to cubic coefficient count");
        equal(4, polynomial.rows(), "polynomial resized coefficient rows");
        equal("", polynomial.cell(0, 0), "raising degree inserts a new leading coefficient");
        equal("1", polynomial.cell(1, 0), "a2 stays attached to x^2 after raising degree");
        equal("2", polynomial.cell(2, 0), "a1 stays attached to x after raising degree");
        equal("-3", polynomial.cell(3, 0), "a0 stays constant after raising degree");
        polynomial.setCell(0, 0, "9");
        check(polynomial.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "polynomial action can lower degree");
        equal("1", polynomial.cell(0, 0), "lowering degree drops only the old leading coefficient");
        equal("-3", polynomial.cell(2, 0), "lowering degree preserves the constant term");

        var simultaneous = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "simultaneous"));
        equal(2, simultaneous.rows(), "simultaneous defaults to two variables");
        equal(3, simultaneous.columns(), "two-variable system has augmented column");
        simultaneous.setCell(0, 0, "1");
        simultaneous.setCell(0, 1, "1");
        simultaneous.setCell(0, 2, "5");
        simultaneous.setCell(1, 0, "2");
        simultaneous.setCell(1, 1, "-1");
        simultaneous.setCell(1, 2, "1");
        equal("2,1,1,5,2,-1,1", simultaneous.legacySource(),
                "simultaneous session prefixes dimension");
        near(2.0, simultaneous.evaluate(context).primaryValue(), 1e-12,
                "simultaneous session delegates to equation engine");
        check(simultaneous.setEquationDimension(3), "simultaneous can switch to three variables");
        equal(3, simultaneous.rows(), "three-variable equation rows");
        equal(4, simultaneous.columns(), "three-variable augmented columns");
        equal("1", simultaneous.cell(0, 0), "x1 coefficient survives dimension increase");
        equal("1", simultaneous.cell(0, 1), "x2 coefficient survives dimension increase");
        equal("", simultaneous.cell(0, 2), "new x3 coefficient starts blank");
        equal("5", simultaneous.cell(0, 3), "RHS stays in augmented column after dimension increase");
        equal("1", simultaneous.cell(1, 3), "second RHS stays augmented after dimension increase");
        check(simultaneous.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "simultaneous action can reduce dimension");
        equal(2, simultaneous.rows(), "dimension reduction returns to two variables");
        equal(3, simultaneous.columns(), "two-variable augmented width restored");
        equal("5", simultaneous.cell(0, 2), "RHS survives dimension reduction");
        equal("1", simultaneous.cell(1, 2), "second RHS survives dimension reduction");
        check(!simultaneous.resizeGrid(3, 3), "simultaneous rejects invalid non-augmented shape");

        var solve = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "solve"));
        solve.setCell(0, 0, "x^2-16");
        solve.setCell(0, 1, "1");
        equal("x^2-16,1", solve.legacySource(), "SOLVE fixed fields serialize in order");
        near(4.0, solve.evaluate(context).primaryValue(), 1e-9,
                "SOLVE session delegates to numeric solver");
    }

    private void matrixAndVectorSessions() {
        var matrix = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate"));
        check(matrix.resizeGrid(2, 2), "matrix can choose 2x2 shape");
        matrix.setCell(0, 0, "2");
        matrix.setCell(0, 1, "1");
        matrix.setCell(1, 0, "1");
        matrix.setCell(1, 1, "1");
        equal("2,2,2,1,1,1", matrix.legacySource(), "matrix serializes dimensions and cells");
        near(1.0, matrix.evaluate(context).primaryValue(), 0.0,
                "matrix session delegates to matrix engine");

        var vector = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate"));
        equal(1, vector.rows(), "vector defaults to one vector");
        equal(2, vector.columns(), "vector defaults to 2D");
        check(vector.resizeGrid(2, 2), "vector can switch to two 2D vectors");
        vector.setCell(0, 0, "1");
        vector.setCell(0, 1, "2");
        vector.setCell(1, 0, "3");
        vector.setCell(1, 1, "4");
        equal("1,2,3,4", vector.legacySource(), "two-vector row-major serialization");
        near(11.0, vector.evaluate(context).primaryValue(), 0.0,
                "vector session delegates to vector engine");
    }

    private void unsupportedWorkflowReturnsNull() {
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.CALCULATE, "calculate") == null,
                "ordinary calculate has no structured workflow spec");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "missing") == null,
                "unknown statistics command is rejected");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "missing") == null,
                "unknown inequality command is rejected");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "missing") == null,
                "unknown ratio command is rejected");
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
