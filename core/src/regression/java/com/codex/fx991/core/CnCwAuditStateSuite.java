package com.codex.fx991.core;

import com.codex.fx991.core.cw.*;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;
import com.codex.fx991.core.math.CalculationBudget;
import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.List;

/** Public-input reproductions from the September audit, not snapshots of implementation text. */
public final class CnCwAuditStateSuite {
    private int checks;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) { new CnCwAuditStateSuite().run(); }

    private void run() {
        test("HEX operands", this::baseN);
        test("small matrix/vector results", this::smallGrid);
        test("workflow error recovery", this::workflowError);
        test("repeated HOME", this::repeatedHome);
        test("history FORMAT", this::historyFormat);
        test("complex verification", this::complexVerification);
        test("public scalar/complex precedence", this::expressionPrecedence);
        test("quadratic workflow retains large-offset coefficients", this::quadraticWorkflow);
        test("verification availability", this::verificationAvailability);
        test("small imaginary display", this::smallImaginary);
        test("small application result display", this::smallApplicationResult);
        test("machine shared budget", this::sharedBudget);
        test("core-owned scheduling intent", this::schedulingIntent);
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("PASS " + checks + " audit state checks");
    }

    private void test(String name, Runnable body) {
        try { body.run(); }
        catch (AssertionError | RuntimeException error) {
            failures.add(name + ": " + error.getMessage());
            System.out.println("FAIL " + name + ": " + error.getMessage());
        }
    }

    private void baseN() {
        CnCwMachine m = app(6);
        choose(m, "base-convert");
        key(m, CnCwKey.RIGHT, CnCwKey.OK, CnCwKey.OK, CnCwKey.DIGIT_1,
                CnCwKey.VAR_E, CnCwKey.EXE);
        check(m.state().resultShown() && m.state().ans() == 30.0, "HEX 1E must convert to DEC 30");
        for (String command : new String[]{"base-convert", "base-add", "base-not"}) {
            CnCwWorkflowSession session = CnCwWorkflowSession.create(
                    CnCwWorkflowSpec.forCommand(ApplicationMode.BASE_N, command));
            session.setCell(0, 0, "16");
            int first = command.equals("base-convert") ? 2 : 1;
            session.setCell(0, first, "1E");
            if (command.equals("base-add")) session.setCell(0, 2, "A");
            check(CnCwWorkflowValidation.validate(session).ready(), command + " accepts HEX digits");
            session.setCell(0, 0, "2");
            check(!CnCwWorkflowValidation.validate(session).ready(), command + " rejects nonbinary digits");
        }
    }

    private void smallGrid() {
        CnCwMachine m = app(7);
        choose(m, "define");
        m.pasteExpression("0.000000000001"); key(m, CnCwKey.EXE);
        near(1e-12, number(m.state().applicationResult().cells().get(0)), "matrix small value");
        open(m, 7); choose(m, "matrix-transpose"); key(m, CnCwKey.EXE);
        near(1e-12, number(m.state().applicationResult().cells().get(0)), "transpose small value");
        CnCwMachine v = app(8); choose(v, "define");
        v.pasteExpression("0.000000000001"); key(v, CnCwKey.OK);
        v.pasteExpression("0"); key(v, CnCwKey.EXE);
        near(1e-12, number(v.state().applicationResult().cells().get(0)), "vector small value");
    }

    private void workflowError() {
        for (CnCwKey dismiss : new CnCwKey[]{CnCwKey.OK, CnCwKey.ENTER, CnCwKey.BACK,
                CnCwKey.LEFT, CnCwKey.RIGHT}) {
            CnCwMachine m = app(1); choose(m, "one-freq");
            m.pasteExpression("10"); key(m, CnCwKey.OK);
            m.pasteExpression("-1"); key(m, CnCwKey.EXE);
            check(m.state().calculationState().isError(), "negative frequency rejects");
            key(m, dismiss);
            check(!m.state().expression().contains(","), "error exit restores one cell: " + dismiss);
            m.selectWorkflowCell(0, 0);
            check(m.state().workflowInput().cells().equals(List.of("10", "-1")), "cells preserved: " + dismiss);
            m.selectWorkflowCell(0, 1); key(m, CnCwKey.AC);
            m.pasteExpression("2"); key(m, CnCwKey.EXE);
            check(m.state().calculationState().isResult(), "frequency correctable after " + dismiss);
        }
    }

    private void repeatedHome() {
        CnCwMachine m = app(0); verify(m);
        key(m, CnCwKey.HOME, CnCwKey.HOME, CnCwKey.RIGHT, CnCwKey.OK);
        check(!m.state().verificationMode(), "double HOME must clear verification across apps");
        open(m, 0); m.pasteExpression("2+3"); key(m, CnCwKey.EXE);
        near(5, m.state().ans(), "normal calculation after switch");
        CnCwMachine matrix = app(7); choose(matrix, "define");
        matrix.pasteExpression("5"); key(matrix, CnCwKey.EXE);
        open(matrix, 7); choose(matrix, "matrix-square"); key(matrix, CnCwKey.EXE);
        key(matrix, CnCwKey.HOME, CnCwKey.HOME, CnCwKey.OK);
        key(matrix, CnCwKey.HOME, CnCwKey.HOME);
        for (int n = 0; n < 7; n++) key(matrix, CnCwKey.RIGHT);
        key(matrix, CnCwKey.OK); choose(matrix, "matrix-ans");
        check(matrix.state().calculationState().isError(), "MatAns clears on actual app switch");
        open(matrix, 7); choose(matrix, "matrix-square"); key(matrix, CnCwKey.EXE);
        near(25, matrix.state().ans(), "named MatA survives app switch");
    }

    private void historyFormat() {
        CnCwMachine m = app(0);
        m.pasteExpression("1/2"); key(m, CnCwKey.EXE, CnCwKey.AC);
        m.pasteExpression("1/3"); key(m, CnCwKey.EXE, CnCwKey.UP, CnCwKey.UP);
        key(m, CnCwKey.FORMAT); choose(m, "improper");
        equal("1/2", m.state().result(), "history fraction uses recalled payload");
        near(1.0 / 3, m.state().ans(), "history formatting leaves Ans alone");
        near(0.5, m.state().calculationState().scalarValue(), "typed result still recalled value");
        key(m, CnCwKey.FORMAT); choose(m, "engineering");
        check(m.state().result().startsWith("500"), "ENG operates on historical half");
        key(m, CnCwKey.RIGHT);
        check(m.state().result().startsWith("0.5"), "ENG movement retains history value");
        key(m, CnCwKey.BACK);
        equal("1/2", m.state().result(), "format restore uses original historical display");
        CnCwMachine c = app(5);
        c.pasteExpression("1+i"); key(c, CnCwKey.EXE, CnCwKey.AC);
        c.pasteExpression("2+3i"); key(c, CnCwKey.EXE, CnCwKey.UP, CnCwKey.UP);
        key(c, CnCwKey.FORMAT); choose(c, "rectangular");
        equal("1+i", c.state().result(), "historical complex format");
        near(2, c.state().ans(), "complex formatting leaves newest Ans");
    }

    private void complexVerification() {
        CnCwMachine c = app(5); c.pasteExpression("2+3i"); key(c, CnCwKey.EXE, CnCwKey.AC);
        verify(c); c.pasteExpression("i^2=-1"); key(c, CnCwKey.EXE);
        equal("True", c.state().result(), "complex relation verifies");
        near(2, c.state().ans(), "verification must not overwrite complex Ans");
        key(c, CnCwKey.AC); c.pasteExpression("i^2"); key(c, CnCwKey.EXE);
        check(c.state().calculationState().isError(), "verification requires a relation");
        key(c, CnCwKey.AC); verify(c);
        c.pasteExpression("Ans"); key(c, CnCwKey.EXE);
        equal("2+3i", c.state().result(), "complex answer survives verification");
    }

    private void verificationAvailability() {
        for (int index = 0; index < 10; index++) {
            CnCwMachine m = app(index); key(m, CnCwKey.TOOLS);
            boolean available = m.state().menuItems().stream().anyMatch(c -> c.id().equals("verify"));
            check(available == (index == 0 || index == 5), "verify availability in app " + index);
        }
    }

    private void smallImaginary() {
        CnCwMachine m = app(5); m.pasteExpression("0.000000000000001i"); key(m, CnCwKey.EXE);
        check(m.state().result().contains("i"), "nonzero tiny imaginary value is not displayed as zero");
    }

    private void sharedBudget() {
        CnCwMachine m = app(0); m.pasteExpression("sum(x,1,3)+sum(x,1,3)");
        try (CalculationBudget.Scope ignored = CalculationBudget.open(5, 2_000_000_000L)) {
            key(m, CnCwKey.EXE);
        }
        check(m.state().calculationState().error() == CalculationError.TIMEOUT,
                "parallel sums share one evaluation budget and report TIMEOUT");
        key(m, CnCwKey.AC); m.pasteExpression("1+1"); key(m, CnCwKey.EXE);
        near(2, m.state().ans(), "next calculation is not poisoned by old budget");
        key(m, CnCwKey.AC); m.pasteExpression("sum(x,1,100)");
        boolean canceled = false;
        Thread.currentThread().interrupt();
        try { key(m, CnCwKey.EXE); }
        catch (CancellationException expected) { canceled = true; }
        finally { Thread.interrupted(); }
        check(canceled, "machine propagates cancellation rather than showing syntax error");
        near(2, m.state().ans(), "cancellation never replaces Ans");
    }

    private void schedulingIntent() {
        CnCwMachine m = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        check(!m.requiresEvaluation(CnCwKey.OK), "HOME navigation is inline");
        key(m, CnCwKey.OK);
        check(!m.requiresEvaluation(CnCwKey.EXE), "empty input does not start worker");
        m.pasteExpression("1+1");
        for (CnCwKey execute : new CnCwKey[]{CnCwKey.OK, CnCwKey.ENTER, CnCwKey.EXE}) {
            check(m.requiresEvaluation(execute), "nonempty calculation schedules " + execute);
        }
        check(!m.requiresEvaluation(CnCwKey.DIGIT_2), "digit is inline");
        key(m, CnCwKey.SETTINGS);
        check(!m.requiresEvaluation(CnCwKey.OK), "menu activation is inline");
        key(m, CnCwKey.AC, CnCwKey.AC); m.pasteExpression("1/0"); key(m, CnCwKey.EXE);
        check(m.state().calculationState().isError(), "setup error");
        check(!m.requiresEvaluation(CnCwKey.OK), "error OK is inline");
        check(!m.requiresEvaluation(CnCwKey.ENTER), "error ENTER is inline");
        check(m.requiresEvaluation(CnCwKey.EXE), "error retry EXE evaluates");
        CnCwMachine table = app(2); choose(table, "single");
        check(!table.requiresEvaluation(CnCwKey.OK), "form next cell is inline");
        check(table.requiresEvaluation(CnCwKey.EXE), "form EXE schedules validation/evaluation");
    }

    private void smallApplicationResult() {
        var roots = CnCwModeEngine.evaluate(ApplicationMode.EQUATION, "polynomial",
                "1,0,0.00000000000000000000000001",
                ScalarExpressionEngine.EvaluationContext.standard());
        for (var item : roots.items()) {
            check(item.value().contains("i"), "nonzero complex root keeps imaginary component");
            near(1e-13, Math.abs(number(item.value().replace("i", ""))), "tiny root is not rounded to zero");
        }
        var table = CnCwModeEngine.evaluate(ApplicationMode.FUNCTION_TABLE, "single",
                "0.000000000001,0,1,1", ScalarExpressionEngine.EvaluationContext.standard());
        near(1e-12, number(table.cells().get(1)), "tiny table values remain nonzero");
        CnCwMachine matrix = app(7); choose(matrix, "define");
        matrix.pasteExpression("10"); key(matrix, CnCwKey.EXE);
        equal("10", matrix.state().applicationResult().cells().get(0), "ordinary grid numbers remain plain");
    }

    private void quadraticWorkflow() {
        for (String command : new String[]{"reg-quadratic", "reg-quadratic-freq"}) {
            CnCwMachine m = app(1); choose(m, command);
            int columns = m.state().workflowInput().columns();
            for (int row = 0; row < 3; row++) {
                m.selectWorkflowCell(row, 0);
                m.pasteExpression(Integer.toString(99999999 + row)); key(m, CnCwKey.OK);
                m.pasteExpression(row == 1 ? "0" : "1");
                if (columns == 3) {
                    key(m, CnCwKey.OK); m.pasteExpression("1");
                }
                if (row < 2) key(m, CnCwKey.OK);
            }
            key(m, CnCwKey.EXE);
            check(m.state().calculationState().isResult(), "quadratic workflow result " + command);
            var items = m.state().applicationResult().items();
            near(1, number(items.get(0).value()), "quadratic workflow a");
            near(-200000000, number(items.get(1).value()), "quadratic workflow b");
            near(1e16, number(items.get(2).value()), "quadratic workflow c");
        }
    }

    private void expressionPrecedence() {
        for (int mode : new int[]{0, 5}) {
            CnCwMachine m = app(mode);
            m.pasteExpression("6/2(1+2)"); key(m, CnCwKey.EXE);
            equal("1", m.state().result(), "pasted implicit product in mode " + mode);
            key(m, CnCwKey.AC);
            key(m, CnCwKey.DIGIT_6, CnCwKey.DIVIDE, CnCwKey.DIGIT_2,
                    CnCwKey.OPEN_PAREN, CnCwKey.DIGIT_1, CnCwKey.ADD,
                    CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
            equal("1", m.state().result(), "keyed implicit product in mode " + mode);
            key(m, CnCwKey.AC); m.pasteExpression("6/2*(1+2)"); key(m, CnCwKey.EXE);
            equal("9", m.state().result(), "explicit product stays left associative " + mode);
            key(m, CnCwKey.AC); verify(m);
            m.pasteExpression("6/2(1+2)=1"); key(m, CnCwKey.EXE);
            equal("True", m.state().result(), "verification uses same precedence " + mode);
        }
    }

    private static CnCwMachine app(int index) {
        CnCwMachine m = new CnCwMachine(CnCwModel.FX_991_CN_CW); open(m, index); return m;
    }
    private static void open(CnCwMachine m, int index) {
        key(m, CnCwKey.HOME);
        for (int n = 0; n < index; n++) key(m, CnCwKey.RIGHT);
        key(m, CnCwKey.OK);
    }
    private static void choose(CnCwMachine m, String id) {
        List<CnCwCommand> commands = m.state().screen().isApplication() && m.state().applicationLanding()
                ? m.state().modeCommands() : m.state().menuItems();
        int count = commands.size();
        for (int n = 0; n < count; n++) {
            if (commands.get(m.state().selectedIndex()).id().equals(id)) {
                key(m, CnCwKey.OK); return;
            }
            key(m, CnCwKey.DOWN);
        }
        throw new AssertionError("Missing command: " + id);
    }
    private static void verify(CnCwMachine m) { key(m, CnCwKey.TOOLS); choose(m, "verify"); }
    private static void key(CnCwMachine m, CnCwKey... keys) { for (CnCwKey key : keys) m.dispatch(key); }
    private static double number(String text) { return Double.parseDouble(text.replace('−', '-')); }
    private void equal(String expected, String actual, String message) { check(expected.equals(actual), message + ": " + actual); }
    private void near(double expected, double actual, String message) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= Math.abs(expected) * 1e-10,
                message + ": expected " + expected + ", got " + actual);
    }
    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
