package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.cw.CnCwWorkflowValidation;
import com.codex.fx991.core.mode.ApplicationMode;

/** Regression coverage for Stage 6 workflow cell validation. */
public final class CnCwWorkflowValidationSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwWorkflowValidationSuite().run();
    }

    private void run() {
        blankAndValidCells();
        syntaxErrorsLocateFirstCell();
        compileOnlyDoesNotEvaluateDomain();
        snapshotValidationIsStable();
        System.out.println("PASS " + checks + " workflow-validation checks");
    }

    private void blankAndValidCells() {
        var session = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "solve"));
        var blank = CnCwWorkflowValidation.validate(session);
        check(!blank.ready(), "blank SOLVE input is not ready");
        equal(0, blank.firstProblemRow(), "first blank row");
        equal(0, blank.firstProblemColumn(), "first blank column");
        equal(CnCwWorkflowValidation.Status.EMPTY,
                blank.cell(0, 0, session.columns()).status(), "blank cell state");

        session.setCell(0, 0, "x^2-16");
        session.setCell(0, 1, "1");
        var valid = CnCwWorkflowValidation.validate(session);
        check(valid.ready(), "valid SOLVE fields are ready");
        check(!valid.hasProblem(), "valid report has no problem cell");
    }

    private void syntaxErrorsLocateFirstCell() {
        var matrix = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate"));
        matrix.resizeGrid(2, 2);
        matrix.setCell(0, 0, "1");
        matrix.setCell(0, 1, "2+");
        matrix.setCell(1, 0, "3");
        matrix.setCell(1, 1, "4");

        var report = CnCwWorkflowValidation.validate(matrix);
        check(!report.ready(), "syntax error blocks matrix execution");
        equal(0, report.firstProblemRow(), "syntax error row");
        equal(1, report.firstProblemColumn(), "syntax error column");
        equal(CnCwWorkflowValidation.Status.INVALID_EXPRESSION,
                report.cell(0, 1, matrix.columns()).status(), "invalid expression state");
        equal("表达式格式错误", report.cell(0, 1, matrix.columns()).message(),
                "invalid expression message");
    }

    private void compileOnlyDoesNotEvaluateDomain() {
        equal(CnCwWorkflowValidation.Status.VALID,
                CnCwWorkflowValidation.validateExpression("1/x"),
                "1/x is syntactically valid even if default x would be zero");
        equal(CnCwWorkflowValidation.Status.VALID,
                CnCwWorkflowValidation.validateExpression("sqrt(x-1)"),
                "domain-dependent expression is not evaluated during validation");
        equal(CnCwWorkflowValidation.Status.INVALID_EXPRESSION,
                CnCwWorkflowValidation.validateExpression("sin("),
                "unfinished function is invalid in committed workflow cell");
    }

    private void snapshotValidationIsStable() {
        var statistics = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one"));
        statistics.setSelectedCell("5");
        var snapshot = statistics.snapshot();
        statistics.setSelectedCell("2+");
        check(CnCwWorkflowValidation.validate(snapshot).ready(),
                "snapshot validation is isolated from later live edits");
        check(!CnCwWorkflowValidation.validate(statistics).ready(),
                "live validation sees later invalid edit");
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
}
