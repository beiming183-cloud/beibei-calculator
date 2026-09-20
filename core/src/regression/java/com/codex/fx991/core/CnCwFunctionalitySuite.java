package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwBaseNWorkflow;
import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.util.List;

/** Functionality-first guards for user-visible commands that used to be partial. */
public final class CnCwFunctionalitySuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwFunctionalitySuite().run();
    }

    private void run() {
        statisticsMenuExposesAllRegressionFamilies();
        allRegressionFamiliesReachTheirEngines();
        quadraticRegressionRunsThroughMachineWorkflow();
        matrixSlotsPersistAndOperate();
        vectorSlotsPersistAndOperate();
        baseNOperationsReachUserWorkflows();
        statisticsFrequencyColumnsReachWeightedEngines();
        resetClearsStoredLinearAlgebra();
        linearAnswerMemoriesClearWhenLeavingApps();
        appSwitchClearsTransientErrorAndVerificationState();
        System.out.println("PASS " + checks + " functionality checks");
    }

    private void statisticsMenuExposesAllRegressionFamilies() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        List<CnCwCommand> commands = machine.state().modeCommands();
        equal(18, commands.size(), "statistics command count including frequency variants");
        String[] expected = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        for (int index = 0; index < expected.length; index++) {
            equal(expected[index], commands.get(index + 2).id(),
                    "statistics regression command " + index);
            CnCwWorkflowSpec.WorkflowSpec spec = CnCwWorkflowSpec.forCommand(
                    ApplicationMode.STATISTICS, expected[index]);
            check(spec != null, expected[index] + " owns a structured x/y workflow");
            equal(2, spec.minColumns(), expected[index] + " x/y columns");
        }
        equal(3, CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS,
                "reg-quadratic").minRows(), "quadratic starts with enough rows to solve");
    }

    private void allRegressionFamiliesReachTheirEngines() {
        String[] commands = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        String[] titles = {"线性回归", "二次回归", "对数回归", "e 指数回归",
                "ab^x 回归", "幂回归", "逆数回归"};
        String data = "1,2,2,4,3,8,4,16";
        for (int index = 0; index < commands.length; index++) {
            CnCwModeEngine.ModeResult result = CnCwModeEngine.evaluate(
                    ApplicationMode.STATISTICS, commands[index], data,
                    ScalarExpressionEngine.EvaluationContext.standard());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    commands[index] + " structured result");
            equal(titles[index], result.title(), commands[index] + " result title");
            equal(3, result.items().size(), commands[index] + " coefficient item count");
            check(!result.display().isBlank(), commands[index] + " visible result");
        }
        equal("线性回归", CnCwModeEngine.evaluate(ApplicationMode.STATISTICS,
                "regression", data, ScalarExpressionEngine.EvaluationContext.standard()).title(),
                "legacy regression alias");
    }

    private void quadraticRegressionRunsThroughMachineWorkflow() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
        check(machine.state().hasWorkflowInput(), "quadratic regression opens x/y editor");
        equal(3, machine.state().workflowInput().rows(),
                "quadratic regression begins with three data rows");

        enter(machine, "1"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "2"); machine.dispatch(CnCwKey.OK);
        enter(machine, "7"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "13"); machine.dispatch(CnCwKey.EXE);

        check(machine.state().resultShown(), "quadratic regression reaches result");
        equal("二次回归", machine.state().applicationResult().title(),
                "quadratic regression result type survives Machine path");
        near(1.0, parse(machine, 0), 1e-10, "quadratic a coefficient");
        near(1.0, parse(machine, 1), 1e-10, "quadratic b coefficient");
        near(1.0, parse(machine, 2), 1e-10, "quadratic c coefficient");
    }

    private void statisticsFrequencyColumnsReachWeightedEngines() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 1, 9); // one-variable with frequency
        equal(2, machine.state().workflowInput().columns(), "one-freq exposes x/frequency columns");
        machine.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);
        equal(1, machine.state().workflowInput().selectedRow(),
                "adding a statistics row focuses the new row");
        machine.selectWorkflowCell(0, 0);
        fillGrid(machine, "10", "2", "20", "1");
        equal("3", machine.state().applicationResult().items().get(0).value(),
                "weighted one-variable sample count is sum of frequencies");
        near(40.0 / 3.0, parse(machine, 1), 1e-10, "weighted one-variable mean");

        CnCwModeEngine.ModeResult two = CnCwModeEngine.evaluate(ApplicationMode.STATISTICS,
                "two-freq", "1,2,2,3,4,1", ScalarExpressionEngine.EvaluationContext.standard());
        near(5.0 / 3.0, Double.parseDouble(two.items().get(0).value()), 1e-10,
                "weighted two-variable mean x");

        String[] commands = {"reg-linear-freq", "reg-quadratic-freq", "reg-logarithmic-freq",
                "reg-e-exponential-freq", "reg-ab-exponential-freq",
                "reg-power-freq", "reg-inverse-freq"};
        String weighted = "1,2,2,2,4,1,3,8,1,4,16,1";
        for (String command : commands) {
            CnCwWorkflowSpec.WorkflowSpec spec = CnCwWorkflowSpec.forCommand(
                    ApplicationMode.STATISTICS, command);
            equal(3, spec.minColumns(), command + " exposes x/y/frequency columns");
            CnCwModeEngine.ModeResult result = CnCwModeEngine.evaluate(
                    ApplicationMode.STATISTICS, command, weighted,
                    ScalarExpressionEngine.EvaluationContext.standard());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    command + " returns structured weighted regression");
            check(result.title().contains("频数"), command + " is visibly frequency-aware");
        }
    }

    private void resetClearsStoredLinearAlgebra() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 7, 0);
        enter(machine, "5");
        machine.dispatch(CnCwKey.EXE);
        equal("MatA 已保存", machine.state().applicationResult().title(), "MatA stored before reset");
        machine.reset();
        openCommand(machine, 7, 5);
        machine.dispatch(CnCwKey.EXE);
        check(machine.state().calculationState().isError(), "reset clears MatA memory");
    }

    private void baseNOperationsReachUserWorkflows() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 6, 4); // conversion
        machine.dispatch(CnCwKey.OK);    // source DEC -> target
        machine.dispatch(CnCwKey.RIGHT); // target HEX
        machine.dispatch(CnCwKey.OK);    // value
        enter(machine, "255");
        machine.dispatch(CnCwKey.EXE);
        equal("FF", machine.state().applicationResult().items().get(0).value(),
                "DEC 255 converts to HEX FF");
        near(255.0, machine.state().ans(), 0.0, "Base-N conversion stores numeric Ans");

        openCommand(machine, 6, 12); // OR
        machine.dispatch(CnCwKey.RIGHT); // HEX
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.VAR_A);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.EXE);
        equal("F", machine.state().applicationResult().items().get(0).value(),
                "HEX A OR 5 = F through Machine path");

        String[] commands = {"base-add", "base-subtract", "base-multiply", "base-divide",
                "base-negate", "base-not", "base-and", "base-or", "base-xor", "base-xnor"};
        for (String command : commands) {
            CnCwWorkflowSession session = CnCwWorkflowSession.create(
                    CnCwWorkflowSpec.forCommand(ApplicationMode.BASE_N, command));
            session.setCell(0, 0, "10");
            session.setCell(0, 1, "6");
            if (session.columns() == 3) session.setCell(0, 2, "3");
            CnCwModeEngine.ModeResult result = CnCwBaseNWorkflow.evaluate(command,
                    session.snapshot());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    command + " structured Base-N result");
            check(!result.items().isEmpty(), command + " has visible formatted value");
        }

        openCommand(machine, 6, 2); // existing BIN mode remains compatible
        enter(machine, "1010");
        machine.dispatch(CnCwKey.EXE);
        equal("1010", machine.state().result(), "legacy BIN parse/format remains reachable");
        near(10.0, machine.state().ans(), 0.0, "legacy BIN numeric value remains 10");
    }

    private void matrixSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 7, 0); // define MatA
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "2", "3", "4");
        check(machine.state().resultShown(), "MatA definition reaches result");
        equal("MatA 已保存", machine.state().applicationResult().title(), "MatA stored title");

        openCommand(machine, 7, 2); // define MatB
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "0", "0", "1");
        equal("MatB 已保存", machine.state().applicationResult().title(), "MatB stored title");

        openCommand(machine, 7, 5); // det(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, machine.state().applicationResult().layout(),
                "stored determinant result layout");
        near(-2.0, parse(machine, 0), 1e-10, "det(MatA)");

        openCommand(machine, 7, 6); // inverse(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.MATRIX, machine.state().applicationResult().layout(),
                "stored inverse returns matrix");
        equal(4, machine.state().applicationResult().cells().size(), "inverse keeps 2x2 shape");

        openCommand(machine, 7, 7); // transpose(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().applicationResult().cells().get(1),
                "transpose swaps off-diagonal cell");

        openBinaryChoiceCommand(machine, 7, 8); // MatA + MatB
        equal("2", machine.state().applicationResult().cells().get(0), "matrix add [1,1]");
        equal("5", machine.state().applicationResult().cells().get(3), "matrix add [2,2]");

        openBinaryChoiceCommand(machine, 7, 9); // MatA - MatB
        equal("0", machine.state().applicationResult().cells().get(0), "matrix subtract [1,1]");
        equal("3", machine.state().applicationResult().cells().get(3), "matrix subtract [2,2]");

        openBinaryChoiceCommand(machine, 7, 10); // MatA * MatB
        equal("1", machine.state().applicationResult().cells().get(0), "matrix multiply identity");
        equal("4", machine.state().applicationResult().cells().get(3), "matrix multiply identity last");

        openCommand(machine, 7, 11); // MatA^2
        machine.dispatch(CnCwKey.EXE);
        equal("7", machine.state().applicationResult().cells().get(0), "matrix square [1,1]");
        equal("22", machine.state().applicationResult().cells().get(3), "matrix square [2,2]");

        openCommand(machine, 7, 12); // MatA^3
        machine.dispatch(CnCwKey.EXE);
        equal("37", machine.state().applicationResult().cells().get(0), "matrix cube [1,1]");
        equal("118", machine.state().applicationResult().cells().get(3), "matrix cube [2,2]");

        openCommand(machine, 7, 13); // Identity(3)
        enter(machine, "3");
        machine.dispatch(CnCwKey.EXE);
        equal(3, machine.state().applicationResult().rows(), "identity matrix rows");
        equal(3, machine.state().applicationResult().columns(), "identity matrix columns");
        equal("1", machine.state().applicationResult().cells().get(0), "identity diagonal");
        equal("0", machine.state().applicationResult().cells().get(1), "identity off diagonal");

        openCommand(machine, 7, 14); // Abs(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().applicationResult().cells().get(2), "matrix element abs");

        openCommand(machine, 7, 15); // MatAns
        equal("MatAns", machine.state().applicationResult().title(),
                "MatAns uses the manual answer-memory title");
        equal("3", machine.state().applicationResult().cells().get(2),
                "MatAns preserves the last matrix payload");
    }

    private void vectorSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 8, 0); // define VctA
        fillGrid(machine, "3", "4");
        equal("VctA 已保存", machine.state().applicationResult().title(), "VctA stored title");

        openCommand(machine, 8, 2); // define VctB
        fillGrid(machine, "0", "1");
        equal("VctB 已保存", machine.state().applicationResult().title(), "VctB stored title");

        openCommand(machine, 8, 5); // magnitude A
        machine.dispatch(CnCwKey.EXE);
        near(5.0, parse(machine, 0), 1e-10, "|VctA|");

        openCommand(machine, 8, 6); // unit A
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.VECTOR, machine.state().applicationResult().layout(),
                "unit vector layout");

        openBinaryChoiceCommand(machine, 8, 7); // A+B
        equal("3", machine.state().applicationResult().cells().get(0), "vector add x");
        equal("5", machine.state().applicationResult().cells().get(1), "vector add y");

        openBinaryChoiceCommand(machine, 8, 8); // A-B
        equal("3", machine.state().applicationResult().cells().get(0), "vector subtract x");
        equal("3", machine.state().applicationResult().cells().get(1), "vector subtract y");

        openBinaryChoiceCommand(machine, 8, 9); // dot
        near(4.0, parse(machine, 0), 1e-10, "VctA dot VctB");

        openBinaryChoiceCommand(machine, 8, 10); // cross
        equal(3, machine.state().applicationResult().columns(), "2D cross publishes 3D vector");
        equal("3", machine.state().applicationResult().cells().get(2), "2D cross z component");

        openBinaryChoiceCommand(machine, 8, 11); // angle
        check(parse(machine, 0) > 36.0 && parse(machine, 0) < 37.0,
                "VctA/VctB angle is about 36.87 degrees");

        openBinaryChoiceCommand(machine, 8, 7); // A+B -> VctAns
        openCommand(machine, 8, 12); // VctAns
        equal("VctAns", machine.state().applicationResult().title(),
                "VctAns uses the manual answer-memory title");
        equal("3", machine.state().applicationResult().cells().get(0), "VctAns x");
        equal("5", machine.state().applicationResult().cells().get(1), "VctAns y");
    }

    private void linearAnswerMemoriesClearWhenLeavingApps() {
        CnCwMachine matrix = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(matrix, 7, 0);
        enter(matrix, "5");
        matrix.dispatch(CnCwKey.EXE);
        openCommand(matrix, 7, 11); // MatA^2 -> MatAns
        matrix.dispatch(CnCwKey.EXE);
        equal("25", matrix.state().applicationResult().cells().get(0),
                "matrix result populates MatAns before app switch");
        matrix.dispatch(CnCwKey.HOME);
        matrix.dispatch(CnCwKey.OK); // launch Calculate: this clears MatAns only
        openCommand(matrix, 7, 15);
        check(matrix.state().calculationState().isError(),
                "launching another app clears MatAns");
        openCommand(matrix, 7, 5); // MatA itself must persist
        matrix.dispatch(CnCwKey.EXE);
        near(5.0, parse(matrix, 0), 1e-10,
                "launching another app keeps MatA slot data");

        CnCwMachine vector = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(vector, 8, 0);
        fillGrid(vector, "3", "4");
        openCommand(vector, 8, 2);
        fillGrid(vector, "1", "2");
        openBinaryChoiceCommand(vector, 8, 7); // VctA+VctB -> VctAns
        equal("4", vector.state().applicationResult().cells().get(0),
                "vector result populates VctAns before app switch");
        vector.dispatch(CnCwKey.HOME);
        vector.dispatch(CnCwKey.OK); // launch Calculate
        openCommand(vector, 8, 12);
        check(vector.state().calculationState().isError(),
                "launching another app clears VctAns");
        openCommand(vector, 8, 5); // VctA remains available
        vector.dispatch(CnCwKey.EXE);
        near(5.0, parse(vector, 0), 1e-10,
                "launching another app keeps VctA slot data");
    }

    private void appSwitchClearsTransientErrorAndVerificationState() {
        CnCwMachine verify = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        verify.dispatch(CnCwKey.OK); // Calculate
        verify.dispatch(CnCwKey.TOOLS);
        verify.dispatch(CnCwKey.DOWN);
        verify.dispatch(CnCwKey.DOWN);
        verify.dispatch(CnCwKey.OK); // verification on
        check(verify.state().verificationMode(), "verification can be enabled in Calculate");

        verify.dispatch(CnCwKey.HOME);
        verify.dispatch(CnCwKey.OK); // re-open the same Calculate app
        check(verify.state().verificationMode(),
                "HOME then same app preserves verification mode");

        verify.dispatch(CnCwKey.HOME);
        verify.dispatch(CnCwKey.RIGHT);
        verify.dispatch(CnCwKey.OK); // Statistics is a different app
        check(!verify.state().verificationMode(),
                "HOME then another app disables verification mode");

        CnCwMachine error = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(error, 7, 15); // MatAns is undefined
        check(error.state().calculationState().isError(),
                "undefined MatAns publishes an error before app switch");
        error.dispatch(CnCwKey.HOME);
        error.dispatch(CnCwKey.OK); // Calculate
        check(!error.state().calculationState().isError(),
                "opening a new app clears the previous error overlay");
        check(!error.state().resultShown(),
                "opening a new app returns to a clean editing state");
    }

    private static void openCommand(CnCwMachine machine, int homeIndex, int commandIndex) {
        machine.dispatch(CnCwKey.HOME);
        for (int i = 0; i < homeIndex; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < commandIndex; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
    }

    private static void openBinaryChoiceCommand(CnCwMachine machine,
                                                 int homeIndex, int commandIndex) {
        openCommand(machine, homeIndex, commandIndex);
        machine.dispatch(CnCwKey.OK); // first slot -> second slot
        machine.dispatch(CnCwKey.RIGHT); // A -> B
        machine.dispatch(CnCwKey.EXE);
    }

    private static void fillGrid(CnCwMachine machine, String... values) {
        for (int index = 0; index < values.length; index++) {
            enter(machine, values[index]);
            machine.dispatch(index + 1 == values.length ? CnCwKey.EXE : CnCwKey.OK);
        }
    }

    private static double parse(CnCwMachine machine, int index) {
        return Double.parseDouble(machine.state().applicationResult().items().get(index).value()
                .replace('−', '-').replace("°", ""));
    }

    private static void enter(CnCwMachine machine, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            machine.dispatch(switch (digits.charAt(i)) {
                case '0' -> CnCwKey.DIGIT_0;
                case '1' -> CnCwKey.DIGIT_1;
                case '2' -> CnCwKey.DIGIT_2;
                case '3' -> CnCwKey.DIGIT_3;
                case '4' -> CnCwKey.DIGIT_4;
                case '5' -> CnCwKey.DIGIT_5;
                case '6' -> CnCwKey.DIGIT_6;
                case '7' -> CnCwKey.DIGIT_7;
                case '8' -> CnCwKey.DIGIT_8;
                default -> CnCwKey.DIGIT_9;
            });
        }
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

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
