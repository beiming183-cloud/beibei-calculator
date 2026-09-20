package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.math.ScientificConstants;
import com.codex.fx991.core.math.UnitConverter;
import com.codex.fx991.core.mode.CnCwModel;

import java.util.List;

/** Permanent user-entry guards for the last formerly partial 991 feature areas. */
public final class CnCwCompletenessSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwCompletenessSuite().run();
    }

    private void run() {
        complexApplicationRunsRectangularAndPolarPaths();
        complexDedicatedCommandsAreReachable();
        constantCatalogExposesEveryRegistryEntry();
        conversionCatalogExposesEveryRegistryEntry();
        formatterSettingsChangeRealResults();
        System.out.println("PASS " + checks + " completeness checks");
    }

    private void complexApplicationRunsRectangularAndPolarPaths() {
        CnCwMachine machine = openApplication(5);
        press(machine, CnCwKey.DIGIT_3, CnCwKey.ADD, CnCwKey.DIGIT_4,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE);
        equal("3+4i", machine.state().result(), "Complex app rectangular calculation");

        machine.dispatch(CnCwKey.FORMAT);
        int polar = commandIndex(machine.state().menuItems(), "polar");
        check(polar >= 0, "complex result exposes polar format");
        for (int i = 0; i < polar; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
        check(machine.state().result().contains("∠"), "complex result converts to polar form");
        machine.dispatch(CnCwKey.BACK);
        equal("3+4i", machine.state().result(), "BACK restores original complex result");

        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.DIGIT_5, CnCwKey.SHIFT, CnCwKey.DIGIT_8,
                CnCwKey.DIGIT_0, CnCwKey.EXE);
        equal("5", machine.state().result(), "polar input reaches complex evaluator");
    }

    private void complexDedicatedCommandsAreReachable() {
        CnCwMachine machine = openApplication(5);
        machine.dispatch(CnCwKey.CATALOG);
        moveDown(machine, 9);
        machine.dispatch(CnCwKey.OK);
        equal(com.codex.fx991.core.cw.CnCwScreen.CATALOG_COMPLEX, machine.state().screen(),
                "Complex catalog opens dedicated command group");
        equal(4, machine.state().menuItems().size(), "Complex catalog command count");
        machine.dispatch(CnCwKey.OK); // Conjg(
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE);
        equal("2−3i", machine.state().result(), "Conjg command runs through user catalog");

        machine = openApplication(5);
        machine.dispatch(CnCwKey.CATALOG); moveDown(machine, 9); machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DOWN); machine.dispatch(CnCwKey.OK); // Arg(
        press(machine, CnCwKey.DIGIT_1, CnCwKey.ADD, CnCwKey.SHIFT, CnCwKey.DIGIT_9,
                CnCwKey.EXE);
        near(45.0, machine.state().ans(), 1e-10, "Arg command");

        machine = openApplication(5);
        machine.dispatch(CnCwKey.CATALOG); moveDown(machine, 9); machine.dispatch(CnCwKey.OK);
        moveDown(machine, 2); machine.dispatch(CnCwKey.OK); // Re(
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE);
        near(2.0, machine.state().ans(), 1e-12, "Re command");

        machine = openApplication(5);
        machine.dispatch(CnCwKey.CATALOG); moveDown(machine, 9); machine.dispatch(CnCwKey.OK);
        moveDown(machine, 3); machine.dispatch(CnCwKey.OK); // Im(
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE);
        near(3.0, machine.state().ans(), 1e-12, "Im command");
    }

    private void constantCatalogExposesEveryRegistryEntry() {
        CnCwMachine machine = openApplication(0);
        machine.dispatch(CnCwKey.CATALOG);
        moveDown(machine, 6);
        machine.dispatch(CnCwKey.OK);

        int expectedGroups = ScientificConstants.categories().size();
        equal(expectedGroups, machine.state().menuItems().size(),
                "constant catalog exposes every category");
        int expectedItems = ScientificConstants.categories().values().stream()
                .mapToInt(List::size).sum();
        int visibleItems = 0;
        for (int group = 0; group < expectedGroups; group++) {
            if (group > 0) machine.dispatch(CnCwKey.DOWN);
            machine.dispatch(CnCwKey.OK);
            visibleItems += machine.state().menuItems().size();
            machine.dispatch(CnCwKey.BACK);
        }
        equal(expectedItems, visibleItems, "all scientific constants are user-reachable");
        equal(47, expectedItems, "manual scientific-constant catalog entry count");

        machine.dispatch(CnCwKey.OK); // last group: 其他
        machine.dispatch(CnCwKey.OK); // Celsius offset
        machine.dispatch(CnCwKey.EXE);
        near(273.15, machine.state().ans(), 1e-12,
                "catalog constant inserts an evaluable numeric token");
    }

    private void conversionCatalogExposesEveryRegistryEntry() {
        CnCwMachine machine = openApplication(0);
        machine.dispatch(CnCwKey.CATALOG);
        moveDown(machine, 7);
        machine.dispatch(CnCwKey.OK);

        int expectedGroups = UnitConverter.categories().size();
        equal(9, expectedGroups, "unit conversion catalog category count");
        equal(expectedGroups, machine.state().menuItems().size(),
                "unit conversion catalog exposes every category");
        int expectedItems = UnitConverter.categories().values().stream()
                .mapToInt(List::size).sum();
        int visibleItems = 0;
        for (int group = 0; group < expectedGroups; group++) {
            if (group > 0) machine.dispatch(CnCwKey.DOWN);
            machine.dispatch(CnCwKey.OK);
            visibleItems += machine.state().menuItems().size();
            machine.dispatch(CnCwKey.BACK);
        }
        equal(40, expectedItems, "all 40 conversion directions are registered");
        equal(expectedItems, visibleItems, "all unit conversions are user-reachable");

        machine.dispatch(CnCwKey.OK); // last group: 温度
        machine.dispatch(CnCwKey.OK); // °F→°C(
        press(machine, CnCwKey.DIGIT_3, CnCwKey.DIGIT_2,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(0.0, machine.state().ans(), 1e-12,
                "temperature conversion runs through the user catalog path");
    }

    private void formatterSettingsChangeRealResults() {
        CnCwMachine machine = openApplication(0);
        openCalculationSettings(machine);
        machine.dispatch(CnCwKey.OK); // Input/output options
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK); // Math input / decimal output
        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIVIDE, CnCwKey.DIGIT_2, CnCwKey.EXE);
        equal("0.5", machine.state().result(), "decimal-output setting suppresses exact fraction");

        machine = openApplication(0);
        toggleCalculationSetting(machine, 4); // mixed fraction
        press(machine, CnCwKey.DIGIT_7, CnCwKey.DIVIDE, CnCwKey.DIGIT_3, CnCwKey.EXE);
        equal("2 1/3", machine.state().result(), "mixed-fraction setting reaches formatter");

        machine = openApplication(0);
        toggleCalculationSetting(machine, 3); // engineering symbols
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.DIGIT_0,
                CnCwKey.DIGIT_0, CnCwKey.SHIFT, CnCwKey.EXE);
        check(machine.state().result().contains("k"), "engineering-symbol setting reaches formatter");

        machine = openApplication(0);
        toggleCalculationSetting(machine, 6); // decimal comma
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIVIDE, CnCwKey.DIGIT_8,
                CnCwKey.SHIFT, CnCwKey.EXE);
        equal("0,125", machine.state().result(), "decimal-mark setting reaches formatter");

        machine = openApplication(0);
        toggleCalculationSetting(machine, 7); // digit separator
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_4, CnCwKey.DIGIT_5, CnCwKey.DIGIT_6,
                CnCwKey.DIGIT_7, CnCwKey.SHIFT, CnCwKey.EXE);
        equal("1,234,567", machine.state().result(), "digit-separator setting reaches formatter");

        machine = openApplication(0);
        toggleCalculationSetting(machine, 5); // polar complex output
        machine.dispatch(CnCwKey.HOME);
        moveRight(machine, 5);
        machine.dispatch(CnCwKey.OK);
        press(machine, CnCwKey.DIGIT_3, CnCwKey.ADD, CnCwKey.DIGIT_4,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE);
        check(machine.state().result().contains("∠"), "complex-format setting reaches evaluator output");
    }

    private static CnCwMachine openApplication(int homeIndex) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        moveRight(machine, homeIndex);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private static void openCalculationSettings(CnCwMachine machine) {
        machine.dispatch(CnCwKey.SETTINGS);
        machine.dispatch(CnCwKey.OK);
    }

    private static void toggleCalculationSetting(CnCwMachine machine, int row) {
        openCalculationSettings(machine);
        moveDown(machine, row);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.AC);
    }

    private static int commandIndex(List<CnCwCommand> commands, String id) {
        for (int i = 0; i < commands.size(); i++) {
            if (id.equals(commands.get(i).id())) return i;
        }
        return -1;
    }

    private static void moveRight(CnCwMachine machine, int count) {
        for (int i = 0; i < count; i++) machine.dispatch(CnCwKey.RIGHT);
    }

    private static void moveDown(CnCwMachine machine, int count) {
        for (int i = 0; i < count; i++) machine.dispatch(CnCwKey.DOWN);
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

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
