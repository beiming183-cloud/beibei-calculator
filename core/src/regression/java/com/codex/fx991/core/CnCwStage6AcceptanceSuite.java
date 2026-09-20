package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

/**
 * Stage 6 end-to-end acceptance guards for the remaining structured workflows.
 *
 * <p>These checks intentionally drive the public machine/key APIs where the
 * workflow is user-facing, and exercise the core-owned WorkflowAction protocol
 * directly for dimension boundaries. They cover the behaviors that otherwise
 * require repetitive manual acceptance: inequality choice cells, validation
 * focus, result-to-input round trips, ratio structured input, the legacy comma
 * bridge, and statistics/matrix/vector action limits.</p>
 */
public final class CnCwStage6AcceptanceSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwStage6AcceptanceSuite().run();
    }

    private void run() {
        inequalityCommandsExposeStructuredChoice();
        inequalityChoiceRejectsTextAndResets();
        inequalityValidationLocatesBlankAndInvalidCells();
        inequalityResultReturnsToPreservedInput();
        ratioStructuredWorkflowsRoundTrip();
        ratioLegacyCommaBridgeStillWorks();
        dualFunctionWorkflowKeepsInputOnBack();
        statisticsActionsRespectRowBoundaries();
        matrixActionsRespectDimensionBoundaries();
        vectorActionsRespectDimensionBoundaries();
        System.out.println("PASS " + checks + " Stage 6 acceptance checks");
    }

    private void inequalityCommandsExposeStructuredChoice() {
        for (int command = 0; command < 3; command++) {
            CnCwMachine machine = homeApplication(4); // Inequality.
            for (int i = 0; i < command; i++) machine.dispatch(CnCwKey.DOWN);
            machine.dispatch(CnCwKey.OK);

            check(machine.state().hasWorkflowInput(),
                    "inequality command opens structured workflow");
            equal(4 + command, machine.state().workflowInput().columns(),
                    "quadratic/cubic/quartic field count");
            check(machine.state().workflowInput().isChoiceCell(0, 0),
                    "inequality relation is exposed as a choice cell");
            equal(">", machine.state().workflowInput().displayCell(0, 0),
                    "inequality relation defaults to greater-than");
        }
    }

    private void inequalityChoiceRejectsTextAndResets() {
        CnCwMachine machine = homeApplication(4);
        machine.dispatch(CnCwKey.OK); // quadratic

        machine.dispatch(CnCwKey.DIGIT_2);
        equal("1", machine.state().workflowInput().cell(0, 0),
                "digit cannot overwrite relation choice code");
        check(machine.state().status().contains("方向键选择"),
                "choice cell tells user to use directions");

        machine.dispatch(CnCwKey.RIGHT);
        equal("<", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to less-than");
        machine.dispatch(CnCwKey.RIGHT);
        equal("≥", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to greater-or-equal");
        machine.dispatch(CnCwKey.RIGHT);
        equal("≤", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to less-or-equal");
        machine.dispatch(CnCwKey.RIGHT);
        equal(">", machine.state().workflowInput().displayCell(0, 0),
                "choice wraps after fourth relation");

        machine.dispatch(CnCwKey.LEFT);
        equal("≤", machine.state().workflowInput().displayCell(0, 0),
                "LEFT cycles backward");
        machine.dispatch(CnCwKey.DEL);
        equal(">", machine.state().workflowInput().displayCell(0, 0),
                "DEL resets relation to default");
    }

    private void inequalityValidationLocatesBlankAndInvalidCells() {
        CnCwMachine blank = homeApplication(4);
        blank.dispatch(CnCwKey.OK);
        blank.dispatch(CnCwKey.EXE);
        check(!blank.state().resultShown(),
                "blank inequality does not enter result/error display");
        equal(1, blank.state().workflowInput().selectedColumn(),
                "blank inequality focuses first coefficient");
        check(blank.state().status().contains("空白"),
                "blank inequality reports missing input");

        CnCwMachine invalid = homeApplication(4);
        invalid.dispatch(CnCwKey.OK);
        invalid.dispatch(CnCwKey.OK); // relation -> leading coefficient
        press(invalid, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.EXE);
        check(!invalid.state().resultShown(),
                "invalid workflow expression stays on input page");
        equal(1, invalid.state().workflowInput().selectedColumn(),
                "invalid expression keeps focus on bad coefficient");
        check(invalid.state().status().contains("输入格式错误"),
                "invalid coefficient reports format error");
    }

    private void inequalityResultReturnsToPreservedInput() {
        CnCwMachine machine = homeApplication(4);
        machine.dispatch(CnCwKey.OK); // quadratic
        machine.dispatch(CnCwKey.OK); // relation -> a2
        press(machine, CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_0, CnCwKey.OK,
                CnCwKey.NEGATE, CnCwKey.DIGIT_1, CnCwKey.EXE);

        check(machine.state().resultShown(), "complete inequality reaches result state");
        check(!machine.state().result().isBlank(), "inequality result is non-empty");
        machine.dispatch(CnCwKey.BACK);
        check(machine.state().hasWorkflowInput() && !machine.state().resultShown(),
                "BACK returns from inequality result to input workflow");
        equal("1", machine.state().workflowInput().cell(0, 1),
                "leading coefficient survives result inspection");
        equal("0", machine.state().workflowInput().cell(0, 2),
                "middle coefficient survives result inspection");
        equal("-1", machine.state().workflowInput().cell(0, 3),
                "constant coefficient survives result inspection");
    }

    private void ratioStructuredWorkflowsRoundTrip() {
        CnCwMachine first = homeApplication(9);
        first.dispatch(CnCwKey.OK); // A:B=X:D
        check(first.state().hasWorkflowInput(), "first ratio opens structured workflow");
        equal("A", first.state().workflowInput().spec().fields().get(0).label(),
                "first ratio A label");
        equal("B", first.state().workflowInput().spec().fields().get(1).label(),
                "first ratio B label");
        equal("D", first.state().workflowInput().spec().fields().get(2).label(),
                "first ratio D label");
        press(first, CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_4, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.EXE);
        equal("X=5", first.state().result(), "A:B=X:D structured result");
        check(first.state().hasStructuredApplicationResult(),
                "first ratio publishes structured application result");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE,
                first.state().applicationResult().layout(),
                "ratio structured result uses KEY_VALUE layout");
        first.dispatch(CnCwKey.BACK);
        equal("10", first.state().workflowInput().cell(0, 2),
                "ratio fields survive BACK from result");

        CnCwMachine second = homeApplication(9);
        second.dispatch(CnCwKey.DOWN);
        second.dispatch(CnCwKey.OK); // A:B=C:X
        equal("C", second.state().workflowInput().spec().fields().get(2).label(),
                "second ratio C label");
        press(second, CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_4, CnCwKey.OK,
                CnCwKey.DIGIT_3, CnCwKey.EXE);
        equal("X=6", second.state().result(), "A:B=C:X structured result");
    }

    private void ratioLegacyCommaBridgeStillWorks() {
        CnCwMachine machine = homeApplication(9);
        machine.dispatch(CnCwKey.OK); // structured A:B=X:D
        check(machine.state().hasWorkflowInput(), "ratio starts structured before comma bridge");

        press(machine, CnCwKey.DIGIT_2, CnCwKey.COMMA);
        check(!machine.state().hasWorkflowInput(),
                "comma in first ratio field drops to legacy editor");
        press(machine, CnCwKey.DIGIT_4, CnCwKey.COMMA,
                CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.EXE);
        equal("X=5", machine.state().result(),
                "legacy comma ratio path still evaluates");
    }

    private void dualFunctionWorkflowKeepsInputOnBack() {
        CnCwMachine machine = homeApplication(2); // Function table.
        machine.dispatch(CnCwKey.OK); // f(x), g(x)
        check(machine.state().hasWorkflowInput(),
                "dual function command opens structured workflow");
        equal(5, machine.state().workflowInput().columns(),
                "dual function workflow has five fields");
        press(machine, CnCwKey.VAR_X, CnCwKey.OK,
                CnCwKey.VAR_X, CnCwKey.ADD, CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_6, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.EXE);
        check(machine.state().resultShown(), "dual function reaches table result");
        equal(CnCwModeEngine.ResultLayout.TABLE,
                machine.state().applicationResult().layout(),
                "dual function uses TABLE result layout");
        equal(3, machine.state().applicationResult().columns(),
                "dual function result has x/f/g columns");
        machine.dispatch(CnCwKey.BACK);
        check(machine.state().hasWorkflowInput() && !machine.state().resultShown(),
                "BACK returns to dual function input");
        equal("x+10", machine.state().workflowInput().cell(0, 1),
                "dual function g(x) survives result inspection");
    }

    private void statisticsActionsRespectRowBoundaries() {
        CnCwWorkflowSession session = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one"));
        CnCwWorkflowSession.Snapshot initial = session.snapshot();
        equal(4, initial.actions().size(), "statistics exposes four workflow actions");
        check(action(initial, CnCwWorkflowAction.Type.ADD_ROW).enabled(),
                "statistics can add a row at minimum size");
        check(!action(initial, CnCwWorkflowAction.Type.REMOVE_ROW).enabled(),
                "statistics remove is disabled at minimum rows");
        check(!action(initial, CnCwWorkflowAction.Type.EXECUTE).enabled(),
                "statistics execute is disabled while required input is blank");
        check(action(initial, CnCwWorkflowAction.Type.BACK).enabled(),
                "statistics back is always enabled");

        session.setCell(0, 0, "1");
        check(session.applyAction(CnCwWorkflowAction.Type.ADD_ROW),
                "statistics add row action mutates session");
        session.setCell(1, 0, "2");
        check(session.applyAction(CnCwWorkflowAction.Type.ADD_ROW),
                "statistics can add a third row");
        session.setCell(2, 0, "3");
        CnCwWorkflowSession.Snapshot populated = session.snapshot();
        equal(3, populated.rows(), "statistics keeps three entered rows");
        check(action(populated, CnCwWorkflowAction.Type.REMOVE_ROW).enabled(),
                "statistics remove becomes enabled above minimum rows");
        check(action(populated, CnCwWorkflowAction.Type.EXECUTE).enabled(),
                "statistics execute becomes enabled when all rows are valid");

        check(session.applyAction(CnCwWorkflowAction.Type.REMOVE_ROW),
                "statistics can remove selected third row");
        check(session.applyAction(CnCwWorkflowAction.Type.REMOVE_ROW),
                "statistics can return to minimum row count");
        check(!session.applyAction(CnCwWorkflowAction.Type.REMOVE_ROW),
                "statistics cannot remove below minimum row count");
        equal(1, session.snapshot().rows(), "statistics row count stays at minimum");
    }

    private void matrixActionsRespectDimensionBoundaries() {
        CnCwWorkflowSession session = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate"));
        CnCwWorkflowSession.Snapshot initial = session.snapshot();
        equal(6, initial.actions().size(), "matrix exposes six workflow actions");
        equal(CnCwWorkflowAction.Type.DECREASE_ROWS, initial.actions().get(0).type(),
                "matrix action 1 decreases rows");
        equal(CnCwWorkflowAction.Type.INCREASE_ROWS, initial.actions().get(1).type(),
                "matrix action 2 increases rows");
        equal(CnCwWorkflowAction.Type.DECREASE_COLUMNS, initial.actions().get(2).type(),
                "matrix action 3 decreases columns");
        equal(CnCwWorkflowAction.Type.INCREASE_COLUMNS, initial.actions().get(3).type(),
                "matrix action 4 increases columns");
        equal(CnCwWorkflowAction.Type.EXECUTE, initial.actions().get(4).type(),
                "matrix action 5 executes");
        equal(CnCwWorkflowAction.Type.BACK, initial.actions().get(5).type(),
                "matrix action 6 returns");
        check(!action(initial, CnCwWorkflowAction.Type.DECREASE_ROWS).enabled(),
                "matrix decrease rows disabled at minimum");
        check(action(initial, CnCwWorkflowAction.Type.INCREASE_ROWS).enabled(),
                "matrix increase rows enabled at minimum");
        check(!action(initial, CnCwWorkflowAction.Type.DECREASE_COLUMNS).enabled(),
                "matrix decrease columns disabled at minimum");
        check(action(initial, CnCwWorkflowAction.Type.INCREASE_COLUMNS).enabled(),
                "matrix increase columns enabled at minimum");

        for (int i = 0; i < 3; i++) {
            check(session.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                    "matrix grows one row within range");
            check(session.applyAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS),
                    "matrix grows one column within range");
        }
        CnCwWorkflowSession.Snapshot maximum = session.snapshot();
        equal(4, maximum.rows(), "matrix reaches maximum row count");
        equal(4, maximum.columns(), "matrix reaches maximum column count");
        check(!action(maximum, CnCwWorkflowAction.Type.INCREASE_ROWS).enabled(),
                "matrix increase rows disabled at maximum");
        check(!action(maximum, CnCwWorkflowAction.Type.INCREASE_COLUMNS).enabled(),
                "matrix increase columns disabled at maximum");
        check(!session.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                "matrix cannot grow beyond maximum rows");
        check(!session.applyAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS),
                "matrix cannot grow beyond maximum columns");

        check(session.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "matrix row decrease re-enables growth");
        check(session.applyAction(CnCwWorkflowAction.Type.DECREASE_COLUMNS),
                "matrix column decrease re-enables growth");
        CnCwWorkflowSession.Snapshot reduced = session.snapshot();
        equal(3, reduced.rows(), "matrix returns to three rows");
        equal(3, reduced.columns(), "matrix returns to three columns");
        check(action(reduced, CnCwWorkflowAction.Type.INCREASE_ROWS).enabled(),
                "matrix row growth is enabled again below maximum");
        check(action(reduced, CnCwWorkflowAction.Type.INCREASE_COLUMNS).enabled(),
                "matrix column growth is enabled again below maximum");
    }

    private void vectorActionsRespectDimensionBoundaries() {
        CnCwWorkflowSession session = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate"));
        CnCwWorkflowSession.Snapshot initial = session.snapshot();
        equal(6, initial.actions().size(), "vector exposes six workflow actions");
        equal(1, initial.rows(), "vector starts with one vector");
        equal(2, initial.columns(), "vector starts with two dimensions");
        check(!action(initial, CnCwWorkflowAction.Type.DECREASE_ROWS).enabled(),
                "vector decrease count disabled at minimum");
        check(action(initial, CnCwWorkflowAction.Type.INCREASE_ROWS).enabled(),
                "vector increase count enabled at minimum");
        check(!action(initial, CnCwWorkflowAction.Type.DECREASE_COLUMNS).enabled(),
                "vector decrease dimension disabled at minimum");
        check(action(initial, CnCwWorkflowAction.Type.INCREASE_COLUMNS).enabled(),
                "vector increase dimension enabled at minimum");

        check(session.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                "vector can grow to two vectors");
        check(session.applyAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS),
                "vector can grow to three dimensions");
        CnCwWorkflowSession.Snapshot maximum = session.snapshot();
        equal(2, maximum.rows(), "vector reaches maximum vector count");
        equal(3, maximum.columns(), "vector reaches maximum dimension");
        check(!action(maximum, CnCwWorkflowAction.Type.INCREASE_ROWS).enabled(),
                "vector increase count disabled at maximum");
        check(!action(maximum, CnCwWorkflowAction.Type.INCREASE_COLUMNS).enabled(),
                "vector increase dimension disabled at maximum");
        check(!session.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                "vector cannot exceed maximum vector count");
        check(!session.applyAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS),
                "vector cannot exceed maximum dimension");

        check(session.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "vector can return to one vector");
        check(session.applyAction(CnCwWorkflowAction.Type.DECREASE_COLUMNS),
                "vector can return to two dimensions");
        CnCwWorkflowSession.Snapshot minimum = session.snapshot();
        equal(1, minimum.rows(), "vector returns to minimum vector count");
        equal(2, minimum.columns(), "vector returns to minimum dimension");
        check(!action(minimum, CnCwWorkflowAction.Type.DECREASE_ROWS).enabled(),
                "vector decrease count disabled again at minimum");
        check(!action(minimum, CnCwWorkflowAction.Type.DECREASE_COLUMNS).enabled(),
                "vector decrease dimension disabled again at minimum");
    }

    private CnCwWorkflowAction action(CnCwWorkflowSession.Snapshot snapshot,
                                      CnCwWorkflowAction.Type type) {
        for (CnCwWorkflowAction action : snapshot.actions()) {
            if (action.type() == type) return action;
        }
        throw new AssertionError("missing workflow action " + type);
    }

    private CnCwMachine homeApplication(int index) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        for (int i = 0; i < index; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private static void press(CnCwMachine machine, CnCwKey... keys) {
        for (CnCwKey key : keys) machine.dispatch(key);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
