package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwExpressionNode;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwScreen;
import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.mode.CnCwModel;

/** Golden interaction traces for the clean-room CN CW shell. */
public final class CnCwMachineSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwMachineSuite().run();
    }

    private void run() {
        productProfilesHaveSeparateHomeSets();
        homeAndApplicationNavigation();
        homeSettingsOpensSettings();
        calculateUsesManualPrecedence();
        semanticTokensDoNotCollapseDuringEvaluation();
        clipboardPastePreservesExpressionSemantics();
        ansTokenCanBeSelectedAndCopied();
        ansProcessExpandsForInspection();
        calculateKeepsExactStandardResults();
        imaginaryUnitWorksInCalculate();
        shiftedExeForcesDecimalResult();
        naturalConstantEUsesManualDisplayPrecision();
        percentUsesPostfixPrecedence();
        multiStatementAdvancesOneExeAtATime();
        calculationErrorsReturnToSourcePosition();
        executeAndRelationAreSeparate();
        verificationStoresBooleanAns();
        verificationChainsAndPersistsAcrossHome();
        evaluationSnapshotIsIsolated();
        catalogManualUtilitiesAreReachable();
        catalogConversionAngleAndCoordinatePathsWork();
        manualSimpUsesStepAndSpecifiedFactor();
        formatMenuIsDynamicReversibleAndEngInteractive();
        randomKeyUsesCoreFunctionName();
        shiftIsOneShotAndAngleAware();
        manualShiftSevenInputsPi();
        physicalShiftLegendsAndFractionKey();
        naturalPowerTemplatesPublishSuperscripts();
        answerPowerContinuesFromResult();
        shiftedLogFunctionsWork();
        functionDefinitionsPersistAndEvaluate();
        unfinishedFunctionParenthesisIsCompleted();
        semanticDeleteAndCursor();
        semanticSelectionSupportsDeleteAndReplace();
        touchSelectionCanAnchorAndExtend();
        touchSelectionHandlesMoveIndependently();
        directTouchCursorMovesAtomically();
        shiftedDeleteTogglesOverwriteAndOnIsDistinct();
        settingsAreMachineOwned();
        fixDigitsDriveRndOnMainKeyPath();
        menusCloseByAcAndHomeAlwaysWins();
        applicationLandingIsDeterministic();
        structuredModesReachCoreEngines();
        structuredWorkflowInputPageRunsEndToEnd();
        workflowActionsCanBeAppliedFromMachine();
        spreadsheetCompactWorkflowPersistsCells();
        spreadsheetGridMovesAndCommitsSelectedCell();
        System.out.println("PASS " + checks + " CN CW machine checks");
    }

    private void productProfilesHaveSeparateHomeSets() {
        CnCwMachine cn991 = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        CnCwMachine cn999 = new CnCwMachine(CnCwModel.FX_999_CN_CW);
        equal(10, cn991.state().homeItems().size(), "991 home application count");
        equal(12, cn999.state().homeItems().size(), "999 home application count");
        check(cn999.state().homeItems().stream().anyMatch(item -> item.id().equals("DISTRIBUTION")),
                "999 includes Distribution");
        check(cn991.state().homeItems().stream().noneMatch(item -> item.id().equals("SPREADSHEET")),
                "991 excludes Spreadsheet");
    }

    private void homeAndApplicationNavigation() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        equal(CnCwScreen.HOME, machine.state().screen(), "power-on HOME");
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        equal(CnCwScreen.STATISTICS, machine.state().screen(), "HOME opens selected app");
        check(machine.state().applicationLanding(), "structured app starts at its command menu");
        machine.dispatch(CnCwKey.HOME);
        equal(CnCwScreen.HOME, machine.state().screen(), "HOME returns globally");
    }

    private void homeSettingsOpensSettings() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.SETTINGS);
        equal(CnCwScreen.SETTINGS, machine.state().screen(),
                "HOME settings opens settings root");
        check(machine.state().menuItems().get(0).label().contains("计算"),
                "settings root exposes calculation settings");
        machine.dispatch(CnCwKey.BACK);
        equal(CnCwScreen.HOME, machine.state().screen(), "settings BACK returns HOME");
    }

    private void calculateUsesManualPrecedence() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3,
                CnCwKey.MULTIPLY, CnCwKey.DIGIT_4, CnCwKey.EXE);
        near(14.0, machine.state().ans(), 0.0, "operator precedence");
        equal("14", machine.state().result(), "formatted result");
        check(machine.state().applicationResult() == null,
                "ordinary Calculate result does not publish application protocol");

        press(machine, CnCwKey.DIVIDE, CnCwKey.DIGIT_2, CnCwKey.EXE);
        near(7.0, machine.state().ans(), 0.0, "binary continuation starts from Ans");
    }

    private void semanticTokensDoNotCollapseDuringEvaluation() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.SQUARE, CnCwKey.DIGIT_3,
                CnCwKey.EXE);
        near(12.0, machine.state().ans(), 0.0,
                "square token followed by digit is implicit multiplication");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.VAR_E, CnCwKey.EXE);
        near(0.0, machine.state().ans(), 0.0,
                "E variable cannot merge into scientific notation");

        machine = calculateMachine();
        press(machine, CnCwKey.VAR_A, CnCwKey.VAR_B, CnCwKey.EXE);
        near(0.0, machine.state().ans(), 0.0,
                "adjacent variables remain separate implicit factors");
    }

    private void clipboardPastePreservesExpressionSemantics() {
        CnCwMachine source = calculateMachine();
        press(source, CnCwKey.DIGIT_5, CnCwKey.DIGIT_6, CnCwKey.SQUARE);
        equal("56^2", source.state().expression(), "square copy uses evaluator source");

        CnCwMachine pasted = calculateMachine();
        check(pasted.pasteExpression(source.state().expression()) > 0,
                "evaluator source can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3136", pasted.state().result(), "56^2 round-trips through clipboard");

        pasted = calculateMachine();
        check(pasted.pasteExpression("56²") > 0, "display superscript can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3136", pasted.state().result(), "56² keeps square semantics");

        pasted = calculateMachine();
        check(pasted.pasteExpression("√(9)") > 0, "display square-root can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3", pasted.state().result(), "square-root paste does not duplicate parenthesis");

        pasted = calculateMachine();
        check(pasted.pasteExpression("π/2") > 0, "pi display form can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("π/2", pasted.state().result(), "pi paste keeps exact semantics");

        pasted = calculateMachine();
        check(pasted.pasteExpression("i^2") > 0, "imaginary expression can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("-1", pasted.state().result(), "imaginary-unit paste uses complex engine");

        pasted = calculateMachine();
        press(pasted, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3, CnCwKey.EXE);
        check(pasted.pasteExpression("Ans+1") > 0, "Ans source can be pasted after result");
        pasted.dispatch(CnCwKey.EXE);
        equal("6", pasted.state().result(), "paste after result starts new expression but keeps Ans");

        pasted = calculateMachine();
        press(pasted, CnCwKey.DIGIT_7);
        equal(-1, pasted.pasteExpression("56@2"), "unsupported paste is rejected atomically");
        equal("7", pasted.state().expression(), "invalid paste cannot silently drop symbols");

        pasted = calculateMachine();
        check(pasted.pasteExpression("1E3") > 0, "scientific E literal can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("1000", pasted.state().result(), "scientific E literal keeps numeric meaning");
    }

    private void ansTokenCanBeSelectedAndCopied() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3, CnCwKey.EXE);
        machine.dispatch(CnCwKey.ANS);
        equal("Ans", machine.state().expression(), "Ans key publishes evaluator source");
        machine.selectTouchWord(0);
        check(machine.state().hasSelection(), "Ans token can be touch-selected");
        equal("Ans", machine.selectedExpression(), "selected Ans exports to clipboard source");
    }

    private void ansProcessExpandsForInspection() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3, CnCwKey.EXE);
        equal("2+3", machine.calculationProcessDisplay(),
                "first result keeps its readable calculation process");

        press(machine, CnCwKey.MULTIPLY, CnCwKey.DIGIT_2, CnCwKey.EXE);
        equal("Ans*2", machine.state().expression(),
                "Ans remains evaluator semantics internally");
        equal("(2+3)×2", machine.calculationProcessDisplay(),
                "inspection process expands Ans to previous calculation");
        equal("10", machine.state().result(), "expanded inspection does not alter result");

        press(machine, CnCwKey.ADD, CnCwKey.DIGIT_1, CnCwKey.EXE);
        equal("Ans+1", machine.state().expression(),
                "second continuation still keeps internal Ans token");
        equal("((2+3)×2)+1", machine.calculationProcessDisplay(),
                "Ans process expansion is recursive across calculations");
        equal("11", machine.state().result(), "recursive display expansion does not alter arithmetic");
    }

    private void calculateKeepsExactStandardResults() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_7, CnCwKey.DIVIDE,
                CnCwKey.DIGIT_6, CnCwKey.EXE);
        equal("π/6", machine.state().result(), "pi fraction standard result");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_3, CnCwKey.MULTIPLY, CnCwKey.SQRT,
                CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("3√2", machine.state().result(), "surd standard result");

        machine = calculateMachine();
        press(machine, CnCwKey.SIN, CnCwKey.DIGIT_3, CnCwKey.DIGIT_0,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("1/2", machine.state().result(), "special angle standard result");
    }

    private void imaginaryUnitWorksInCalculate() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_9,
                CnCwKey.POWER, CnCwKey.DIGIT_2, CnCwKey.EXE);
        equal("-1", machine.state().result(), "i squared evaluates in Calculate mode");

        machine = calculateMachine();
        press(machine, CnCwKey.OPEN_PAREN, CnCwKey.DIGIT_1, CnCwKey.ADD,
                CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.CLOSE_PAREN,
                CnCwKey.POWER, CnCwKey.DIGIT_2, CnCwKey.EXE);
        equal("2i", machine.state().result(), "complex power uses the complex engine");

        machine = calculateMachine();
        press(machine, CnCwKey.SQRT, CnCwKey.SUBTRACT, CnCwKey.DIGIT_1,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("i", machine.state().result(), "sqrt negative upgrades to complex evaluation");

        machine = calculateMachine();
        press(machine, CnCwKey.OPEN_PAREN, CnCwKey.SUBTRACT, CnCwKey.DIGIT_1,
                CnCwKey.CLOSE_PAREN, CnCwKey.POWER, CnCwKey.OPEN_PAREN,
                CnCwKey.DIGIT_1, CnCwKey.DIVIDE, CnCwKey.DIGIT_2,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("i", machine.state().result(), "negative fractional power upgrades to complex evaluation");

        machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_9, CnCwKey.EXE,
                CnCwKey.ADD, CnCwKey.DIGIT_1, CnCwKey.EXE);
        equal("1+i", machine.state().result(), "complex Ans keeps its imaginary part");
    }

    private void shiftedExeForcesDecimalResult() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_7, CnCwKey.DIVIDE,
                CnCwKey.DIGIT_6, CnCwKey.SHIFT, CnCwKey.EXE);
        equal("0.5235987756", machine.state().result(),
                "SHIFT+EXE requests decimal output for this calculation");
    }

    private void naturalConstantEUsesManualDisplayPrecision() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.NEGATE, CnCwKey.EXE);
        equal("2.718281828", machine.state().result(),
                "e uses the manual's ten-digit decimal display");
    }

    private void percentUsesPostfixPrecedence() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_6, CnCwKey.DIGIT_6, CnCwKey.DIGIT_0,
                CnCwKey.DIVIDE, CnCwKey.DIGIT_8, CnCwKey.DIGIT_8,
                CnCwKey.DIGIT_0, CnCwKey.PERCENT, CnCwKey.EXE);
        near(75.0, machine.state().ans(), 0.0, "percent binds to preceding value");
        equal("75", machine.state().result(), "660 divided by 880 percent");
    }

    private void multiStatementAdvancesOneExeAtATime() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_3, CnCwKey.ADD, CnCwKey.DIGIT_3,
                CnCwKey.CATALOG);
        for (int i = 0; i < 8; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Other / relations
        for (int i = 0; i < 7; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // colon
        press(machine, CnCwKey.DIGIT_3, CnCwKey.MULTIPLY, CnCwKey.DIGIT_3,
                CnCwKey.EXE);
        equal("6", machine.state().result(), "first EXE shows first statement only");
        near(6.0, machine.state().ans(), 0.0, "first statement advances Ans");
        machine.dispatch(CnCwKey.EXE);
        equal("9", machine.state().result(), "second EXE shows second statement");
        near(9.0, machine.state().ans(), 0.0, "second statement advances Ans");
    }

    private void calculationErrorsReturnToSourcePosition() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_4, CnCwKey.DIVIDE,
                CnCwKey.DIGIT_0, CnCwKey.MULTIPLY, CnCwKey.DIGIT_2,
                CnCwKey.EXE);
        equal("Math ERROR", machine.state().result(), "division by zero category");
        equal(3, machine.state().cursor(), "error cursor points at zero");
        machine.dispatch(CnCwKey.AC);
        equal("│", machine.state().displayText(),
                "AC clears the errored expression in one press");
        check(!machine.state().resultShown(), "AC clears the error result in one press");
    }

    private void randomKeyUsesCoreFunctionName() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.RAN, CnCwKey.EXE);
        check(machine.state().hasAns(), "Ran# evaluates instead of producing Syntax ERROR");
        check(machine.state().ans() >= 0.0 && machine.state().ans() <= 0.999,
                "Ran# stays in the manual's 0.000 to 0.999 range");
    }

    private void executeAndRelationAreSeparate() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.TOOLS, CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_1, CnCwKey.EQUALS, CnCwKey.DIGIT_1);
        equal("1=1│", machine.state().displayText(),
                "relation equality is inserted without executing");
        machine.dispatch(CnCwKey.EXE);
        equal("True", machine.state().result(), "EXE evaluates verification relation");
    }

    private void verificationStoresBooleanAns() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.TOOLS, CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.DIGIT_2, CnCwKey.EQUALS, CnCwKey.DIGIT_2,
                CnCwKey.EXE);
        equal("True", machine.state().result(), "verification true label");
        near(1.0, machine.state().ans(), 0.0, "True stores Ans=1");
    }

    private void verificationChainsAndPersistsAcrossHome() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.TOOLS, CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.DIGIT_1, CnCwKey.EQUALS, CnCwKey.DIGIT_1,
                CnCwKey.EXE);
        machine.dispatch(CnCwKey.EQUALS);
        equal("1=│", machine.state().displayText(),
                "continuous verification carries previous right side");
        press(machine, CnCwKey.DIGIT_1, CnCwKey.EXE);
        equal("True", machine.state().result(), "continuous relation evaluates");

        machine.dispatch(CnCwKey.HOME);
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_2, CnCwKey.EQUALS, CnCwKey.DIGIT_2,
                CnCwKey.EXE);
        equal("True", machine.state().result(),
                "verification setting persists after HOME round trip");
    }

    private void evaluationSnapshotIsIsolated() {
        CnCwMachine live = calculateMachine();
        press(live, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3);
        CnCwMachine snapshot = live.copyForEvaluation();
        snapshot.dispatch(CnCwKey.EXE);
        check(!live.state().hasAns(), "background snapshot cannot mutate live Ans");
        near(5.0, snapshot.state().ans(), 0.0, "snapshot evaluates independently");
        equal("2+3│", live.state().displayText(), "live editor remains available for AC/edit");
    }

    private void catalogManualUtilitiesAreReachable() {
        CnCwMachine machine = calculateMachine();
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.EXE); // Functions submenu
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // ÷R
        press(machine, CnCwKey.DIGIT_2, CnCwKey.EXE);
        check(machine.state().result().contains("商=2"), "catalog ÷R workflow");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.CATALOG);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Angle submenu
        for (int i = 0; i < 5; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // DMS(
        press(machine, CnCwKey.DIGIT_1, CnCwKey.COMMA, CnCwKey.DIGIT_1,
                CnCwKey.DIGIT_5, CnCwKey.COMMA, CnCwKey.DIGIT_0,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(1.25, machine.state().ans(), 0.0, "catalog DMS workflow");
    }

    private void catalogConversionAngleAndCoordinatePathsWork() {
        CnCwMachine machine = calculateMachine();
        machine.dispatch(CnCwKey.CATALOG);
        for (int i = 0; i < 7; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Unit conversions
        machine.dispatch(CnCwKey.EXE); // Length category
        machine.dispatch(CnCwKey.DOWN); // cm→in
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_5, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(1.968503937007874, machine.state().ans(), 1e-12,
                "catalog unit conversion evaluates");

        machine = calculateMachine();
        machine.dispatch(CnCwKey.CATALOG);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Angle / coordinate
        machine.dispatch(CnCwKey.DOWN); // rad(
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_7, CnCwKey.DIVIDE,
                CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(90.0, machine.state().ans(), 1e-12,
                "catalog rad suffix overrides DEG setting");

        machine = calculateMachine();
        machine.dispatch(CnCwKey.CATALOG);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Pol(
        press(machine, CnCwKey.SQRT, CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN,
                CnCwKey.COMMA, CnCwKey.SQRT, CnCwKey.DIGIT_2,
                CnCwKey.CLOSE_PAREN, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("r=2\nθ=45", machine.state().result(), "Pol shows both outputs");
        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.VAR_X, CnCwKey.EXE);
        near(2.0, machine.state().ans(), 0.0, "Pol stores radius in x");
        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.VAR_Y, CnCwKey.EXE);
        near(45.0, machine.state().ans(), 1e-12, "Pol stores angle in y");
    }

    private void manualSimpUsesStepAndSpecifiedFactor() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.TOOLS, CnCwKey.DOWN, CnCwKey.EXE); // manual simplify
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.EXE);
        for (int i = 0; i < 4; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3, CnCwKey.DIGIT_4,
                CnCwKey.FRACTION, CnCwKey.DIGIT_6, CnCwKey.DIGIT_7,
                CnCwKey.DIGIT_8, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("117/339", machine.state().result(), "Simp divides by next common factor");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.EXE);
        for (int i = 0; i < 4; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3, CnCwKey.DIGIT_4,
                CnCwKey.FRACTION, CnCwKey.DIGIT_6, CnCwKey.DIGIT_7,
                CnCwKey.DIGIT_8, CnCwKey.COMMA, CnCwKey.DIGIT_3,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("78/226", machine.state().result(), "Simp accepts specified common factor");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.EXE);
        for (int i = 0; i < 4; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        press(machine, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3, CnCwKey.DIGIT_4,
                CnCwKey.FRACTION, CnCwKey.DIGIT_6, CnCwKey.DIGIT_7,
                CnCwKey.DIGIT_8, CnCwKey.COMMA, CnCwKey.DIGIT_5,
                CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        equal("Cannot Simplify", machine.state().result(),
                "non-common factor has dedicated error");
    }

    private void formatMenuIsDynamicReversibleAndEngInteractive() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_3, CnCwKey.FRACTION,
                CnCwKey.DIGIT_4, CnCwKey.EXE, CnCwKey.FORMAT);
        check(machine.state().menuItems().stream().anyMatch(item -> item.id().equals("mixed")),
                "rational result exposes mixed-fraction format");
        check(machine.state().menuItems().stream().noneMatch(item -> item.id().equals("polar")),
                "real result hides complex-only formats");
        selectMenuId(machine, "mixed");
        equal("3 1/4", machine.state().result(), "mixed fraction conversion");
        machine.dispatch(CnCwKey.BACK);
        equal("13/4", machine.state().result(), "BACK restores standard result");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_4, CnCwKey.EXE, CnCwKey.FORMAT);
        selectMenuId(machine, "engineering");
        equal("1.234×10^3", machine.state().result(), "ENG default exponent");
        machine.dispatch(CnCwKey.RIGHT);
        equal("0.001234×10^6", machine.state().result(),
                "ENG right moves decimal point by three places");
        machine.dispatch(CnCwKey.BACK);
        equal("1234", machine.state().result(), "BACK exits ENG and restores result");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.DIGIT_0, CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_6, CnCwKey.DIGIT_1, CnCwKey.DIGIT_6,
                CnCwKey.DIGIT_2, CnCwKey.EXE, CnCwKey.FORMAT);
        selectMenuId(machine, "factor");
        equal("2×(1018081)", machine.state().result(),
                "prime factor manual limit keeps unresolved factor in parentheses");
    }

    private void selectMenuId(CnCwMachine machine, String id) {
        int index = -1;
        for (int item = 0; item < machine.state().menuItems().size(); item++) {
            if (machine.state().menuItems().get(item).id().equals(id)) {
                index = item;
                break;
            }
        }
        if (index < 0) throw new AssertionError("menu item not found: " + id);
        for (int item = 0; item < index; item++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
    }

    private void shiftIsOneShotAndAngleAware() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.SIN, CnCwKey.DIGIT_0,
                CnCwKey.DOT, CnCwKey.DIGIT_5, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(30.0, machine.state().ans(), 1e-10, "SHIFT sin uses inverse in DEG");
        check(!machine.state().shiftArmed(), "SHIFT consumed exactly once");
    }

    private void manualShiftSevenInputsPi() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_7, CnCwKey.EXE);
        near(Math.PI, machine.state().ans(), 0.0, "SHIFT+7 inputs pi");

        CnCwMachine home = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        press(home, CnCwKey.SHIFT, CnCwKey.RIGHT);
        check(!home.state().shiftArmed(), "non-entry navigation consumes SHIFT");
    }

    private void physicalShiftLegendsAndFractionKey() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.FRACTION, CnCwKey.DIGIT_2, CnCwKey.EXE);
        near(0.5, machine.state().ans(), 0.0, "physical fraction key uses division semantics");

        machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_1);
        equal("D│", machine.state().displayText(), "SHIFT+1 inserts D variable");
        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.SHIFT, CnCwKey.DIGIT_4);
        equal("A│", machine.state().displayText(), "SHIFT+4 inserts A variable");
    }

    private void naturalPowerTemplatesPublishSuperscripts() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.POWER, CnCwKey.DIGIT_3);
        CnCwExpressionNode power = machine.state().naturalExpression();
        check(containsKind(power, CnCwExpressionNode.Kind.SUPERSCRIPT),
                "ordinary power publishes a superscript display node");
        check(!containsText(power, "^"), "ordinary power has no visible caret node");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_6, CnCwKey.EXP, CnCwKey.DIGIT_4);
        CnCwExpressionNode engineering = machine.state().naturalExpression();
        check(containsKind(engineering, CnCwExpressionNode.Kind.SUPERSCRIPT),
                "EXP publishes a superscript display node");
        check(containsText(engineering, "\u00d710"),
                "EXP keeps ten-times notation visible without a caret node");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_5, CnCwKey.FRACTION, CnCwKey.DIGIT_6);
        CnCwExpressionNode fraction = machine.state().naturalExpression();
        check(containsKind(fraction, CnCwExpressionNode.Kind.FRACTION),
                "fraction key publishes a stacked fraction display node");
        check(!containsText(fraction, "a/b"), "fraction template has no linear a/b text node");
    }

    private void answerPowerContinuesFromResult() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_2, CnCwKey.EXE,
                CnCwKey.POWER, CnCwKey.DIGIT_3, CnCwKey.EXE);
        near(8.0, machine.state().ans(), 0.0,
                "power after a result continues from Ans without manual entry");
    }

    private void unfinishedFunctionParenthesisIsCompleted() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.TAN, CnCwKey.DIGIT_3, CnCwKey.DIGIT_6, CnCwKey.EXE);
        near(Math.tan(Math.toRadians(36.0)), machine.state().ans(), 1e-12,
                "EXE completes an unclosed function parenthesis");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.ADD, CnCwKey.EXE);
        equal("Syntax ERROR", machine.state().result(),
                "incomplete operator remains a syntax error");
    }

    private void shiftedLogFunctionsWork() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.LOG, CnCwKey.E, CnCwKey.CLOSE_PAREN,
                CnCwKey.EXE);
        near(1.0, machine.state().ans(), 1e-12, "SHIFT+log is natural logarithm");

        machine = calculateMachine();
        press(machine, CnCwKey.SHIFT, CnCwKey.SQUARE, CnCwKey.DIGIT_2,
                CnCwKey.COMMA, CnCwKey.DIGIT_8, CnCwKey.CLOSE_PAREN, CnCwKey.EXE);
        near(3.0, machine.state().ans(), 1e-12, "SHIFT+square is logarithm with base");
    }

    private void functionDefinitionsPersistAndEvaluate() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.FUNCTION, CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.VAR_X, CnCwKey.SQUARE, CnCwKey.ADD,
                CnCwKey.DIGIT_1, CnCwKey.EXE);
        check(machine.state().result().startsWith("f(x)="),
                "function key defines f(x)");

        machine.dispatch(CnCwKey.AC);
        press(machine, CnCwKey.FUNCTION, CnCwKey.EXE,
                CnCwKey.DIGIT_3, CnCwKey.CLOSE_PAREN,
                CnCwKey.EXE);
        near(10.0, machine.state().ans(), 0.0,
                "defined f(x) remains callable in Calculate");
    }

    private void semanticDeleteAndCursor() {
        CnCwMachine machine = calculateMachine();
        machine.dispatch(CnCwKey.SIN);
        equal("sin(│", machine.state().displayText(), "function is one semantic token");
        machine.dispatch(CnCwKey.DEL);
        equal("│", machine.state().displayText(), "DEL removes complete function token");
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.LEFT, CnCwKey.DIGIT_3);
        equal("13│2", machine.state().displayText(), "token cursor insertion");
    }

    private void semanticSelectionSupportsDeleteAndReplace() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.SHIFT, CnCwKey.LEFT);
        check(machine.state().hasSelection(), "SHIFT+LEFT creates a selection");
        equal("123", machine.selectedExpression(), "selection exports semantic number text");
        equal(0, machine.state().selectionStart(), "selection start is semantic index");
        equal(3, machine.state().selectionEnd(), "selection end is semantic index");
        machine.dispatch(CnCwKey.DEL);
        equal("│", machine.state().displayText(), "DEL removes selected token");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.SHIFT, CnCwKey.LEFT, CnCwKey.DIGIT_9);
        equal("9│", machine.state().displayText(),
                "inserting a key replaces the selected token");

        machine = calculateMachine();
        press(machine, CnCwKey.SIN, CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN,
                CnCwKey.SHIFT, CnCwKey.LEFT);
        equal("sin(2)", machine.selectedExpression(),
                "selection treats a function call as one unit");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_3, CnCwKey.POWER, CnCwKey.DIGIT_2,
                CnCwKey.SHIFT, CnCwKey.LEFT);
        equal("3^2", machine.selectedExpression(),
                "selection treats a power as one unit");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_5, CnCwKey.FRACTION, CnCwKey.DIGIT_6,
                CnCwKey.SHIFT, CnCwKey.LEFT);
        equal("6", machine.selectedExpression(),
                "fraction selection starts with the denominator atom");
        press(machine, CnCwKey.SHIFT, CnCwKey.LEFT);
        equal("5/6", machine.selectedExpression(),
                "fraction selection expands to the complete numerator/denominator unit");
        equal(0, machine.state().selectionStart(),
                "fraction whole selection starts at numerator");
        equal(3, machine.state().selectionEnd(),
                "fraction whole selection ends after denominator");
    }

    private void directTouchCursorMovesAtomically() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.SIN, CnCwKey.DIGIT_2);
        equal(3, machine.cursorLimit(), "touch cursor exposes semantic token count");
        machine.moveCursorTo(1);
        equal("1│sin(2", machine.state().displayText(),
                "touch cursor lands between semantic tokens in one update");
        check(machine.state().result().isEmpty() && !machine.state().resultShown(),
                "moving touch cursor clears stale result state");
        machine.moveCursorTo(99);
        equal(3, machine.state().cursor(), "touch cursor clamps to expression end");
        machine.moveCursorTo(-10);
        equal(0, machine.state().cursor(), "touch cursor clamps to expression start");
    }

    private void touchSelectionCanAnchorAndExtend() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.ADD, CnCwKey.DIGIT_2,
                CnCwKey.ADD, CnCwKey.DIGIT_3);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(4);
        equal("+2+", machine.selectedExpression(),
                "touch selection exports the exact dragged token range");
        equal(1, machine.state().selectionStart(),
                "touch selection preserves its anchor boundary");
        equal(4, machine.state().selectionEnd(),
                "touch selection updates its focus boundary");

        machine = calculateMachine();
        press(machine, CnCwKey.SIN, CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN);
        equal("sin(2)", machine.state().expression(),
                "touch selection function fixture expression");
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal("2", machine.selectedExpression(),
                "touch selection stays fine-grained inside one function argument");

        machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3);
        machine.selectTouchWord(1);
        equal("123", machine.selectedExpression(),
                "long-press selects the complete numeric word before dragging");
    }

    private void touchSelectionHandlesMoveIndependently() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_4, CnCwKey.DIGIT_5);
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(5);
        machine.moveTouchSelectionStart(2);
        equal("345", machine.selectedExpression(),
                "left touch handle moves without changing the right boundary");
        equal(2, machine.state().selectionStart(),
                "left touch handle publishes its new boundary");
        equal(5, machine.state().selectionEnd(),
                "left touch handle preserves right boundary");
        machine.moveTouchSelectionEnd(4);
        equal("34", machine.selectedExpression(),
                "right touch handle moves without changing the left boundary");
        equal(2, machine.state().selectionStart(),
                "right touch handle preserves left boundary");
        equal(4, machine.state().selectionEnd(),
                "right touch handle publishes its new boundary");

        machine = calculateMachine();
        press(machine, CnCwKey.SIN, CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN);
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(3);
        machine.moveTouchSelectionStart(1);
        equal("sin(2)", machine.selectedExpression(),
                "left handle cannot split an enclosing function call");
        machine.moveTouchSelectionEnd(2);
        equal("sin(2)", machine.selectedExpression(),
                "right handle cannot split an enclosing function call");
    }

    private void shiftedDeleteTogglesOverwriteAndOnIsDistinct() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.LEFT, CnCwKey.LEFT, CnCwKey.SHIFT, CnCwKey.DEL,
                CnCwKey.DIGIT_9);
        equal("19│3", machine.state().displayText(),
                "SHIFT+DEL toggles overwrite instead of deleting");
        check(machine.state().overwriteMode(), "overwrite state is renderer-visible");

        press(machine, CnCwKey.SHIFT, CnCwKey.AC);
        check(!machine.state().poweredOn(), "SHIFT+AC powers calculator off");
        machine.dispatch(CnCwKey.DIGIT_7);
        check(!machine.state().poweredOn(), "ordinary keys are ignored while off");
        machine.dispatch(CnCwKey.ON);
        check(machine.state().poweredOn(), "dedicated ON powers calculator on");
        equal(CnCwScreen.HOME, machine.state().screen(), "ON returns to HOME");
    }

    private void settingsAreMachineOwned() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SETTINGS, CnCwKey.OK, CnCwKey.DOWN, CnCwKey.OK,
                CnCwKey.DOWN, CnCwKey.OK);
        equal(AngleUnit.RAD, machine.state().settings().angleUnit(), "settings changes angle unit");
        equal(CnCwScreen.SETTINGS_INPUT_OUTPUT, machine.state().screen(),
                "setting selection stays in settings list");
        machine.dispatch(CnCwKey.BACK);
        equal(CnCwScreen.SETTINGS, machine.state().screen(), "BACK moves up one menu level");
    }

    private void fixDigitsDriveRndOnMainKeyPath() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.SETTINGS, CnCwKey.OK,
                CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.DOWN, CnCwKey.EXE,
                CnCwKey.AC);
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Numeric
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Rnd(
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.DIVIDE,
                CnCwKey.DIGIT_3, CnCwKey.CLOSE_PAREN, CnCwKey.MULTIPLY,
                CnCwKey.DIGIT_3, CnCwKey.EXE);
        equal("9.999", machine.state().result(), "Fix 3 controls Rnd in Calculate");
    }

    private void menusCloseByAcAndHomeAlwaysWins() {
        CnCwMachine machine = calculateMachine();
        machine.dispatch(CnCwKey.CATALOG);
        equal(CnCwScreen.CATALOG, machine.state().screen(), "catalog opens contextually");
        machine.dispatch(CnCwKey.AC);
        equal(CnCwScreen.CALCULATE, machine.state().screen(), "AC closes ordinary menu");
        machine.dispatch(CnCwKey.SETTINGS);
        machine.dispatch(CnCwKey.HOME);
        equal(CnCwScreen.HOME, machine.state().screen(), "HOME wins from nested UI");
    }

    private void applicationLandingIsDeterministic() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_999_CN_CW);
        machine.dispatch(CnCwKey.RIGHT); // Statistics
        machine.dispatch(CnCwKey.RIGHT); // Distribution
        machine.dispatch(CnCwKey.OK);
        equal(CnCwScreen.DISTRIBUTION, machine.state().screen(), "999 distribution entry");
        equal(3, machine.state().modeCommands().size(), "distribution command list");
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
        check(!machine.state().applicationLanding(), "OK enters selected workflow");
        check(machine.state().status().startsWith("二项分布"), "workflow prompt follows selection");
    }

    private void structuredModesReachCoreEngines() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK);
        press(statistics, CnCwKey.DIGIT_1, CnCwKey.COMMA, CnCwKey.DIGIT_2,
                CnCwKey.COMMA, CnCwKey.DIGIT_3, CnCwKey.EXE);
        near(2.0, statistics.state().ans(), 0.0,
                "statistics workflow delegates to StatisticsEngine");
        check(statistics.state().result().startsWith("n=3"),
                "statistics workflow returns structured result");
        check(statistics.state().hasStructuredApplicationResult(),
                "statistics UiState carries structured application result");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE,
                statistics.state().applicationResult().layout(),
                "statistics UiState exposes key/value layout");
        equal("一元统计", statistics.state().applicationResult().title(),
                "statistics UiState exposes result title");
        equal("n", statistics.state().applicationResult().items().get(0).label(),
                "statistics UiState preserves ordered result items");

        CnCwMachine distribution = homeApplication(CnCwModel.FX_999_CN_CW, 2);
        distribution.dispatch(CnCwKey.DOWN);
        distribution.dispatch(CnCwKey.OK);
        press(distribution, CnCwKey.DIGIT_2, CnCwKey.COMMA, CnCwKey.DIGIT_5,
                CnCwKey.COMMA, CnCwKey.DIGIT_0, CnCwKey.DOT, CnCwKey.DIGIT_5,
                CnCwKey.EXE);
        near(0.3125, distribution.state().ans(), 1e-15,
                "distribution workflow delegates to DistributionEngine");

        CnCwMachine ratio = homeApplication(CnCwModel.FX_991_CN_CW, 9);
        ratio.dispatch(CnCwKey.OK);
        press(ratio, CnCwKey.DIGIT_3, CnCwKey.COMMA, CnCwKey.DIGIT_8,
                CnCwKey.COMMA, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.EXE);
        near(4.5, ratio.state().ans(), 0.0,
                "ratio workflow delegates to RatioEngine");
        equal("X=4.5", ratio.state().result(), "ratio workflow display");
    }

    private void structuredWorkflowInputPageRunsEndToEnd() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK); // One-variable structured command.
        check(statistics.state().hasWorkflowInput(),
                "selecting statistics command opens structured input page");
        equal(1, statistics.state().workflowInput().rows(),
                "one-variable input starts with one row");
        press(statistics, CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_3, CnCwKey.EXE);
        near(2.0, statistics.state().ans(), 0.0,
                "structured statistics input evaluates through existing engine");
        check(statistics.state().hasStructuredApplicationResult(),
                "structured input keeps Stage 4 result protocol");
        statistics.dispatch(CnCwKey.BACK);
        check(statistics.state().hasWorkflowInput() && !statistics.state().resultShown(),
                "BACK from result restores structured input page");
        equal("3", statistics.state().workflowInput().cell(2, 0),
                "structured input data survives result inspection");

        CnCwMachine touch = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        touch.dispatch(CnCwKey.OK);
        press(touch, CnCwKey.DIGIT_4, CnCwKey.OK, CnCwKey.DIGIT_5);
        touch.selectWorkflowCell(0, 0);
        equal("4", touch.state().displayText().replace("│", ""),
                "direct cell selection restores the selected cell editor");
        CnCwMachine isolated = touch.copyForEvaluation();
        isolated.selectWorkflowCell(1, 0);
        equal(0, touch.state().workflowInput().selectedRow(),
                "evaluation snapshot owns a deep workflow-session copy");

        CnCwMachine legacy = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        legacy.dispatch(CnCwKey.DIGIT_1); // Direct typing from landing is compatibility path.
        check(!legacy.state().hasWorkflowInput(),
                "direct typing from application landing preserves legacy bridge");
    }

    private void workflowActionsCanBeAppliedFromMachine() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK);
        check(statistics.state().hasWorkflowInput(), "statistics action test opens workflow");
        equal(1, statistics.state().workflowInput().rows(), "series starts at one row");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);
        equal(2, statistics.state().workflowInput().rows(), "ADD_ROW mutates workflow through machine");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.REMOVE_ROW);
        equal(1, statistics.state().workflowInput().rows(), "REMOVE_ROW mutates workflow through machine");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.BACK);
        check(statistics.state().applicationLanding(), "BACK action returns to command landing");
    }

    private void spreadsheetCompactWorkflowPersistsCells() {
        CnCwMachine sheet = homeApplication(CnCwModel.FX_999_CN_CW, 3);
        sheet.dispatch(CnCwKey.OK);
        press(sheet, CnCwKey.VAR_A, CnCwKey.DIGIT_1, CnCwKey.COMMA,
                CnCwKey.DIGIT_7, CnCwKey.MULTIPLY, CnCwKey.DIGIT_5, CnCwKey.EXE);
        near(35.0, sheet.state().ans(), 0.0, "spreadsheet A1 value");
        check(sheet.state().result().startsWith("A1=35"), "spreadsheet cell result rendered");

        sheet.dispatch(CnCwKey.AC);
        press(sheet, CnCwKey.VAR_A, CnCwKey.DIGIT_2, CnCwKey.COMMA,
                CnCwKey.VAR_A, CnCwKey.DIGIT_1, CnCwKey.ADD,
                CnCwKey.DIGIT_7, CnCwKey.EXE);
        near(42.0, sheet.state().ans(), 0.0, "spreadsheet formula references prior cell");
        check(sheet.state().result().startsWith("A2=42"), "spreadsheet formula result rendered");
    }

    private void spreadsheetGridMovesAndCommitsSelectedCell() {
        CnCwMachine sheet = homeApplication(CnCwModel.FX_999_CN_CW, 3);
        sheet.dispatch(CnCwKey.OK); // A1:E45 grid command
        check(sheet.state().spreadsheetGrid(), "spreadsheet opens a grid session");
        press(sheet, CnCwKey.DIGIT_7, CnCwKey.EXE);
        check(sheet.state().result().startsWith("A1=7"), "grid EXE commits selected A1");
        sheet.dispatch(CnCwKey.DOWN);
        equal(1, sheet.state().spreadsheetRow(), "grid DOWN moves to row 2");
        press(sheet, CnCwKey.DIGIT_9, CnCwKey.EXE);
        check(sheet.state().result().startsWith("A2=9"), "grid EXE commits selected A2");
    }

    private CnCwMachine homeApplication(CnCwModel model, int index) {
        CnCwMachine machine = new CnCwMachine(model);
        for (int i = 0; i < index; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private CnCwMachine calculateMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private static boolean containsKind(CnCwExpressionNode node, CnCwExpressionNode.Kind kind) {
        if (node.kind() == kind) return true;
        for (CnCwExpressionNode child : node.children()) {
            if (containsKind(child, kind)) return true;
        }
        return false;
    }

    private static boolean containsText(CnCwExpressionNode node, String text) {
        if (node.text().contains(text)) return true;
        for (CnCwExpressionNode child : node.children()) {
            if (containsText(child, text)) return true;
        }
        return false;
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
