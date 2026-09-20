package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCalculationState;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.mode.CnCwModel;

/** Stage 6 compatibility checks for typed calculation-session snapshots. */
public final class CnCwCalculationStateSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwCalculationStateSuite().run();
    }

    private void run() {
        editingMirrorsLegacyState();
        exactResultMirrorsLegacyState();
        complexResultIsTyped();
        applicationResultIsTyped();
        errorMirrorsLegacyState();
        historyRetainsTypedPayloads();
        evaluationSnapshotRetainsTypedOutcome();
        System.out.println("PASS " + checks + " calculation-state checks");
    }

    private void editingMirrorsLegacyState() {
        CnCwMachine machine = calculateMachine();
        var state = machine.state();
        equal(CnCwCalculationState.Phase.EDITING, state.calculationState().phase(),
                "blank Calculate starts in typed editing phase");
        equal(CnCwCalculationState.ResultKind.NONE, state.calculationState().resultKind(),
                "editing has no result payload");
        check(!state.resultShown(), "legacy result flag remains false while editing");
    }

    private void exactResultMirrorsLegacyState() {
        CnCwMachine machine = calculateMachine();
        equal(3, machine.pasteExpression("2+3"), "paste simple expression");
        machine.dispatch(CnCwKey.EXE);
        var state = machine.state();
        equal(CnCwCalculationState.Phase.RESULT, state.calculationState().phase(),
                "successful evaluation publishes result phase");
        equal(CnCwCalculationState.ResultKind.EXACT, state.calculationState().resultKind(),
                "integer result publishes exact payload");
        equal("5", state.calculationState().display(), "typed display mirrors legacy result");
        check(state.calculationState().exactValue() != null, "exact payload retained");
        check(state.resultShown(), "legacy result flag remains true");
    }

    private void complexResultIsTyped() {
        CnCwMachine machine = calculateMachine();
        equal(1, machine.pasteExpression("i"), "paste imaginary unit");
        machine.dispatch(CnCwKey.EXE);
        var state = machine.state();
        equal(CnCwCalculationState.Phase.RESULT, state.calculationState().phase(),
                "complex evaluation publishes result phase");
        equal(CnCwCalculationState.ResultKind.COMPLEX, state.calculationState().resultKind(),
                "imaginary result publishes complex payload");
        check(state.calculationState().complexValue() != null, "complex payload retained");
    }

    private void applicationResultIsTyped() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK); // Statistics
        machine.dispatch(CnCwKey.OK); // one-variable workflow
        machine.pasteExpression("1");
        machine.dispatch(CnCwKey.OK);
        machine.pasteExpression("2");
        machine.dispatch(CnCwKey.EXE);
        var state = machine.state();
        equal(CnCwCalculationState.Phase.RESULT, state.calculationState().phase(),
                "structured workflow publishes result phase");
        equal(CnCwCalculationState.ResultKind.APPLICATION, state.calculationState().resultKind(),
                "statistics publishes application payload");
        check(state.calculationState().applicationResult() != null,
                "structured application payload retained");
        check(state.hasStructuredApplicationResult(),
                "legacy structured-result accessor remains compatible");
    }

    private void errorMirrorsLegacyState() {
        CnCwMachine machine = calculateMachine();
        machine.pasteExpression("1+");
        machine.dispatch(CnCwKey.EXE);
        var state = machine.state();
        equal(CnCwCalculationState.Phase.ERROR, state.calculationState().phase(),
                "invalid expression publishes typed error phase");
        equal(CnCwCalculationState.ResultKind.NONE, state.calculationState().resultKind(),
                "errors are not result payloads");
        check(state.calculationState().error() != null, "typed error keeps error kind");
        check(state.resultShown(), "legacy error still uses result surface during migration");
        machine.dispatch(CnCwKey.OK);
        equal(CnCwCalculationState.Phase.EDITING, machine.state().calculationState().phase(),
                "dismissing error returns typed editing phase");
    }

    private void historyRetainsTypedPayloads() {
        CnCwMachine machine = calculateMachine();
        machine.pasteExpression("1/3");
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwCalculationState.ResultKind.EXACT, machine.state().calculationState().resultKind(),
                "first history result is exact");

        machine.dispatch(CnCwKey.AC);
        machine.pasteExpression("i");
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwCalculationState.ResultKind.COMPLEX, machine.state().calculationState().resultKind(),
                "second history result is complex");

        machine.dispatch(CnCwKey.UP);
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCalculationState.ResultKind.EXACT, machine.state().calculationState().resultKind(),
                "recalling older history restores exact payload rather than current Ans type");
        check(machine.state().calculationState().exactValue() != null,
                "recalled exact history keeps exact value");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCalculationState.ResultKind.COMPLEX, machine.state().calculationState().resultKind(),
                "moving forward in history restores complex payload");
        check(machine.state().calculationState().complexValue() != null,
                "recalled complex history keeps complex value");
    }

    private void evaluationSnapshotRetainsTypedOutcome() {
        CnCwMachine machine = calculateMachine();
        machine.pasteExpression("i");
        machine.dispatch(CnCwKey.EXE);
        CnCwMachine copy = machine.copyForEvaluation();
        equal(CnCwCalculationState.ResultKind.COMPLEX, copy.state().calculationState().resultKind(),
                "evaluation copy retains committed complex payload");
        equal(machine.state().calculationState().display(), copy.state().calculationState().display(),
                "evaluation copy retains typed display");
    }

    private CnCwMachine calculateMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        return machine;
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
