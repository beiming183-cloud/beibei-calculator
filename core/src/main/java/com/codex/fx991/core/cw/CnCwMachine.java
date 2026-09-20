package com.codex.fx991.core.cw;

import com.codex.fx991.core.AngleUnit;
import com.codex.fx991.core.math.BaseNEngine;
import com.codex.fx991.core.math.ComplexExpressionEngine;
import com.codex.fx991.core.math.ComplexValue;
import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.CalculationException;
import com.codex.fx991.core.math.CalculationBudget;
import com.codex.fx991.core.math.ExactValue;
import com.codex.fx991.core.math.ManualFunctions;
import com.codex.fx991.core.math.Rational;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.ScientificConstants;
import com.codex.fx991.core.math.SpreadsheetModel;
import com.codex.fx991.core.math.StatementEngine;
import com.codex.fx991.core.math.UnitConverter;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CancellationException;

/**
 * Android-free CN CW shell and fast expression controller.
 *
 * <p>Every interaction enters through {@link #dispatch(CnCwKey)}. Menus,
 * HOME navigation, one-shot SHIFT, semantic expression tokens, cursor,
 * history, Ans, settings, and application landing screens therefore have a
 * single owner. The Android canvas is only an input/rendering adapter.</p>
 */
public final class CnCwMachine {
    private static final int HOME_COLUMNS = 3;
    /** Stable home-grid contract for the Android renderer (two rows × three tiles). */
    public static final int HOME_VIEWPORT_SIZE = CnCwUiState.HOME_VIEWPORT_SIZE;
    public static final int HOME_VIEWPORT_COLUMNS = CnCwUiState.HOME_VIEWPORT_COLUMNS;
    private static final List<String> VARIABLE_NAMES =
            com.codex.fx991.core.Compat.list("A", "B", "C", "D", "E", "F", "x", "y", "z");

    private final CnCwModel model;
    private final Random random = new Random();
    private final Map<String, Double> variables = new HashMap<>();
    private final Map<String, ExactValue> exactVariables = new HashMap<>();
    private final Deque<Navigation> navigation = new ArrayDeque<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Token> tokens = new ArrayList<>();
    private final CnCwLinearAlgebraMemory linearAlgebraMemory = new CnCwLinearAlgebraMemory();
    private final SpreadsheetModel spreadsheet;
    private boolean spreadsheetGrid;
    private int spreadsheetRow;
    private int spreadsheetColumn;
    private int catalogCategoryIndex;
    private int conversionCategoryIndex;

    private CnCwScreen screen = CnCwScreen.HOME;
    private ApplicationMode application;
    /** App that was active immediately before HOME, used for MatAns/VctAns lifetime rules. */
    private ApplicationMode applicationBeforeHome;
    private CnCwSettings settings = CnCwSettings.defaults();
    private int selectedIndex;
    private int cursor;
    /** Stage 3 nested position override; null falls back to legacy-boundary inference. */
    private CnCwCursorPath semanticCursorOverride;
    /** Inclusive anchor and exclusive focus for semantic token selection. */
    private int selectionAnchor = -1;
    private int selectionFocus = -1;
    private boolean shiftArmed;
    private boolean poweredOn = true;
    private boolean overwriteMode;
    private boolean applicationLanding;
    private boolean resultShown;
    private boolean verificationMode;
    private boolean manualSimplification;
    private String result = "";
    /** Last core-owned application result; renderer sees it only while resultShown. */
    private CnCwModeEngine.ModeResult applicationResult;
    /** Stage 5 structured input editor; null keeps the legacy expression bridge. */
    private CnCwWorkflowSession workflowSession;
    /** Last explicitly committed Stage 6 result/error payload. */
    private CnCwCalculationState committedCalculationState = CnCwCalculationState.editing();
    private String status = "HOME";
    private String activeCommandId = "";
    private double ans;
    private boolean hasAns;
    /** Human-readable calculation process that produced the current Ans value. */
    private String ansProcessDisplay = "";
    /** Human-readable calculation process associated with the currently shown result. */
    private String resultProcessDisplay = "";
    private ExactValue exactAns;
    private ExactValue lastExactResult;
    private String originalResult = "";
    private boolean formatConverted;
    private boolean engineeringMode;
    private int engineeringExponent;
    private boolean errorShown;
    private CalculationError lastError;
    private int errorCursor;
    private ComplexValue complexAns = ComplexValue.ZERO;
    private boolean hasComplexAns;
    private String functionFSource = "";
    private String functionGSource = "";
    private String pendingFunctionDefinition = "";
    private ScalarExpressionEngine.CompiledExpression functionF;
    private ScalarExpressionEngine.CompiledExpression functionG;
    private int historyIndex;
    private String statementSequenceSource = "";
    private List<String> statementSequence = com.codex.fx991.core.Compat.list();
    private int statementSequenceIndex;
    private List<Token> undoTokens;
    private int undoCursor;
    private CnCwUiState state;

    public CnCwMachine(CnCwModel model) {
        this.model = model;
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        spreadsheet = new SpreadsheetModel(evaluationContext());
        publish();
    }

    private CnCwMachine(CnCwMachine source) {
        model = source.model;
        variables.putAll(source.variables);
        exactVariables.putAll(source.exactVariables);
        navigation.addAll(source.navigation);
        history.addAll(source.history);
        tokens.addAll(source.tokens);
        linearAlgebraMemory.copyFrom(source.linearAlgebraMemory);

        screen = source.screen;
        application = source.application;
        applicationBeforeHome = source.applicationBeforeHome;
        settings = source.settings;
        selectedIndex = source.selectedIndex;
        cursor = source.cursor;
        semanticCursorOverride = source.semanticCursorOverride;
        selectionAnchor = source.selectionAnchor;
        selectionFocus = source.selectionFocus;
        shiftArmed = source.shiftArmed;
        poweredOn = source.poweredOn;
        overwriteMode = source.overwriteMode;
        applicationLanding = source.applicationLanding;
        resultShown = source.resultShown;
        verificationMode = source.verificationMode;
        manualSimplification = source.manualSimplification;
        result = source.result;
        applicationResult = source.applicationResult;
        workflowSession = source.workflowSession == null ? null : source.workflowSession.copy();
        committedCalculationState = source.committedCalculationState;
        status = source.status;
        activeCommandId = source.activeCommandId;
        ans = source.ans;
        hasAns = source.hasAns;
        ansProcessDisplay = source.ansProcessDisplay;
        resultProcessDisplay = source.resultProcessDisplay;
        exactAns = source.exactAns;
        lastExactResult = source.lastExactResult;
        originalResult = source.originalResult;
        formatConverted = source.formatConverted;
        engineeringMode = source.engineeringMode;
        engineeringExponent = source.engineeringExponent;
        errorShown = source.errorShown;
        lastError = source.lastError;
        errorCursor = source.errorCursor;
        complexAns = source.complexAns;
        hasComplexAns = source.hasComplexAns;
        functionFSource = source.functionFSource;
        functionGSource = source.functionGSource;
        pendingFunctionDefinition = source.pendingFunctionDefinition;
        functionF = source.functionF;
        functionG = source.functionG;
        spreadsheetGrid = source.spreadsheetGrid;
        spreadsheetRow = source.spreadsheetRow;
        spreadsheetColumn = source.spreadsheetColumn;
        catalogCategoryIndex = source.catalogCategoryIndex;
        conversionCategoryIndex = source.conversionCategoryIndex;
        historyIndex = source.historyIndex;
        statementSequenceSource = source.statementSequenceSource;
        statementSequence = source.statementSequence;
        statementSequenceIndex = source.statementSequenceIndex;
        undoTokens = source.undoTokens == null ? null
                : com.codex.fx991.core.Compat.copyList(source.undoTokens);
        undoCursor = source.undoCursor;
        spreadsheet = source.spreadsheet.copy(evaluationContext());
        publish();
    }

    /**
     * Returns an isolated machine snapshot. Heavy EXE work can mutate this
     * copy off the UI thread and atomically replace the live machine only if
     * its input revision is still current.
     */
    public CnCwMachine copyForEvaluation() {
        return new CnCwMachine(this);
    }

    public CnCwUiState state() { return state; }

    /**
     * Returns the human-readable calculation process for inspection/copying.
     * Ans is expanded to the already-expanded process that produced its value;
     * evaluator tokens themselves are never rewritten.
     */
    public String calculationProcessDisplay() {
        if (resultShown && resultProcessDisplay != null && !resultProcessDisplay.isBlank()) {
            return resultProcessDisplay;
        }
        return expandedProcessDisplay(tokens, ansProcessDisplay);
    }

    private String expandedProcessDisplay(List<Token> source, String ansSource) {
        StringBuilder out = new StringBuilder(Math.max(8, source.size() * 2));
        for (Token token : source) {
            if ("Ans".equals(token.evaluation) && ansSource != null && !ansSource.isBlank()) {
                out.append('(').append(ansSource).append(')');
            } else {
                out.append(token.display);
            }
        }
        return out.toString();
    }

    /**
     * Applies exactly one logical key intent and returns the new snapshot.
     *
     * <p>EXE is the physical commit key.  OK and ENTER intentionally share
     * that command path; EQUALS is handled as an expression relation token
     * below and never enters {@link #evaluate()} from this dispatcher.</p>
     */
    public CnCwUiState dispatch(CnCwKey key) {
        if (key == null) return state;
        if (key == CnCwKey.ON) return reset();
        if (!poweredOn) return state;
        boolean consumeShift = key != CnCwKey.SHIFT && shiftArmed;
        if (key == CnCwKey.HOME) {
            showHome();
        } else if (key == CnCwKey.SHIFT) {
            shiftArmed = !shiftArmed;
            status = shiftArmed ? "SHIFT" : applicationStatus();
        } else if (screen == CnCwScreen.HOME) {
            handleHome(key);
        } else if (screen.isPopupMenu()) {
            handlePopup(key);
        } else if (screen.isApplication()) {
            handleApplication(key);
        }
        if (consumeShift && shiftArmed) shiftArmed = false;
        publish();
        return state;
    }

    public CnCwUiState reduce(CnCwKey key) { return dispatch(key); }

    /** Atomically moves the expression insertion point for direct-touch adapters. */
    public CnCwUiState moveCursorTo(int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        semanticCursorOverride = null;
        cursor = Math.max(0, Math.min(tokens.size(), target));
        clearSelection();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        publish();
        return state;
    }

    /**
     * Semantic touch entry point used by Stage 3 platform adapters. Invalid or stale
     * paths fail closed to their retained legacy boundary instead of corrupting a
     * structure.
     */
    public CnCwUiState moveCursorTo(CnCwCursorPath target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        if (!applySemanticTouchCursor(target)) {
            semanticCursorOverride = null;
            cursor = semanticTouchBoundary(target);
        }
        clearSelection();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        publish();
        return state;
    }

    /** Validates and installs one semantic insertion path. */
    private boolean applySemanticTouchCursor(CnCwCursorPath target) {
        if (target == null) return false;
        if (target.isRootBoundary()) {
            setRootCursor(target.legacyTokenBoundary());
            return true;
        }
        if (target.childPath().isEmpty()) return false;
        int template = target.childPath().get(0);
        switch (target.slot()) {
            case FRACTION_NUMERATOR, FRACTION_DENOMINATOR -> {
                FractionBounds bounds = fractionBounds(template);
                if (bounds == null) return false;
                setFractionCursor(bounds, target.slot(), target.offset());
                return true;
            }
            case SUPERSCRIPT_BASE, SUPERSCRIPT_EXPONENT -> {
                PowerBounds bounds = powerBounds(template);
                if (bounds == null) return false;
                setPowerCursor(bounds, target.slot(), target.offset());
                return true;
            }
            case RADICAL_CONTENT, ROOT_INDEX, ROOT_CONTENT -> {
                RadicalBounds bounds = radicalBounds(template);
                if (bounds == null) return false;
                Token token = tokens.get(template);
                if (target.slot() == CnCwCursorPath.Slot.ROOT_INDEX
                        && !isGenericRootTemplate(token)) return false;
                if (target.slot() == CnCwCursorPath.Slot.RADICAL_CONTENT
                        && !isSquareRootTemplate(token)) return false;
                if (target.slot() == CnCwCursorPath.Slot.ROOT_CONTENT
                        && isSquareRootTemplate(token)) return false;
                setRadicalCursor(bounds, target.slot(), target.offset());
                return true;
            }
            case FUNCTION_ARGUMENT -> {
                if (target.childPath().size() < 2) return false;
                FunctionBounds bounds = functionBounds(template);
                int argument = target.childPath().get(1);
                if (bounds == null || argument < 0 || argument >= bounds.arguments.size()) {
                    return false;
                }
                setFunctionCursor(bounds, argument, target.offset());
                return true;
            }
            case ROW -> {
                setRootCursor(target.legacyTokenBoundary());
                return true;
            }
        }
        return false;
    }

    private int semanticTouchBoundary(CnCwCursorPath target) {
        if (target == null) return cursor;
        return Math.max(0, Math.min(tokens.size(), target.legacyTokenBoundary()));
    }

    /** Starts a touch-driven text selection at a semantic insertion boundary. */
    public CnCwUiState beginTouchSelection(int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        semanticCursorOverride = null;
        cursor = Math.max(0, Math.min(tokens.size(), target));
        selectionAnchor = cursor;
        selectionFocus = cursor;
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
        publish();
        return state;
    }

    /** Selects the semantic word/unit under a long-press before dragging. */
    public CnCwUiState selectTouchWord(int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        semanticCursorOverride = null;
        int boundary = Math.max(0, Math.min(tokens.size(), target));
        if (tokens.isEmpty()) return beginTouchSelection(boundary);
        int start;
        int end;
        if (boundary < tokens.size()) {
            start = semanticAtomStart(boundary);
            end = semanticAtomEnd(boundary);
        } else {
            start = semanticAtomStart(boundary);
            end = boundary;
        }
        SelectionRange range = normalizeTouchSelectionRange(start, end);
        selectionAnchor = range.start;
        selectionFocus = range.end;
        cursor = range.end;
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
        publish();
        return state;
    }

    /** Moves the active touch-selection focus without clearing its anchor. */
    public CnCwUiState extendTouchSelection(int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        semanticCursorOverride = null;
        if (selectionAnchor < 0) {
            beginTouchSelection(cursor);
        }
        int clamped = Math.max(0, Math.min(tokens.size(), target));
        SelectionRange range = normalizeTouchSelectionRange(selectionAnchor, clamped);
        if (clamped >= selectionAnchor) {
            selectionAnchor = range.start;
            selectionFocus = range.end;
            cursor = range.end;
        } else {
            selectionAnchor = range.end;
            selectionFocus = range.start;
            cursor = range.start;
        }
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
        publish();
        return state;
    }

    /** Moves only the left touch-selection handle, preserving the right edge. */
    public CnCwUiState moveTouchSelectionStart(int target) {
        return moveTouchSelectionBoundary(true, target);
    }

    /** Moves only the right touch-selection handle, preserving the left edge. */
    public CnCwUiState moveTouchSelectionEnd(int target) {
        return moveTouchSelectionBoundary(false, target);
    }

    private CnCwUiState moveTouchSelectionBoundary(boolean startBoundary, int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding || !hasSelection()) {
            return state;
        }
        semanticCursorOverride = null;
        int currentStart = Math.min(selectionAnchor, selectionFocus);
        int currentEnd = Math.max(selectionAnchor, selectionFocus);
        int clamped = Math.max(0, Math.min(tokens.size(), target));
        SelectionRange range;
        if (startBoundary) {
            clamped = Math.min(clamped, currentEnd - 1);
            range = normalizeTouchSelectionRange(clamped, currentEnd);
            selectionAnchor = range.start;
            selectionFocus = range.end;
            cursor = range.start;
        } else {
            clamped = Math.max(clamped, currentStart + 1);
            range = normalizeTouchSelectionRange(currentStart, clamped);
            selectionAnchor = range.start;
            selectionFocus = range.end;
            cursor = range.end;
        }
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
        publish();
        return state;
    }

    /** Semantic-path overloads used by the Android Stage 3 touch adapter. */
    public CnCwUiState beginTouchSelection(CnCwCursorPath target) {
        int boundary = semanticTouchBoundary(target);
        CnCwUiState value = beginTouchSelection(boundary);
        if (applySemanticTouchCursor(target)) {
            selectionAnchor = cursor;
            selectionFocus = cursor;
            publish();
            return state;
        }
        return value;
    }

    public CnCwUiState selectTouchWord(CnCwCursorPath target) {
        return selectTouchWord(semanticTouchBoundary(target));
    }

    public CnCwUiState extendTouchSelection(CnCwCursorPath target) {
        return extendTouchSelection(semanticTouchBoundary(target));
    }

    public CnCwUiState moveTouchSelectionStart(CnCwCursorPath target) {
        return moveTouchSelectionStart(semanticTouchBoundary(target));
    }

    public CnCwUiState moveTouchSelectionEnd(CnCwCursorPath target) {
        return moveTouchSelectionEnd(semanticTouchBoundary(target));
    }

    /**
     * Snaps a dragged selection around structures that must stay intact when
     * copied or replaced. Touches may land inside a function, power, or
     * fraction, but the resulting range always contains that complete unit.
     */
    private SelectionRange normalizeTouchSelectionRange(int anchor, int target) {
        if (anchor == target) return new SelectionRange(anchor, target);
        int start = Math.min(anchor, target);
        int end = Math.max(anchor, target);
        boolean changed;
        do {
            changed = false;
            for (int index = start; index < end && index < tokens.size(); index++) {
                Token token = tokens.get(index);
                int unitStart = index;
                int unitEnd = index + 1;
                if (isFractionTemplate(token)) {
                    FractionBounds fraction = fractionBounds(index);
                    if (fraction != null) {
                        unitStart = fraction.numeratorStart;
                        unitEnd = fraction.denominatorEnd;
                    }
                } else if (isPowerTemplate(token)) {
                    PowerBounds power = powerBounds(index);
                    if (power != null) {
                        unitStart = power.baseStart;
                        unitEnd = power.exponentEnd;
                    }
                } else if (opensParenthesis(token)) {
                    int close = matchingClose(index);
                    unitEnd = close >= 0 ? close + 1 : unitEnd;
                } else if (")".equals(token.evaluation)) {
                    int open = matchingOpen(index);
                    unitStart = open >= 0 ? open : unitStart;
                } else if (!selectionWithinSemanticEditableSlot(start, end, index)) {
                    int enclosing = enclosingOpen(index);
                    if (enclosing >= 0) {
                        int close = matchingClose(enclosing);
                        unitStart = enclosing;
                        unitEnd = close >= 0 ? close + 1 : unitEnd;
                    }
                }
                int nextStart = Math.min(start, unitStart);
                int nextEnd = Math.max(end, unitEnd);
                if (nextStart != start || nextEnd != end) {
                    start = nextStart;
                    end = nextEnd;
                    changed = true;
                }
            }
        } while (changed);
        return new SelectionRange(start, end);
    }

    /** Number of semantic insertion slots currently available. */
    public int cursorLimit() { return tokens.size(); }

    /** Display labels for touch hit-testing; one entry per semantic token. */
    public List<String> cursorTokenDisplays() {
        List<String> labels = new ArrayList<>(tokens.size());
        for (Token token : tokens) labels.add(token.display());
        return com.codex.fx991.core.Compat.copyList(labels);
    }

    /**
     * Imports plain text from an external clipboard as semantic calculator tokens.
     *
     * <p>The Android adapter must not silently drop characters while simulating
     * key presses.  Clipboard text is parsed atomically here so display spellings
     * such as {@code ²}, {@code √( )}, {@code π} and evaluator spellings such as
     * {@code ^2}, {@code sqrt(} and {@code pi} round-trip through the same token
     * model used by normal key input.  Unknown non-whitespace characters reject
     * the whole paste instead of corrupting a valid expression by omission.</p>
     *
     * @return number of imported semantic tokens, 0 for blank/no-op, -1 when the
     *         source contains unsupported text.
     */
    public int pasteExpression(String text) {
        if (!poweredOn || !screen.isApplication() || applicationLanding || text == null) return 0;
        List<Token> imported = parsePastedTokens(text);
        if (imported == null) return -1;
        if (imported.isEmpty()) return 0;

        // Treat one system paste as one editor mutation.  This also makes undo
        // restore the entire pre-paste expression instead of only the last char.
        rememberUndo();
        semanticCursorOverride = null;
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";

        if (selectionActive()) {
            int start = selectionStart();
            int end = selectionEnd();
            tokens.subList(start, end).clear();
            cursor = start;
            clearSelection();
        } else if (resultShown) {
            tokens.clear();
            cursor = 0;
            clearSelection();
        }

        tokens.addAll(cursor, imported);
        cursor += imported.size();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = applicationStatus();
        publish();
        return imported.size();
    }

    /** Returns null rather than partially importing text whose semantics are unknown. */
    private List<Token> parsePastedTokens(String text) {
        String source = text.replace("│", "").replace("▌", "").trim();
        List<Token> imported = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char value = source.charAt(index);
            if (Character.isWhitespace(value)) {
                index++;
                continue;
            }

            PasteMatch match = pasteLexeme(source, index);
            if (match != null) {
                imported.add(match.token);
                index += match.length;
                continue;
            }

            // Preserve scientific E notation as one evaluator token.  Without
            // this, 1E3 would be misread as 1 * variable-E * 3.
            if (Character.isDigit(value) || value == '.') {
                int numberEnd = index;
                boolean hasDigit = false;
                while (numberEnd < source.length()) {
                    char number = source.charAt(numberEnd);
                    if (Character.isDigit(number)) {
                        hasDigit = true;
                        numberEnd++;
                    } else if (number == '.') {
                        numberEnd++;
                    } else {
                        break;
                    }
                }
                if (hasDigit && numberEnd < source.length()
                        && (source.charAt(numberEnd) == 'E' || source.charAt(numberEnd) == 'e')) {
                    int exponentEnd = numberEnd + 1;
                    if (exponentEnd < source.length()
                            && (source.charAt(exponentEnd) == '+'
                            || source.charAt(exponentEnd) == '-'
                            || source.charAt(exponentEnd) == '−')) exponentEnd++;
                    int exponentDigits = exponentEnd;
                    while (exponentEnd < source.length()
                            && Character.isDigit(source.charAt(exponentEnd))) exponentEnd++;
                    if (exponentEnd > exponentDigits) {
                        String literal = source.substring(index, exponentEnd).replace('−', '-');
                        imported.add(token(literal, literal));
                        index = exponentEnd;
                        continue;
                    }
                }
            }

            Token token = switch (value) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.' -> token(Character.toString(value));
                case '+' -> token("+", "+", true);
                case '-', '−' -> token("−", "-", true);
                case '*', '×' -> token("×", "*", true);
                case '/', '÷' -> token("÷", "/", true);
                case '^' -> token("^", "^", true);
                case '(' -> token("(");
                case ')' -> token(")");
                case ',' -> token(",");
                case '!' -> token("!");
                case '%' -> token("%", "%");
                case '=' -> token("=", "=", true);
                case '<' -> token("<", "<", true);
                case '>' -> token(">", ">", true);
                case ':' -> token(":", ":", true);
                case 'π' -> token("π", "pi");
                case 'e' -> token("e", "e");
                case 'i' -> token("i", "i");
                case 'x' -> token("x");
                case 'y' -> token("y");
                case 'z' -> token("z");
                case 'A', 'B', 'C', 'D', 'E', 'F' -> token(Character.toString(value));
                case '²' -> token("²", "^2");
                case '³' -> token("³", "^3");
                case '√' -> token("√(", "sqrt(");
                case '∠' -> token("∠", "∠");
                default -> null;
            };
            if (token == null) return null;
            imported.add(token);
            index++;
            // The √ token already owns its opening parenthesis.  A displayed
            // string normally contains √(...), so consume that literal '(' once.
            if (value == '√' && index < source.length() && source.charAt(index) == '(') index++;
        }
        return imported;
    }

    /** Matches multi-character evaluator/display spellings before char fallback. */
    private PasteMatch pasteLexeme(String source, int index) {
        String[][] spellings = {
                {"sinh⁻¹(", "sinh⁻¹(", "asinh("},
                {"cosh⁻¹(", "cosh⁻¹(", "acosh("},
                {"tanh⁻¹(", "tanh⁻¹(", "atanh("},
                {"sin⁻¹(", "sin⁻¹(", "asin("},
                {"cos⁻¹(", "cos⁻¹(", "acos("},
                {"tan⁻¹(", "tan⁻¹(", "atan("},
                {"integral(", "∫(", "integral("},
                {"ranint(", "RanInt#(", "ranint("},
                {"sqrt(", "√(", "sqrt("},
                {"mixed(", "a b/c(", "mixed("},
                {"asinh(", "sinh⁻¹(", "asinh("},
                {"acosh(", "cosh⁻¹(", "acosh("},
                {"atanh(", "tanh⁻¹(", "atanh("},
                {"asin(", "sin⁻¹(", "asin("},
                {"acos(", "cos⁻¹(", "acos("},
                {"atan(", "tan⁻¹(", "atan("},
                {"sinh(", "sinh(", "sinh("},
                {"cosh(", "cosh(", "cosh("},
                {"tanh(", "tanh(", "tanh("},
                {"root(", "√[ ](", "root("},
                {"diff(", "d/dx(", "diff("},
                {"sum(", "Σ(", "sum("},
                {"conjugate(", "Conjg(", "conj("},
                {"conj(", "Conjg(", "conj("},
                {"real(", "Re(", "re("},
                {"imag(", "Im(", "im("},
                {"arg(", "Arg(", "arg("},
                {"re(", "Re(", "re("},
                {"im(", "Im(", "im("},
                {"abs(", "Abs(", "abs("},
                {"dms(", "DMS(", "dms("},
                {"pol(", "Pol(", "pol("},
                {"rec(", "Rec(", "rec("},
                {"ran(", "Ran#", "ran("},
                {"sin(", "sin(", "sin("},
                {"cos(", "cos(", "cos("},
                {"tan(", "tan(", "tan("},
                {"log(", "log(", "log("},
                {"ln(", "ln(", "ln("},
                {"f(", "f(", "f("},
                {"g(", "g(", "g("},
                {"Ans", "Ans", "Ans"},
                {"nPr", "nPr", "nPr"},
                {"nCr", "nCr", "nCr"},
                {"pi", "π", "pi"},
                {"<=", "≤", "<=", "binary"},
                {">=", "≥", ">=", "binary"},
                {"!=", "≠", "!=", "binary"},
                {"->", "→", "->", "binary"},
                {"⁻¹", "⁻¹", "^(-1)"}
        };
        for (String[] spelling : spellings) {
            if (!source.startsWith(spelling[0], index)) continue;
            boolean binary = spelling.length > 3 && "binary".equals(spelling[3]);
            Token token = token(spelling[1], spelling[2], binary);
            return new PasteMatch(token, spelling[0].length());
        }
        return null;
    }

    private static final class PasteMatch {
        private final Token token;
        private final int length;

        private PasteMatch(Token token, int length) {
            this.token = token;
            this.length = length;
        }
    }

    public CnCwUiState reset() {
        workflowSession = null;
        applicationResult = null;
        screen = CnCwScreen.HOME;
        application = null;
        applicationBeforeHome = null;
        settings = CnCwSettings.defaults();
        selectedIndex = 0;
        cursor = 0;
        semanticCursorOverride = null;
        clearSelection();
        shiftArmed = false;
        poweredOn = true;
        overwriteMode = false;
        applicationLanding = false;
        resultShown = false;
        verificationMode = false;
        manualSimplification = false;
        result = "";
        status = "HOME";
        activeCommandId = "";
        ans = 0.0;
        hasAns = false;
        ansProcessDisplay = "";
        resultProcessDisplay = "";
        exactAns = null;
        lastExactResult = null;
        originalResult = "";
        formatConverted = false;
        engineeringMode = false;
        engineeringExponent = 0;
        errorShown = false;
        lastError = null;
        errorCursor = 0;
        complexAns = ComplexValue.ZERO;
        hasComplexAns = false;
        functionFSource = "";
        functionGSource = "";
        pendingFunctionDefinition = "";
        functionF = null;
        functionG = null;
        spreadsheetGrid = false;
        spreadsheetRow = 0;
        spreadsheetColumn = 0;
        catalogCategoryIndex = 0;
        conversionCategoryIndex = 0;
        tokens.clear();
        history.clear();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        navigation.clear();
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        spreadsheet.clearAll();
        linearAlgebraMemory.clear();
        undoTokens = null;
        publish();
        return state;
    }

    /** Direct application selection from the currently visible HOME viewport. */
    public CnCwUiState activateHomeItem(int index) {
        if (poweredOn && screen == CnCwScreen.HOME && index >= 0 && index < model.applications().size()) {
            selectedIndex = index;
            openApplication(model.applications().get(index));
            publish();
        }
        return state;
    }

    private void handleHome(CnCwKey key) {
        int size = model.applications().size();
        int visible = Math.min(HOME_VIEWPORT_SIZE, size - (selectedIndex / HOME_VIEWPORT_SIZE) * HOME_VIEWPORT_SIZE);
        int columns = visible <= 4 ? 2 : HOME_COLUMNS;
        selectedIndex = switch (key) {
            case LEFT -> wrap(selectedIndex - 1, size);
            case RIGHT -> wrap(selectedIndex + 1, size);
            case UP -> wrap(selectedIndex - columns, size);
            case DOWN -> wrap(selectedIndex + columns, size);
            case PAGE_UP -> wrap(selectedIndex - HOME_VIEWPORT_SIZE, size);
            case PAGE_DOWN -> wrap(selectedIndex + HOME_VIEWPORT_SIZE, size);
            default -> selectedIndex;
        };
        switch (key) {
            case OK, ENTER, EXE -> openApplication(model.applications().get(selectedIndex));
            case SETTINGS -> openPopup(CnCwScreen.SETTINGS);
            default -> { }
        }
    }

    private void handlePopup(CnCwKey key) {
        List<CnCwCommand> items = currentMenuItems();
        switch (key) {
            case UP -> selectedIndex = wrap(selectedIndex - 1, items.size());
            case DOWN -> selectedIndex = wrap(selectedIndex + 1, items.size());
            case PAGE_UP -> selectedIndex = wrap(selectedIndex - 6, items.size());
            case PAGE_DOWN -> selectedIndex = wrap(selectedIndex + 6, items.size());
            case LEFT, BACK -> back();
            case RIGHT, OK, ENTER, EXE -> activateMenuItem();
            case AC -> closeAllPopups();
            case SETTINGS -> switchPopup(CnCwScreen.SETTINGS);
            case CATALOG -> switchPopup(CnCwScreen.CATALOG);
            case TOOLS -> switchPopup(CnCwScreen.TOOLS);
            case VARIABLE -> switchPopup(CnCwScreen.VARIABLES);
            case FUNCTION -> switchPopup(CnCwScreen.FUNCTIONS);
            case FORMAT -> switchPopup(CnCwScreen.FORMAT);
            default -> { }
        }
    }

    /**
     * Read-only scheduling contract. The core owns command meaning; adapters
     * must not guess from key labels whether OK dismisses, advances or evaluates.
     */
    public boolean requiresEvaluation(CnCwKey key) {
        if (!poweredOn || !screen.isApplication() || applicationLanding || engineeringMode) return false;
        if (key != CnCwKey.EXE && key != CnCwKey.OK && key != CnCwKey.ENTER) return false;
        if (errorShown && (key == CnCwKey.OK || key == CnCwKey.ENTER)) return false;
        if (workflowSession != null) return key == CnCwKey.EXE;
        return !tokens.isEmpty();
    }

    private void handleApplication(CnCwKey key) {
        if (errorShown) {
            switch (key) {
                case OK, ENTER, BACK -> { dismissError(); return; }
                case AC -> {
                    // AC is an all-clear action even while an error overlay is
                    // visible.  Dismissing only the error leaves the invalid
                    // expression behind and forces the user to press AC twice.
                    if (shiftArmed) powerOff();
                    else clearExpression();
                    return;
                }
                case LEFT -> {
                    dismissError();
                    semanticCursorOverride = null;
                    cursor = Math.max(0, cursor - 1);
                    return;
                }
                case RIGHT -> {
                    dismissError();
                    semanticCursorOverride = null;
                    cursor = Math.min(tokens.size(), cursor + 1);
                    return;
                }
                default -> { }
            }
        }
        if (engineeringMode) {
            switch (key) {
                case LEFT -> {
                    engineeringExponent -= 3;
                    refreshEngineeringResult();
                    return;
                }
                case RIGHT -> {
                    engineeringExponent += 3;
                    refreshEngineeringResult();
                    return;
                }
                case BACK -> { restoreOriginalFormat(); return; }
                case AC -> {
                    engineeringMode = false;
                    formatConverted = false;
                    clearExpression();
                    return;
                }
                case FORMAT -> { engineeringMode = false; openPopup(CnCwScreen.FORMAT); return; }
                default -> {
                    status = "ENG 模式 · 用 ←/→ 移动小数点";
                    return;
                }
            }
        }
        if (formatConverted && key == CnCwKey.BACK) {
            restoreOriginalFormat();
            return;
        }
        if (workflowSession != null && handleWorkflowSessionKey(key)) return;

        switch (key) {
            case SETTINGS -> { openPopup(CnCwScreen.SETTINGS); return; }
            case CATALOG -> { openPopup(CnCwScreen.CATALOG); return; }
            case TOOLS -> { openPopup(CnCwScreen.TOOLS); return; }
            case VARIABLE -> { openPopup(CnCwScreen.VARIABLES); return; }
            case FUNCTION -> { openPopup(CnCwScreen.FUNCTIONS); return; }
            case FORMAT -> {
                // On the 991 keycap Ans is the SHIFT legend of the format
                // key.  The unshifted key opens the format overlay.
                if (shiftArmed) insertKey(CnCwKey.ANS);
                else openPopup(CnCwScreen.FORMAT);
                return;
            }
            case BACK -> {
                if (!tokens.isEmpty() || resultShown) clearExpression();
                else applicationLanding = true;
                return;
            }
            case AC -> {
                if (shiftArmed) powerOff();
                else clearExpression();
                return;
            }
            default -> { }
        }

        if (spreadsheetGrid && tokens.isEmpty()) {
            switch (key) {
                case LEFT -> { moveSpreadsheetCell(0, -1); return; }
                case RIGHT -> { moveSpreadsheetCell(0, 1); return; }
                case UP -> { moveSpreadsheetCell(-1, 0); return; }
                case DOWN -> { moveSpreadsheetCell(1, 0); return; }
                case DEL -> { clearSpreadsheetCell(); return; }
                default -> { }
            }
        }

        if (applicationLanding) {
            List<CnCwCommand> commands = modeCommands(application);
            switch (key) {
                case UP, LEFT -> selectedIndex = wrap(selectedIndex - 1, commands.size());
                case DOWN, RIGHT -> selectedIndex = wrap(selectedIndex + 1, commands.size());
                case PAGE_UP -> selectedIndex = wrap(selectedIndex - 6, commands.size());
                case PAGE_DOWN -> selectedIndex = wrap(selectedIndex + 6, commands.size());
                case OK, ENTER, EXE -> beginModeCommand(commands.get(selectedIndex));
                default -> {
                    if (isEntryKey(key)) {
                        workflowSession = null;
                        applicationLanding = false;
                        selectedIndex = 0;
                        insertKey(key);
                    }
                }
            }
            return;
        }

        switch (key) {
            case LEFT -> {
                if (shiftArmed) {
                    semanticCursorOverride = null;
                    extendSelection(-1);
                } else {
                    boolean hadSelection = selectionActive();
                    collapseSelection(-1);
                    if (hadSelection) {
                        semanticCursorOverride = null;
                        cursor = Math.max(0, cursor - 1);
                    } else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)
                            && !moveRadicalHorizontal(-1) && !moveFunctionHorizontal(-1)) {
                        semanticCursorOverride = null;
                        cursor = Math.max(0, cursor - 1);
                    }
                }
                shiftArmed = false;
                result = "";
                resultShown = false;
                errorShown = false;
                lastError = null;
            }
            case RIGHT -> {
                if (shiftArmed) {
                    semanticCursorOverride = null;
                    extendSelection(1);
                } else {
                    boolean hadSelection = selectionActive();
                    collapseSelection(1);
                    if (hadSelection) {
                        semanticCursorOverride = null;
                        cursor = Math.min(tokens.size(), cursor + 1);
                    } else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)
                            && !moveRadicalHorizontal(1) && !moveFunctionHorizontal(1)) {
                        semanticCursorOverride = null;
                        cursor = Math.min(tokens.size(), cursor + 1);
                    }
                }
                shiftArmed = false;
                result = "";
                resultShown = false;
                errorShown = false;
                lastError = null;
            }
            case UP -> {
                if (!moveFractionVertical(-1) && !movePowerVertical(-1)
                        && !moveRadicalVertical(-1)) recallHistory(-1);
            }
            case DOWN -> {
                if (!moveFractionVertical(1) && !movePowerVertical(1)
                        && !moveRadicalVertical(1)) recallHistory(1);
            }
            case PAGE_UP -> recallHistory(-6);
            case PAGE_DOWN -> recallHistory(6);
            case DEL -> {
                if (shiftArmed) {
                    overwriteMode = !overwriteMode;
                    shiftArmed = false;
                    status = overwriteMode ? "覆盖输入" : "插入输入";
                } else {
                    deleteBeforeCursor();
                }
            }
            case OK, ENTER, EXE -> {
                boolean approximate = key == CnCwKey.EXE && shiftArmed;
                shiftArmed = false;
                evaluate(approximate);
            }
            // Keep the keyboard '=' key as a relation token.  In particular,
            // do not fold it into the EXE/OK/ENTER execution branch above.
            case EQUALS -> insertKey(CnCwKey.EQUALS);
            default -> insertKey(key);
        }
    }

    private void beginModeCommand(CnCwCommand command) {
        applicationLanding = false;
        selectedIndex = 0;
        clearExpression();
        applicationLanding = false;
        activeCommandId = command.id();
        if ((application == ApplicationMode.MATRIX && command.id().equals("matrix-ans"))
                || (application == ApplicationMode.VECTOR && command.id().equals("vector-ans"))) {
            try {
                CnCwModeEngine.ModeResult modeResult = linearAlgebraMemory.answer(application);
                applicationResult = modeResult;
                double scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
                commitSuccessfulResult(modeResult.display(), scalar, null, "", true, false);
            } catch (IllegalArgumentException error) {
                commitCalculationError(CalculationError.ARGUMENT, 0);
            }
            return;
        }
        CnCwWorkflowSpec.WorkflowSpec workflowSpec =
                CnCwWorkflowSpec.forCommand(application, command.id());
        workflowSession = workflowSpec == null ? null : CnCwWorkflowSession.create(workflowSpec);
        if (workflowSession != null) {
            status = workflowStatus();
            return;
        }
        status = workflowPrompt(application, command);
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("sheet")) {
            spreadsheetGrid = true;
            spreadsheetRow = 0;
            spreadsheetColumn = 0;
            status = "A1 · 数据表格";
        }
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("recalc")) {
            spreadsheet.recalculate();
            result = "重新计算完成\n剩余 " + spreadsheet.remainingBytes() + " bytes";
            resultShown = true;
            committedCalculationState = CnCwCalculationState.textResult(result);
        }
    }

    /** Executes one core-owned workflow action without duplicating layout rules in Android. */
    public CnCwUiState performWorkflowAction(CnCwWorkflowAction.Type type) {
        if (type == null || workflowSession == null || resultShown) return state;
        if (type == CnCwWorkflowAction.Type.EXECUTE) return dispatch(CnCwKey.EXE);
        if (type == CnCwWorkflowAction.Type.BACK) return dispatch(CnCwKey.BACK);
        commitWorkflowCell();
        boolean changed = workflowSession.applyAction(type);
        if (changed) loadWorkflowCell();
        status = changed ? workflowStatus() : "操作不可用 · " + workflowStatus();
        publish();
        return state;
    }

    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
        if (workflowSession == null || resultShown) return state;
        commitWorkflowCell();
        if (workflowSession.selectCell(row, column)) loadWorkflowCell();
        status = workflowStatus();
        publish();
        return state;
    }

    private boolean handleWorkflowSessionKey(CnCwKey key) {
        if (workflowSession == null) return false;

        if (key == CnCwKey.BACK) {
            if (resultShown) {
                result = "";
                resultShown = false;
                applicationResult = null;
                loadWorkflowCell();
                status = workflowStatus();
            } else if (!tokens.isEmpty()) {
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                workflowSession.setSelectedCell("");
                status = workflowStatus();
            } else {
                workflowSession = null;
                activeCommandId = "";
                applicationLanding = true;
                status = applicationStatus();
            }
            return true;
        }

        if (key == CnCwKey.AC) {
            if (shiftArmed) {
                powerOff();
            } else {
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                workflowSession.setSelectedCell("");
                result = "";
                resultShown = false;
                applicationResult = null;
                status = workflowStatus();
            }
            shiftArmed = false;
            return true;
        }

        // Preserve the historical comma bridge for ratio mode.
        if (key == CnCwKey.COMMA
                && workflowSession.spec().mode() == ApplicationMode.RATIO
                && workflowSession.selectedRow() == 0
                && workflowSession.selectedColumn() == 0) {
            workflowSession = null;
            status = "比例 · 兼容逗号输入";
            return false;
        }

        if (workflowSession.selectedIsChoice() && !shiftArmed) {
            if (key == CnCwKey.LEFT || key == CnCwKey.UP
                    || key == CnCwKey.RIGHT || key == CnCwKey.DOWN) {
                int delta = (key == CnCwKey.LEFT || key == CnCwKey.UP) ? -1 : 1;
                workflowSession.cycleSelectedChoice(delta);
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                status = workflowStatus();
                return true;
            }
            if (key == CnCwKey.DEL) {
                workflowSession.resetSelectedChoice();
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                status = workflowStatus();
                return true;
            }
            if (isEntryKey(key)) {
                status = "用方向键选择 · " + workflowStatus();
                return true;
            }
        }

        if (shiftArmed && (key == CnCwKey.UP || key == CnCwKey.DOWN
                || key == CnCwKey.LEFT || key == CnCwKey.RIGHT)) {
            commitWorkflowCell();
            boolean changed = resizeWorkflowFromShift(key);
            shiftArmed = false;
            if (changed) loadWorkflowCell();
            status = changed ? workflowStatus() : "已到输入尺寸边界";
            return true;
        }

        if (key == CnCwKey.UP || key == CnCwKey.DOWN) {
            commitWorkflowCell();
            if (workflowSession.move(key == CnCwKey.UP ? -1 : 1, 0)) loadWorkflowCell();
            status = workflowStatus();
            return true;
        }

        if (key == CnCwKey.LEFT && !selectionActive() && cursor == 0) {
            commitWorkflowCell();
            if (workflowSession.move(0, -1)) {
                loadWorkflowCell();
                status = workflowStatus();
                return true;
            }
            return false;
        }
        if (key == CnCwKey.RIGHT && !selectionActive() && cursor == tokens.size()) {
            commitWorkflowCell();
            if (workflowSession.move(0, 1)) {
                loadWorkflowCell();
                status = workflowStatus();
                return true;
            }
            return false;
        }

        if (key == CnCwKey.OK || key == CnCwKey.ENTER) {
            commitWorkflowCell();
            advanceWorkflowCell();
            loadWorkflowCell();
            status = workflowStatus();
            return true;
        }

        if (key == CnCwKey.EXE) {
            shiftArmed = false;
            commitWorkflowCell();
            CnCwWorkflowValidation.Report validation =
                    CnCwWorkflowValidation.validate(workflowSession);
            if (!validation.ready()) {
                workflowSession.selectCell(validation.firstProblemRow(),
                        validation.firstProblemColumn());
                loadWorkflowCell();
                CnCwWorkflowValidation.CellState problem = validation.cell(
                        validation.firstProblemRow(), validation.firstProblemColumn(),
                        workflowSession.columns());
                status = problem.status() == CnCwWorkflowValidation.Status.EMPTY
                        ? "还有空白项 · " + workflowStatus()
                        : "输入格式错误 · " + workflowStatus();
                return true;
            }
            String source = workflowSession.legacySource();
            List<Token> imported = parsePastedTokens(source);
            if (imported == null) {
                status = "输入包含无法识别的内容";
                return true;
            }
            tokens.clear();
            tokens.addAll(imported);
            cursor = tokens.size();
            semanticCursorOverride = null;
            clearSelection();
            evaluate(false);
            return true;
        }
        return false;
    }

    private void commitWorkflowCell() {
        if (workflowSession == null || resultShown || workflowSession.selectedIsChoice()) return;
        String source = evaluationSource().replace("\u2063", "");
        workflowSession.setSelectedCell(source);
    }

    private void loadWorkflowCell() {
        if (workflowSession == null) return;
        tokens.clear();
        semanticCursorOverride = null;
        clearSelection();
        String source = workflowSession.selectedCell();
        if (!workflowSession.selectedIsChoice() && !com.codex.fx991.core.Compat.isBlank(source)) {
            List<Token> imported = parsePastedTokens(source);
            if (imported != null) tokens.addAll(imported);
        }
        cursor = tokens.size();
        result = "";
        resultShown = false;
        applicationResult = null;
        errorShown = false;
        lastError = null;
    }

    private void advanceWorkflowCell() {
        int row = workflowSession.selectedRow();
        int column = workflowSession.selectedColumn();
        if (column + 1 < workflowSession.columns()) {
            workflowSession.selectCell(row, column + 1);
            return;
        }
        if (row + 1 < workflowSession.rows()) {
            workflowSession.selectCell(row + 1, 0);
            return;
        }
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        if ((layout == CnCwWorkflowSpec.InputLayout.SERIES
                || layout == CnCwWorkflowSpec.InputLayout.PAIRED_SERIES)
                && workflowSession.appendRow()) {
            workflowSession.selectCell(workflowSession.rows() - 1, 0);
        }
    }

    private boolean resizeWorkflowFromShift(CnCwKey key) {
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        CnCwWorkflowAction.Type action = null;
        if (layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            action = switch (key) {
                case UP -> CnCwWorkflowAction.Type.DECREASE_ROWS;
                case DOWN -> CnCwWorkflowAction.Type.INCREASE_ROWS;
                case LEFT -> CnCwWorkflowAction.Type.DECREASE_COLUMNS;
                case RIGHT -> CnCwWorkflowAction.Type.INCREASE_COLUMNS;
                default -> null;
            };
        } else if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && workflowSession.spec().mode() == ApplicationMode.EQUATION) {
            action = switch (key) {
                case UP -> CnCwWorkflowAction.Type.DECREASE_ROWS;
                case DOWN -> CnCwWorkflowAction.Type.INCREASE_ROWS;
                default -> null;
            };
        }
        return action != null && workflowSession.applyAction(action);
    }

    private String workflowStatus() {
        if (workflowSession == null) return applicationStatus();
        CnCwWorkflowSpec.FieldSpec field = workflowSession.selectedField();
        String fieldLabel = field == null ? ""
                : " · " + field.label()
                + (workflowSession.selectedIsChoice()
                ? "=" + workflowSession.selectedDisplayCell() : "");
        return workflowSession.spec().title() + fieldLabel + " · "
                + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
    }

    private void moveSpreadsheetCell(int rowDelta, int columnDelta) {
        spreadsheetRow = Math.max(0, Math.min(SpreadsheetModel.ROWS - 1,
                spreadsheetRow + rowDelta));
        spreadsheetColumn = Math.max(0, Math.min(SpreadsheetModel.COLUMNS - 1,
                spreadsheetColumn + columnDelta));
        status = spreadsheetAddress() + " · 数据表格";
    }

    private void clearSpreadsheetCell() {
        try {
            spreadsheet.clear(spreadsheetAddress());
            result = "";
            resultShown = false;
            status = spreadsheetAddress() + " 已清除";
        } catch (RuntimeException error) {
            commitCalculationError(CalculationError.MATH, 0);
        }
    }

    private String spreadsheetAddress() {
        return (char) ('A' + spreadsheetColumn) + Integer.toString(spreadsheetRow + 1);
    }

    private void insertKey(CnCwKey key) {
        Token token = shiftArmed && key == CnCwKey.PI && application == ApplicationMode.COMPLEX
                ? token("i") : tokenFor(key, shiftArmed);
        shiftArmed = false;
        if (token == null) return;
        semanticCursorOverride = null;
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";

        rememberUndo();
        if (prepareContinuousVerification(token)) return;
        deleteSelectionIfPresent();
        if (resultShown) {
            if (token.binary && hasAns) {
                tokens.clear();
                tokens.add(new Token("Ans", "Ans", false));
                cursor = 1;
            } else {
                tokens.clear();
                cursor = 0;
            }
        }
        if (token.binary && cursor == 0 && hasAns) {
            tokens.add(new Token("Ans", "Ans", false));
            cursor = 1;
        }
        if (token.binary && cursor > 0 && tokens.get(cursor - 1).binary) {
            tokens.set(cursor - 1, token);
        } else if (overwriteMode && cursor < tokens.size() && !token.binary) {
            tokens.set(cursor, token);
            cursor++;
        } else {
            tokens.add(cursor, token);
            cursor++;
        }
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        originalResult = "";
        status = applicationStatus();
    }

    private static boolean isRelationToken(Token token) {
        return token.evaluation.equals("=") || token.evaluation.equals("!=")
                || token.evaluation.equals("<") || token.evaluation.equals("<=")
                || token.evaluation.equals(">") || token.evaluation.equals(">=");
    }

    private boolean prepareContinuousVerification(Token token) {
        if (!resultShown || !verificationMode || application != ApplicationMode.CALCULATE
                || !isRelationToken(token)) return false;
        int relation = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (isRelationToken(tokens.get(index))) relation = index;
        }
        List<Token> rightSide = relation >= 0 && relation + 1 < tokens.size()
                ? new ArrayList<>(tokens.subList(relation + 1, tokens.size()))
                : new ArrayList<>();
        tokens.clear();
        tokens.addAll(rightSide);
        tokens.add(token);
        cursor = tokens.size();
        result = "";
        resultShown = false;
        lastExactResult = null;
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        status = "连续验证";
        return true;
    }

    private void deleteBeforeCursor() {
        shiftArmed = false;
        if (deleteSelectionIfPresent()) return;
        if (deleteFractionSemantic()) return;
        if (deletePowerSemantic()) return;
        if (deleteRadicalSemantic()) return;
        if (deleteFunctionSemantic()) return;
        if (cursor <= 0 || tokens.isEmpty()) return;
        semanticCursorOverride = null;
        resetStatementSequence();
        rememberUndo();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        tokens.remove(cursor - 1);
        cursor--;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
    }

    /** Moves the active end of a semantic token selection by one token. */
    private void extendSelection(int direction) {
        semanticCursorOverride = null;
        if (selectionAnchor < 0) selectionAnchor = cursor;
        int next = semanticSelectionTarget(cursor, direction);
        cursor = next;
        selectionFocus = cursor;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
    }

    /**
     * Returns the next selection boundary without splitting the common
     * structures users perceive as one expression unit.
     */
    private int semanticSelectionTarget(int position, int direction) {
        if (direction < 0) {
            if (position <= 0) return 0;
            int atomStart = semanticAtomStart(position);
            if (atomStart > 0 && isPowerTemplate(tokens.get(atomStart - 1))) {
                return powerBaseStart(atomStart - 1);
            }
            return atomStart;
        }
        if (position >= tokens.size()) return tokens.size();
        int atomEnd = semanticAtomEnd(position);
        if (atomEnd < tokens.size() && isFractionTemplate(tokens.get(atomEnd))) {
            FractionBounds fraction = fractionBounds(atomEnd);
            return fraction == null ? semanticAtomEnd(atomEnd + 1) : fraction.denominatorEnd;
        }
        if (atomEnd < tokens.size() && isPowerTemplate(tokens.get(atomEnd))) {
            PowerBounds power = powerBounds(atomEnd);
            return power == null ? semanticAtomEnd(atomEnd + 1) : power.exponentEnd;
        }
        return atomEnd;
    }

    private int semanticAtomStart(int position) {
        int safe = Math.max(0, Math.min(tokens.size(), position));
        if (safe == 0) return 0;
        Token previous = tokens.get(safe - 1);
        // The fraction-template separator belongs to the structure around it;
        // never expose it as an independent selection unit.
        if (isFractionTemplate(previous)) {
            return fractionNumeratorStart(safe - 1);
        }
        if (isPowerTemplate(previous)) {
            return powerBaseStart(safe - 1);
        }
        if (isNumericFragment(previous)) {
            int start = safe - 1;
            while (start > 0 && isNumericFragment(tokens.get(start - 1))) start--;
            return start;
        }
        if (")".equals(previous.evaluation)) {
            int open = matchingOpen(safe - 1);
            return open >= 0 ? open : safe - 1;
        }
        return safe - 1;
    }

    private int semanticAtomEnd(int position) {
        int safe = Math.max(0, Math.min(tokens.size(), position));
        if (safe >= tokens.size()) return tokens.size();
        Token current = tokens.get(safe);
        if (isFractionTemplate(current)) {
            FractionBounds fraction = fractionBounds(safe);
            return fraction == null ? safe + 1 : fraction.denominatorEnd;
        }
        if (isPowerTemplate(current)) {
            PowerBounds power = powerBounds(safe);
            return power == null ? safe + 1 : power.exponentEnd;
        }
        if (isNumericFragment(current)) {
            int end = safe + 1;
            while (end < tokens.size() && isNumericFragment(tokens.get(end))) end++;
            return end;
        }
        if (opensParenthesis(current)) {
            int close = matchingClose(safe);
            return close >= 0 ? close + 1 : safe + 1;
        }
        return safe + 1;
    }

    private int matchingOpen(int closeIndex) {
        int depth = 0;
        for (int index = closeIndex; index >= 0; index--) {
            String value = tokens.get(index).evaluation;
            if (")".equals(value)) depth++;
            else if (opensParenthesis(tokens.get(index))) {
                depth--;
                if (depth == 0) return index;
            }
        }
        return -1;
    }

    private int matchingClose(int openIndex) {
        int depth = 0;
        for (int index = openIndex; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (opensParenthesis(token)) depth++;
            else if (")".equals(token.evaluation)) {
                depth--;
                if (depth == 0) return index;
            }
        }
        return -1;
    }

    private int enclosingOpen(int index) {
        int depth = 0;
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            Token token = tokens.get(cursor);
            if (")".equals(token.evaluation)) {
                depth++;
            } else if (opensParenthesis(token)) {
                if (depth == 0) return cursor;
                depth--;
            }
        }
        return -1;
    }

    private static boolean opensParenthesis(Token token) {
        return token.evaluation.endsWith("(") || "(".equals(token.evaluation)
                || isFixedRootTemplate(token);
    }

    /** Collapses an existing selection toward the requested movement side. */
    private void collapseSelection(int direction) {
        if (!selectionActive()) return;
        cursor = direction < 0 ? selectionStart() : selectionEnd();
        clearSelection();
    }

    private boolean selectionActive() {
        return selectionAnchor >= 0 && selectionFocus >= 0
                && selectionAnchor != selectionFocus;
    }

    private int selectionStart() {
        return Math.min(selectionAnchor, selectionFocus);
    }

    private int selectionEnd() {
        return Math.max(selectionAnchor, selectionFocus);
    }

    private void clearSelection() {
        selectionAnchor = -1;
        selectionFocus = -1;
    }

    /** Deletes the selected semantic token range and places the cursor at its start. */
    private boolean deleteSelectionIfPresent() {
        if (!selectionActive()) return false;
        rememberUndo();
        int start = selectionStart();
        int end = selectionEnd();
        tokens.subList(start, end).clear();
        cursor = start;
        semanticCursorOverride = null;
        clearSelection();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = applicationStatus();
        return true;
    }

    /** Returns the selected evaluable source without the cursor marker. */
    public String selectedExpression() {
        if (!selectionActive()) return "";
        StringBuilder text = new StringBuilder();
        for (int index = selectionStart(); index < selectionEnd(); index++) {
            // Clipboard/export must use the evaluator spelling, not keycap
            // labels such as `a/b`, `√(`, or `π`.  The display spelling is
            // intentionally visual and is not a valid pasted expression.
            text.append(tokens.get(index).evaluation);
        }
        return text.toString();
    }

    public boolean hasSelection() { return selectionActive(); }
    public int selectionStartIndex() { return selectionActive() ? selectionStart() : cursor; }
    public int selectionEndIndex() { return selectionActive() ? selectionEnd() : cursor; }

    private void rememberUndo() {
        undoTokens = com.codex.fx991.core.Compat.copyList(tokens);
        undoCursor = cursor;
    }

    private void undo() {
        if (undoTokens == null) {
            status = "没有可撤消的操作";
            return;
        }
        resetStatementSequence();
        List<Token> current = com.codex.fx991.core.Compat.copyList(tokens);
        int currentCursor = cursor;
        tokens.clear();
        tokens.addAll(undoTokens);
        cursor = Math.min(undoCursor, tokens.size());
        semanticCursorOverride = null;
        undoTokens = current;
        undoCursor = currentCursor;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = "撤消";
        closeAllPopups();
    }

    private void evaluate(boolean forceDecimal) {
        if (tokens.isEmpty()) return;
        applicationResult = null;
        String source = autoCloseParentheses(evaluationSource());
            String plainSource = source.replace("\u2063", "");
            boolean complexEvaluation = application == ApplicationMode.COMPLEX
                    || needsComplexEvaluation(plainSource)
                    || (hasComplexAns && plainSource.contains("Ans"));
        errorShown = false;
        lastError = null;
        try (CalculationBudget.Scope budget = CalculationBudget.open()) {
            if (!pendingFunctionDefinition.isEmpty()) {
                ScalarExpressionEngine.CompiledExpression definition =
                        ScalarExpressionEngine.compile(source);
                if (pendingFunctionDefinition.equals("f")) {
                    functionF = definition;
                    functionFSource = source;
                } else {
                    functionG = definition;
                    functionGSource = source;
                }
                result = pendingFunctionDefinition + "(x)=" + displayExpressionWithoutCursor();
                status = "已定义 " + pendingFunctionDefinition + "(x)";
                pendingFunctionDefinition = "";
                resultShown = true;
                committedCalculationState = CnCwCalculationState.textResult(result);
                return;
            }
            String formatted;
            double scalar;
            ExactValue exactScalar = null;
            String completionStatus = "";
            boolean storeAnswer = true;
            if (verificationMode && application == ApplicationMode.CALCULATE) {
                boolean valid = ScalarExpressionEngine.verify(source, evaluationContext());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
            } else if (verificationMode && application == ApplicationMode.COMPLEX) {
                Map<String, ComplexValue> complexVariables = new HashMap<>();
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    complexVariables.put(entry.getKey(), new ComplexValue(entry.getValue(), 0.0));
                }
                boolean valid = ComplexExpressionEngine.verify(plainSource, complexVariables,
                        hasComplexAns ? complexAns
                                : hasAns ? new ComplexValue(ans, 0.0) : ComplexValue.ZERO,
                        settings.angleUnit());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
                storeAnswer = false;
                complexEvaluation = false;
            } else if (application == ApplicationMode.CALCULATE
                    && hasStatementOperator(source)) {
                String statement = source;
                if (source.indexOf(':') >= 0) {
                    if (!source.equals(statementSequenceSource) || statementSequence.isEmpty()
                            || statementSequenceIndex >= statementSequence.size()) {
                        statementSequenceSource = source;
                        statementSequence = StatementEngine.splitStatements(source);
                        statementSequenceIndex = 0;
                    }
                    statement = statementSequence.get(statementSequenceIndex++);
                }
                StatementEngine.Outcome outcome = StatementEngine.evaluate(statement, variables,
                        settings.angleUnit(), hasAns ? ans : 0.0, random);
                variables.clear();
                variables.putAll(outcome.variables());
                exactVariables.clear();
                scalar = outcome.ans();
                exactScalar = simpleExact(scalar);
                formatted = formatResult(scalar, exactScalar, statement, false);
            } else if (application == ApplicationMode.SPREADSHEET && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                CnCwModeEngine.ModeResult modeResult = spreadsheetWorkflow(plainSource);
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (!com.codex.fx991.core.Compat.isBlank(activeCommandId) && isStructuredWorkflow(application)) {
                CnCwModeEngine.ModeResult modeResult;
                if (linearAlgebraMemory.handles(application, activeCommandId)) {
                    modeResult = linearAlgebraMemory.evaluate(application, activeCommandId,
                            workflowSession == null ? null : workflowSession.snapshot(),
                            evaluationContext());
                } else {
                    modeResult = CnCwModeEngine.evaluate(
                            application, activeCommandId, plainSource, evaluationContext());
                }
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (application == ApplicationMode.BASE_N
                    && workflowSession != null
                    && CnCwBaseNWorkflow.handles(activeCommandId)) {
                CnCwModeEngine.ModeResult modeResult = CnCwBaseNWorkflow.evaluate(
                        activeCommandId, workflowSession.snapshot());
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (application == ApplicationMode.BASE_N && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                BaseNEngine.Base base = switch (activeCommandId) {
                    case "hex" -> BaseNEngine.Base.HEXADECIMAL;
                    case "binary" -> BaseNEngine.Base.BINARY;
                    case "octal" -> BaseNEngine.Base.OCTAL;
                    default -> BaseNEngine.Base.DECIMAL;
                };
                int value = BaseNEngine.parse(plainSource, base);
                formatted = BaseNEngine.format(value, base);
                scalar = value;
            } else if (complexEvaluation) {
                Map<String, ComplexValue> complexVariables = new HashMap<>();
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    complexVariables.put(entry.getKey(), new ComplexValue(entry.getValue(), 0.0));
                }
                ComplexValue value = ComplexExpressionEngine.evaluate(plainSource, complexVariables,
                        hasComplexAns ? complexAns
                                : hasAns ? new ComplexValue(ans, 0.0) : ComplexValue.ZERO,
                        settings.angleUnit());
                formatted = formatComplex(value);
                scalar = value.real();
                complexAns = value;
                hasComplexAns = true;
            } else {
                SimplificationResult simplification = simplificationCall(plainSource);
                CoordinateCall coordinate = coordinateCall(plainSource);
                int remainderIndex = standaloneRemainderIndex(source);
                if (simplification != null) {
                    scalar = simplification.value();
                    exactScalar = ExactValue.rational(new Rational(
                            java.math.BigInteger.valueOf(simplification.originalNumerator()),
                            java.math.BigInteger.valueOf(simplification.originalDenominator())));
                    formatted = simplification.displayNumerator() + "/"
                            + simplification.displayDenominator();
                    completionStatus = simplification.canContinue()
                            ? "还可继续化简" : "已化简";
                } else if (coordinate != null) {
                    double first = ScalarExpressionEngine.evaluate(coordinate.first(),
                            evaluationContext());
                    double second = ScalarExpressionEngine.evaluate(coordinate.second(),
                            evaluationContext());
                    ManualFunctions.CoordinatePair pair = coordinate.polar()
                            ? ManualFunctions.polar(first, second, settings.angleUnit())
                            : ManualFunctions.rectangular(first, second, settings.angleUnit());
                    variables.put("x", pair.first());
                    variables.put("y", pair.second());
                    ExactValue firstExact = simpleExact(pair.first());
                    ExactValue secondExact = simpleExact(pair.second());
                    if (firstExact == null) exactVariables.remove("x");
                    else exactVariables.put("x", firstExact);
                    if (secondExact == null) exactVariables.remove("y");
                    else exactVariables.put("y", secondExact);
                    scalar = pair.first();
                    exactScalar = firstExact;
                    formatted = coordinate.polar()
                            ? "r=" + formatResult(pair.first(), firstExact, "", true)
                            + "\nθ=" + formatResult(pair.second(), secondExact, "", true)
                            : "x=" + formatResult(pair.first(), firstExact, "", true)
                            + "\ny=" + formatResult(pair.second(), secondExact, "", true);
                } else if (remainderIndex >= 0) {
                    double dividend = ScalarExpressionEngine.evaluate(
                            source.substring(0, remainderIndex), evaluationContext());
                    double divisor = ScalarExpressionEngine.evaluate(
                            source.substring(remainderIndex + 2), evaluationContext());
                    ManualFunctions.RemainderResult division =
                            ManualFunctions.divideWithRemainder(dividend, divisor);
                    scalar = division.ansValue();
                    exactScalar = scalar == Math.rint(scalar) && Math.abs(scalar) <= Long.MAX_VALUE
                            ? ExactValue.integer((long) scalar) : null;
                    formatted = division.remainderMode()
                            ? "商=" + formatNumber(division.quotient())
                            + "\n余数=" + formatNumber(division.remainder())
                            : formatNumber(division.quotient());
                } else {
                    ScalarExpressionEngine.EvaluationResult evaluation =
                            ScalarExpressionEngine.evaluateDetailed(source, evaluationContext());
                    scalar = evaluation.value();
                    exactScalar = evaluation.exactValue();
                    formatted = forceDecimal ? formatNumber(scalar)
                            : formatResult(scalar, exactScalar, source, false);
                }
                if (application == ApplicationMode.BASE_N) {
                    if (!Double.isFinite(scalar) || scalar != Math.rint(scalar)
                            || scalar < Integer.MIN_VALUE || scalar > Integer.MAX_VALUE) {
                        throw new ArithmeticException("32-bit integer required");
                    }
                    formatted = BaseNEngine.format((int) scalar, BaseNEngine.Base.DECIMAL);
                }
            }
            budget.checkpoint();
            commitSuccessfulResult(formatted, scalar, exactScalar, completionStatus,
                    storeAnswer, complexEvaluation);
        } catch (CancellationException canceled) {
            // The adapter owns cancellation; never turn an abandoned job into an error result.
            throw canceled;
        } catch (CalculationException error) {
            commitCalculationError(error.error(), error.position());
        } catch (ArithmeticException error) {
            commitCalculationError(CalculationError.MATH, 0);
        } catch (IllegalArgumentException error) {
            commitCalculationError(CalculationError.ARGUMENT, 0);
        } catch (RuntimeException error) {
            commitCalculationError(CalculationError.SYNTAX, 0);
        }
    }

    /**
     * Single success-commit gate for EXE evaluation. Numerical branches only
     * compute a payload; answer ownership, visible result state and history are
     * committed here so later Stage 6 steps can replace the legacy fields
     * without duplicating policy.
     */
    private void commitSuccessfulResult(String formatted,
                                        double scalar,
                                        ExactValue exactScalar,
                                        String completionStatus,
                                        boolean storeAnswer,
                                        boolean complexEvaluation) {
        if (!Double.isFinite(scalar)) {
            throw new CalculationException(CalculationError.MATH, "Non-finite result", 0);
        }
        String evaluatedProcessDisplay = expandedProcessDisplay(tokens, ansProcessDisplay);
        resultProcessDisplay = evaluatedProcessDisplay;
        if (storeAnswer && application != ApplicationMode.INEQUALITY) {
            ansProcessDisplay = evaluatedProcessDisplay;
            ans = scalar;
            hasAns = true;
            exactAns = exactScalar;
            if (!complexEvaluation) {
                complexAns = new ComplexValue(scalar, 0.0);
                hasComplexAns = false;
            }
        }
        lastExactResult = exactScalar;
        result = formatted;
        resultShown = true;
        originalResult = formatted;
        formatConverted = false;
        engineeringMode = false;
        committedCalculationState = successfulCalculationState(
                formatted, scalar, exactScalar, complexEvaluation);
        status = !completionStatus.isEmpty() ? completionStatus
                : statementSequence.isEmpty() ? applicationStatus()
                : "语句 " + Math.min(statementSequenceIndex, statementSequence.size())
                + "/" + statementSequence.size();
        history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                resultProcessDisplay, applicationResult, committedCalculationState));
        if (history.size() > 100) history.remove(0);
        historyIndex = history.size();
        if (spreadsheetGrid && application == ApplicationMode.SPREADSHEET
                && activeCommandId.equals("sheet")) {
            // EXE commits the cell and returns the editor focus to the grid.
            tokens.clear();
            cursor = 0;
        }
    }

    private CnCwCalculationState successfulCalculationState(String display,
                                                                  double scalar,
                                                                  ExactValue exactScalar,
                                                                  boolean complexEvaluation) {
        if (applicationResult != null) {
            return CnCwCalculationState.applicationResult(display, applicationResult);
        }
        if (complexEvaluation && hasComplexAns) {
            return CnCwCalculationState.complexResult(display, complexAns);
        }
        if (exactScalar != null) {
            return CnCwCalculationState.exactResult(display, scalar, exactScalar);
        }
        return CnCwCalculationState.scalarResult(display, scalar);
    }

    private CnCwCalculationState currentAnswerCalculationState(String display) {
        if (hasComplexAns) return CnCwCalculationState.complexResult(display, complexAns);
        if (exactAns != null) return CnCwCalculationState.exactResult(display, ans, exactAns);
        if (hasAns) return CnCwCalculationState.scalarResult(display, ans);
        return CnCwCalculationState.textResult(display);
    }

    private CoordinateCall coordinateCall(String source) {
        String trimmed = source.trim();
        boolean polar = trimmed.regionMatches(true, 0, "pol(", 0, 4);
        boolean rectangular = trimmed.regionMatches(true, 0, "rec(", 0, 4);
        if ((!polar && !rectangular) || !trimmed.endsWith(")")) return null;
        List<String> arguments = CnCwModeEngine.splitTopLevel(
                trimmed.substring(4, trimmed.length() - 1));
        if (arguments.size() != 2) return null;
        return new CoordinateCall(polar, arguments.get(0), arguments.get(1));
    }

    private SimplificationResult simplificationCall(String source) {
        String trimmed = source.trim();
        if (!trimmed.regionMatches(true, 0, "simp(", 0, 5)
                || !trimmed.endsWith(")")) return null;
        if (!manualSimplification) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Simp requires manual simplification mode", 0);
        }
        List<String> arguments = CnCwModeEngine.splitTopLevel(
                trimmed.substring(5, trimmed.length() - 1));
        if (arguments.isEmpty() || arguments.size() > 2) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Simp argument count", 0);
        }
        int slash = topLevelSlash(arguments.get(0));
        if (slash <= 0 || slash + 1 >= arguments.get(0).length()) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "A fraction is required", 0);
        }
        long numerator = exactLong(ScalarExpressionEngine.evaluate(
                arguments.get(0).substring(0, slash), evaluationContext()));
        long denominator = exactLong(ScalarExpressionEngine.evaluate(
                arguments.get(0).substring(slash + 1), evaluationContext()));
        if (denominator == 0L) {
            throw new CalculationException(CalculationError.MATH,
                    "Zero denominator", slash + 1);
        }
        if (denominator < 0L) {
            numerator = -numerator;
            denominator = -denominator;
        }
        long gcd = gcd(Math.abs(numerator), denominator);
        if (gcd <= 1L) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "Fraction is already irreducible", 0);
        }
        long factor = arguments.size() == 2
                ? exactLong(ScalarExpressionEngine.evaluate(arguments.get(1), evaluationContext()))
                : smallestPrimeFactor(gcd);
        if (factor <= 1L || gcd % factor != 0L) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "Factor does not divide numerator and denominator", 0);
        }
        long displayNumerator = numerator / factor;
        long displayDenominator = denominator / factor;
        boolean canContinue = gcd(Math.abs(displayNumerator), displayDenominator) > 1L;
        return new SimplificationResult(numerator, denominator,
                displayNumerator, displayDenominator, canContinue,
                (double) numerator / denominator);
    }

    private static int topLevelSlash(String source) {
        int depth = 0;
        int found = -1;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && (value == '/' || value == '÷')) {
                if (found >= 0) return -1;
                found = index;
            }
        }
        return depth == 0 ? found : -1;
    }

    private static long exactLong(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Integer required", 0);
        }
        return (long) value;
    }

    private static long gcd(long left, long right) {
        long a = left;
        long b = right;
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.abs(a);
    }

    private static long smallestPrimeFactor(long value) {
        if ((value & 1L) == 0L) return 2L;
        for (long factor = 3L; factor <= value / factor; factor += 2L) {
            if (value % factor == 0L) return factor;
        }
        return value;
    }

    private static ExactValue simpleExact(double value) {
        if (!Double.isFinite(value)) return null;
        long nearest = Math.round(value);
        if (Math.abs(value - nearest) <= 1e-11 * Math.max(1.0, Math.abs(value))) {
            return ExactValue.integer(nearest);
        }
        Rational rational = Rational.approximate(value, 10_000L, 1e-12);
        return Math.abs(rational.toDouble() - value) <= 1e-12 ? ExactValue.rational(rational) : null;
    }

    /** Single error-commit gate for evaluator and mode/tool failures. */
    private void commitCalculationError(CalculationError error, int sourcePosition) {
        applicationResult = null;
        lastExactResult = null;
        lastError = error;
        errorShown = true;
        errorCursor = tokenCursorForSourcePosition(sourcePosition);
        cursor = errorCursor;
        result = error.display();
        resultShown = true;
        committedCalculationState = CnCwCalculationState.error(result, error, errorCursor);
        status = "按 OK、返回或 AC 回到错误位置";
    }

    private int tokenCursorForSourcePosition(int sourcePosition) {
        int position = Math.max(0, sourcePosition);
        int offset = 0;
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0 && needsLexicalBoundary(tokens.get(index - 1), tokens.get(index))) {
                offset++;
            }
            int next = offset + tokens.get(index).evaluation.length();
            if (position < next) return index;
            offset = next;
        }
        return tokens.size();
    }

    /**
     * EXE on the physical CW accepts an unfinished right-parenthesis suffix
     * when the only issue is an unmatched opening parenthesis.  Preserve the
     * visible input and repair only that unambiguous structural omission;
     * unmatched closing parentheses and incomplete operators still report a
     * syntax error.
     */
    private static String autoCloseParentheses(String source) {
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')' && --depth < 0) return source;
        }
        if (depth <= 0) return source;
        StringBuilder completed = new StringBuilder(source.length() + depth);
        completed.append(source);
        for (int index = 0; index < depth; index++) completed.append(')');
        return completed.toString();
    }

    private static boolean hasStatementOperator(String source) {
        return source.indexOf(':') >= 0 || source.indexOf('→') >= 0
                || source.contains("->");
    }

    private String formatStatementResults(List<Double> values) {
        StringBuilder text = new StringBuilder();
        int start = Math.max(0, values.size() - 2);
        for (int index = start; index < values.size(); index++) {
            if (text.length() > 0) text.append('\n');
            text.append(formatNumber(values.get(index)));
        }
        return text.toString();
    }

    /** Returns the ÷R position only when it is the expression's outer operation. */
    private static int standaloneRemainderIndex(String source) {
        int depth = 0;
        int operator = -1;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && value == '÷' && index + 1 < source.length()
                    && (source.charAt(index + 1) == 'R' || source.charAt(index + 1) == 'r')) {
                if (operator >= 0) return -1;
                operator = index++;
            }
            if (depth < 0) return -1;
        }
        if (depth != 0 || operator <= 0 || operator + 2 >= source.length()) return -1;
        depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && (value == '+' || value == '-' || value == '−')) {
                boolean unary = index == 0 || index == operator + 2;
                boolean exponentSign = index > 0
                        && (source.charAt(index - 1) == 'E' || source.charAt(index - 1) == 'e');
                if (!unary && !exponentSign) return -1;
            }
        }
        return operator;
    }

    private ScalarExpressionEngine.EvaluationContext evaluationContext() {
        ScalarExpressionEngine.RoundingKind roundingKind = switch (settings.displayMode()) {
            case FIX -> ScalarExpressionEngine.RoundingKind.FIX;
            case SCI -> ScalarExpressionEngine.RoundingKind.SCI;
            case NORM_1, NORM_2 -> ScalarExpressionEngine.RoundingKind.NORM;
        };
        int digits = roundingKind == ScalarExpressionEngine.RoundingKind.NORM
                ? 10 : settings.displayDigits();
        return new ScalarExpressionEngine.EvaluationContext(variables, exactVariables,
                settings.angleUnit(), hasAns ? ans : 0.0, exactAns, functionF, functionG,
                random, new ScalarExpressionEngine.RoundingPolicy(roundingKind, digits));
    }

    private String displayExpressionWithoutCursor() {
        StringBuilder text = new StringBuilder(tokens.size() * 2);
        for (Token token : tokens) text.append(token.display);
        return text.toString();
    }

    private void recallHistory(int delta) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(0, Math.min(history.size() - 1, historyIndex + delta));
        HistoryEntry entry = history.get(historyIndex);
        rememberUndo();
        tokens.clear();
        tokens.addAll(entry.tokens);
        cursor = tokens.size();
        semanticCursorOverride = null;
        result = entry.result;
        resultProcessDisplay = entry.processDisplay;
        applicationResult = entry.applicationResult;
        committedCalculationState = entry.calculationState;
        lastExactResult = committedCalculationState.exactValue();
        originalResult = result;
        formatConverted = false;
        engineeringMode = false;
        errorShown = false;
        lastError = null;
        resultShown = true;
        status = "历史 " + (historyIndex + 1) + "/" + history.size();
    }

    private void clearExpression() {
        rememberUndo();
        committedCalculationState = CnCwCalculationState.editing();
        applicationResult = null;
        resetStatementSequence();
        tokens.clear();
        cursor = 0;
        semanticCursorOverride = null;
        clearSelection();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        selectedIndex = 0;
        status = applicationStatus();
    }

    private void dismissError() {
        committedCalculationState = CnCwCalculationState.editing();
        if (workflowSession != null) {
            // Evaluation tokens contain the whole serialized worksheet, never a single cell.
            loadWorkflowCell();
            status = workflowStatus();
            return;
        }
        errorShown = false;
        lastError = null;
        result = "";
        resultShown = false;
        cursor = Math.max(0, Math.min(tokens.size(), errorCursor));
        status = applicationStatus();
    }

    private void resetStatementSequence() {
        statementSequenceSource = "";
        statementSequence = com.codex.fx991.core.Compat.list();
        statementSequenceIndex = 0;
    }

    private void showHome() {
        workflowSession = null;
        applicationResult = null;
        navigation.clear();
        screen = CnCwScreen.HOME;
        if (application != null) applicationBeforeHome = application;
        application = null;
        activeCommandId = "";
        selectedIndex = 0;
        shiftArmed = false;
        applicationLanding = false;
        spreadsheetGrid = false;
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        status = "HOME";
    }

    private void powerOff() {
        workflowSession = null;
        linearAlgebraMemory.clear();
        applicationResult = null;
        navigation.clear();
        screen = CnCwScreen.HOME;
        application = null;
        activeCommandId = "";
        selectedIndex = 0;
        shiftArmed = false;
        poweredOn = false;
        overwriteMode = false;
        tokens.clear();
        cursor = 0;
        result = "";
        resultShown = false;
        errorShown = false;
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        clearFunctionDefinitions();
        status = "OFF";
    }

    private void openApplication(ApplicationMode mode) {
        workflowSession = null;
        applicationResult = null;
        if (applicationBeforeHome != null && applicationBeforeHome != mode) {
            // The original 991CN CW clears transient answer/verification state when
            // HOME launches another application, while MatA..MatD/VctA..VctD remain stored.
            linearAlgebraMemory.clearAnswers();
            verificationMode = false;
        }
        applicationBeforeHome = null;
        application = mode;
        screen = CnCwScreen.forApplication(mode);
        selectedIndex = 0;
        navigation.clear();
        shiftArmed = false;
        committedCalculationState = CnCwCalculationState.editing();
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        errorCursor = 0;
        tokens.clear();
        cursor = 0;
        activeCommandId = "";
        spreadsheetGrid = false;
        applicationLanding = mode != ApplicationMode.CALCULATE
                && mode != ApplicationMode.COMPLEX;
        status = mode.chineseName();
    }

    private void openPopup(CnCwScreen target) {
        if (screen == target) return;
        navigation.push(new Navigation(screen, selectedIndex));
        screen = target;
        selectedIndex = 0;
        shiftArmed = false;
        status = titleFor(target);
    }

    /** Switches menu families without leaving the previous menu on BACK's stack. */
    private void switchPopup(CnCwScreen target) {
        if (screen == target) return;
        Navigation base = navigation.peekLast();
        navigation.clear();
        if (base != null) navigation.push(base);
        else navigation.push(new Navigation(application == null
                ? CnCwScreen.HOME : CnCwScreen.forApplication(application), 0));
        screen = target;
        selectedIndex = 0;
        shiftArmed = false;
        status = titleFor(target);
    }

    private void back() {
        if (navigation.isEmpty()) {
            closeAllPopups();
            return;
        }
        Navigation previous = navigation.pop();
        screen = previous.screen;
        selectedIndex = previous.selectedIndex;
        status = titleFor(screen);
    }

    private void closeAllPopups() {
        navigation.clear();
        screen = application == null ? CnCwScreen.HOME : CnCwScreen.forApplication(application);
        selectedIndex = 0;
        shiftArmed = false;
        status = application == null ? "HOME" : applicationStatus();
    }

    private void activateMenuItem() {
        List<CnCwCommand> items = currentMenuItems();
        if (items.isEmpty()) return;
        int index = Math.min(selectedIndex, items.size() - 1);
        switch (screen) {
            case SETTINGS -> activateSettingsRoot(index);
            case SETTINGS_INPUT_OUTPUT -> activateCalculationSetting(index);
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> activateInputOutputOption(index);
            case SETTINGS_ANGLE_OPTIONS -> activateAngleOption(index);
            case SETTINGS_FORMAT -> activateDisplayFormat(index);
            case SETTINGS_FIX_DIGITS -> activateDisplayDigits(false, index);
            case SETTINGS_SCI_DIGITS -> activateDisplayDigits(true, index);
            case SETTINGS_DISPLAY -> activateSystemSetting(index);
            case RESET_CONFIRM -> activateResetConfirm(index);
            case CATALOG -> {
                if (application == ApplicationMode.COMPLEX && index == 9) {
                    openPopup(CnCwScreen.CATALOG_COMPLEX);
                    break;
                }
                openPopup(switch (index) {
                    case 0 -> CnCwScreen.CATALOG_FUNCTIONS;
                    case 1 -> CnCwScreen.CATALOG_PROBABILITY;
                    case 2 -> CnCwScreen.CATALOG_NUMERIC;
                    case 3 -> CnCwScreen.CATALOG_ANGLE;
                    case 4 -> CnCwScreen.CATALOG_TRIG;
                    case 5 -> CnCwScreen.CATALOG_ENGINEERING;
                    case 6 -> CnCwScreen.CATALOG_CONSTANTS;
                    case 7 -> CnCwScreen.CATALOG_CONVERSIONS;
                    default -> CnCwScreen.CATALOG_RELATIONS;
                });
            }
            case CATALOG_FUNCTIONS -> {
                if (index == 4 && !manualSimplification) {
                    status = "先在工具中将化简设为手动";
                } else {
                    insertFromMenu(functionToken(index));
                }
            }
            case CATALOG_NUMERIC -> insertFromMenu(numericToken(index));
            case CATALOG_ANGLE -> insertFromMenu(angleToken(index));
            case CATALOG_TRIG -> insertFromMenu(trigToken(index));
            case CATALOG_ENGINEERING -> insertFromMenu(engineeringToken(index));
            case CATALOG_CONSTANTS -> {
                catalogCategoryIndex = index;
                openPopup(CnCwScreen.CATALOG_CONSTANT_ITEMS);
            }
            case CATALOG_CONSTANT_ITEMS -> insertFromMenu(constantToken(index));
            case CATALOG_CONVERSIONS -> {
                conversionCategoryIndex = index;
                openPopup(CnCwScreen.CATALOG_CONVERSION_ITEMS);
            }
            case CATALOG_CONVERSION_ITEMS -> insertFromMenu(conversionToken(index));
            case CATALOG_PROBABILITY -> insertFromMenu(probabilityToken(index));
            case CATALOG_COMPLEX -> insertFromMenu(complexToken(index));
            case CATALOG_RELATIONS -> insertFromMenu(relationToken(index));
            case TOOLS -> activateTool(index);
            case TOOLS_CONVERSION -> insertFromMenu(conversionToken(index));
            case VARIABLES -> insertFromMenu(variableToken(index));
            case FUNCTIONS -> activateFunctionItem(index);
            case FORMAT -> activateFormat(index);
            default -> back();
        }
    }

    private void activateSettingsRoot(int index) {
        if (index == 0) openPopup(CnCwScreen.SETTINGS_INPUT_OUTPUT);
        else if (index == 1) openPopup(CnCwScreen.SETTINGS_DISPLAY);
        else openPopup(CnCwScreen.RESET_CONFIRM);
    }

    private void activateResetConfirm(int index) {
        if (index != 0) {
            back();
            return;
        }
        settings = CnCwSettings.defaults();
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        ans = 0.0;
        hasAns = false;
        ansProcessDisplay = "";
        resultProcessDisplay = "";
        exactAns = null;
        clearFunctionDefinitions();
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        spreadsheet.clearAll();
        linearAlgebraMemory.clear();
        tokens.clear();
        cursor = 0;
        result = "";
        resultShown = false;
        navigation.clear();
        screen = CnCwScreen.HOME;
        application = null;
        selectedIndex = 0;
        status = "已复位设置和数据";
    }

    private void activateCalculationSetting(int index) {
        if (index == 2) {
            openPopup(CnCwScreen.SETTINGS_FORMAT);
            return;
        }
        if (index == 0) {
            openPopup(CnCwScreen.SETTINGS_INPUT_OUTPUT_OPTIONS);
            return;
        }
        if (index == 1) {
            openPopup(CnCwScreen.SETTINGS_ANGLE_OPTIONS);
            return;
        }
        settings = switch (index) {
            case 3 -> settings.toggleEngineeringSymbols();
            case 4 -> settings.cycleFractionMode();
            case 5 -> settings.cycleComplexMode();
            case 6 -> settings.cycleDecimalMark();
            default -> settings.toggleDigitSeparator();
        };
        status = "已更新 · " + settingValue(index);
    }

    private void activateInputOutputOption(int index) {
        CnCwSettings.InputOutput previous = settings.inputOutput();
        CnCwSettings.InputOutput[] values = CnCwSettings.InputOutput.values();
        settings = settings.withInputOutput(values[Math.max(0, Math.min(index, values.length - 1))]);
        if (settings.inputOutput() != previous) {
            history.clear();
            historyIndex = 0;
            resetStatementSequence();
            boolean previousMath = previous == CnCwSettings.InputOutput.MATH_MATH
                    || previous == CnCwSettings.InputOutput.MATH_DECIMAL;
            boolean currentMath = settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH
                    || settings.inputOutput() == CnCwSettings.InputOutput.MATH_DECIMAL;
            if (previousMath != currentMath) clearFunctionDefinitions();
        }
        back();
        status = "输入/输出 · " + settings.inputOutput().name();
    }

    private void activateAngleOption(int index) {
        AngleUnit[] values = AngleUnit.values();
        settings = settings.withAngleUnit(values[Math.max(0, Math.min(index, values.length - 1))]);
        back();
        status = "角度单位 · " + settings.angleUnit().name();
    }

    private void clearFunctionDefinitions() {
        functionFSource = "";
        functionGSource = "";
        pendingFunctionDefinition = "";
        functionF = null;
        functionG = null;
    }

    private void activateDisplayFormat(int index) {
        int digits = settings.displayDigits();
        if (index == 2) {
            openPopup(CnCwScreen.SETTINGS_FIX_DIGITS);
            return;
        }
        if (index == 3) {
            openPopup(CnCwScreen.SETTINGS_SCI_DIGITS);
            return;
        }
        settings = settings.withDisplay(index == 0
                ? CnCwSettings.DisplayMode.NORM_1 : CnCwSettings.DisplayMode.NORM_2, digits);
        status = "显示格式 · " + settingValue(2);
        back();
    }

    private void activateDisplayDigits(boolean scientific, int index) {
        int digits = scientific ? index + 1 : index;
        settings = settings.withDisplay(scientific ? CnCwSettings.DisplayMode.SCI
                : CnCwSettings.DisplayMode.FIX, digits);
        back();
        status = "显示格式 · " + settingValue(2);
    }

    private void activateSystemSetting(int index) {
        if (index == 3) {
            openPopup(CnCwScreen.CALCULATOR_ID);
            return;
        }
        if (index == 2) settings = settings.cycleMultiLineFont();
        status = switch (index) {
            case 0 -> "对比度由手机显示系统管理";
            case 1 -> "语言 · 中文";
            case 2 -> "多行字体 · " + settings.multiLineFont();
            default -> model.displayName() + " · clean-room core";
        };
    }

    private void activateTool(int index) {
        switch (index) {
            case 0 -> undo();
            case 1 -> {
                manualSimplification = !manualSimplification;
                closeAllPopups();
                status = "化简 · " + (manualSimplification ? "手动" : "自动");
            }
            case 2 -> {
                if (!supportsVerification()) return;
                verificationMode = !verificationMode;
                history.clear();
                historyIndex = 0;
                resetStatementSequence();
                closeAllPopups();
                status = verificationMode
                        ? "运算验证开 · 输入关系式后按 EXE" : "运算验证关";
            }
            default -> { }
        }
    }

    private void activateFunctionItem(int index) {
        if (index >= 2) {
            closeAllPopups();
            clearExpression();
            pendingFunctionDefinition = index == 2 ? "f" : "g";
            status = "定义 " + pendingFunctionDefinition + "(x)";
            return;
        }
        insertFromMenu(index == 0
                ? new Token("f(", "f(", false)
                : new Token("g(", "g(", false));
    }

    private void activateFormat(int index) {
        CnCwCalculationState valueState = formatSourceState();
        if (valueState.scalarValue() == null) {
            status = "尚无可转换的结果";
            return;
        }
        List<CnCwCommand> commands = currentMenuItems();
        if (commands.isEmpty()) return;
        String id = commands.get(Math.max(0, Math.min(index, commands.size() - 1))).id();
        if (!formatConverted) originalResult = result;
        double value = valueState.scalarValue();
        ExactValue exact = valueState.exactValue();
        ComplexValue complex = valueState.complexValue();
        engineeringMode = false;
        result = switch (id) {
            case "standard" -> complex == null ? formatResult(value, exact, expression(), true)
                    : formatComplex(complex);
            case "decimal" -> complex == null ? formatNumber(value) : formatComplex(complex);
            case "factor" -> primeFactors(value);
            case "improper" -> formatFraction(value, exact).improperString();
            case "mixed" -> formatFraction(value, exact).mixedString();
            case "dms" -> ManualFunctions.fromDecimalDegrees(value).display();
            case "rectangular" -> formatComplex(complex, false);
            case "polar" -> formatComplex(complex, true);
            case "engineering" -> {
                engineeringExponent = value == 0.0 ? 0
                        : (int) Math.floor(Math.log10(Math.abs(value)) / 3.0) * 3;
                engineeringMode = true;
                yield engineeringAtExponent(value, engineeringExponent);
            }
            default -> formatNumber(value);
        };
        if ("Math ERROR".equals(result)) {
            closeAllPopups();
            commitCalculationError(CalculationError.MATH, 0);
            return;
        }
        formatConverted = true;
        resultShown = true;
        committedCalculationState = valueState.withDisplay(result);
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
        closeAllPopups();
    }

    private void restoreOriginalFormat() {
        CnCwCalculationState valueState = formatSourceState();
        engineeringMode = false;
        formatConverted = false;
        if (!originalResult.isEmpty()) result = originalResult;
        resultShown = !result.isEmpty();
        if (resultShown) committedCalculationState = valueState.withDisplay(result);
        status = applicationStatus();
    }

    private void refreshEngineeringResult() {
        CnCwCalculationState valueState = formatSourceState();
        if (valueState.scalarValue() == null) return;
        engineeringExponent = Math.max(-99, Math.min(99, engineeringExponent));
        engineeringExponent -= Math.floorMod(engineeringExponent, 3);
        result = engineeringAtExponent(valueState.scalarValue(), engineeringExponent);
        resultShown = true;
        committedCalculationState = valueState.withDisplay(result);
        status = "ENG · 10^" + engineeringExponent;
    }

    private String engineeringAtExponent(double value, int exponent) {
        double mantissa = value / Math.pow(10.0, exponent);
        String text = trimDouble(mantissa);
        if (settings.digitSeparator()) text = separateDigits(text);
        if (settings.decimalMark() == CnCwSettings.DecimalMark.COMMA) {
            text = text.replace('.', ',');
        }
        return text + "×10^" + exponent;
    }

    private CnCwCalculationState formatSourceState() {
        return resultShown ? calculationStateSnapshot() : currentAnswerCalculationState(result);
    }

    private static Rational formatFraction(double value, ExactValue exact) {
        Rational fraction = exact == null ? null : exact.rational();
        return fraction == null ? Rational.approximate(value, 1_000_000L, 1e-12) : fraction;
    }

    private boolean supportsVerification() {
        return application == ApplicationMode.CALCULATE || application == ApplicationMode.COMPLEX;
    }

    private void insertFromMenu(Token token) {
        if (token == null) return;
        closeAllPopups();
        applicationLanding = false;
        semanticCursorOverride = null;
        rememberUndo();
        if (prepareContinuousVerification(token)) return;
        tokens.add(cursor, token);
        cursor++;
        result = "";
        resultShown = false;
    }

    private Token tokenFor(CnCwKey key, boolean shifted) {
        if (shifted) {
            return switch (key) {
                case SIN -> token("sin⁻¹(", "asin(");
                case COS -> token("cos⁻¹(", "acos(");
                case TAN -> token("tan⁻¹(", "atan(");
                case LOG -> token("ln(", "ln(");
                case LN -> token("e^(", "e^(");
                case SQRT -> token("³√(", "root(3,");
                case POWER -> token("⁻¹", "^(-1)");
                case SQUARE -> token("log(", "log(");
                case FRACTION -> token("a b/c(", "mixed(");
                case FACTORIAL -> token("nPr", "nPr", true);
                case PERCENT -> token("nCr", "nCr", true);
                case NPR -> token("nCr", "nCr", true);
                case ADD -> token("nPr", "nPr", true);
                case SUBTRACT -> token("nCr", "nCr", true);
                case ANS -> token("Ans");
                // On the physical CW layout π is the SHIFT function of 7.
                // Keep PI as a compatibility input, but do not make it the
                // source of the SHIFT+7 mapping (the two key intents are
                // intentionally independent).
                case DIGIT_7 -> token("π", "pi");
                case DIGIT_8 -> token("∠", "∠");
                case DIGIT_9 -> token("i", "i");
                case DIGIT_0 -> token("x");
                case DIGIT_1 -> token("D");
                case DIGIT_2 -> token("E");
                case DIGIT_3 -> token("F");
                case DIGIT_4 -> token("A");
                case DIGIT_5 -> token("B");
                case DIGIT_6 -> token("C");
                case DOT -> token("y");
                case EXP -> token("z");
                case NEGATE -> token("e", "e");
                case OPEN_PAREN, OPEN -> token("=", "=", true);
                case CLOSE_PAREN, CLOSE -> token(",", ",");
                case VAR_X -> token("DMS(", "dms(");
                case MULTIPLY -> token("∫(", "integral(");
                case DIVIDE -> token("d/dx(", "diff(");
                case PI -> token("π", "pi");
                case E -> token("e", "e");
                default -> null;
            };
        }
        return switch (key) {
            case DIGIT_0 -> token("0"); case DIGIT_1 -> token("1");
            case DIGIT_2 -> token("2"); case DIGIT_3 -> token("3");
            case DIGIT_4 -> token("4"); case DIGIT_5 -> token("5");
            case DIGIT_6 -> token("6"); case DIGIT_7 -> token("7");
            case DIGIT_8 -> token("8"); case DIGIT_9 -> token("9");
            case DOT -> token("."); case COMMA -> token(",");
            case ADD, PLUS -> token("+", "+", true);
            case SUBTRACT, MINUS -> token("−", "-", true);
            case MULTIPLY -> token("×", "*", true);
            case DIVIDE -> token("÷", "/", true);
            case FRACTION -> token("a/b", "/", true);
            case POWER -> token("^", "^", true);
            case OPEN_PAREN, OPEN -> token("(");
            case CLOSE_PAREN, CLOSE -> token(")");
            case SIN -> token("sin("); case COS -> token("cos(");
            case TAN -> token("tan("); case LOG -> token("log(");
            case LN -> token("ln("); case EXP -> token("×10^(", "*10^(");
            case SQRT -> token("√(", "sqrt(");
            case ROOT -> token("√[ ](", "root(");
            case ABS -> token("Abs(", "abs(");
            case RECIPROCAL -> token("⁻¹", "^(-1)");
            case SQUARE -> token("²", "^2");
            case CUBE -> token("³", "^3");
            case FACTORIAL -> token("!");
            case PERCENT -> token("%", "%");
            case NPR -> token("nPr", "nPr", true);
            case NCR -> token("nCr", "nCr", true);
            case NEGATE -> token("(−)", "-");
            case PI -> token("π", "pi"); case E -> token("e");
            case ANS -> token("Ans"); case RAN -> token("Ran#", "ran()");
            case EQUALS -> token("=", "=", true);
            case VAR_A -> token("A"); case VAR_B -> token("B");
            case VAR_C -> token("C"); case VAR_D -> token("D");
            case VAR_E -> token("E"); case VAR_F -> token("F");
            case VAR_X -> token("x"); case VAR_Y -> token("y");
            case VAR_Z -> token("z");
            default -> null;
        };
    }

    private Token functionToken(int index) {
        return switch (index) {
            case 0 -> token("d/dx(", "diff(");
            case 1 -> token("∫(", "integral(");
            case 2 -> token("Σ(", "sum(");
            case 3 -> token("÷R", "÷R", true);
            case 4 -> token("Simp(", "simp(");
            case 5 -> token("logₐ(", "log(");
            case 6 -> token("log(", "log(");
            default -> token("ln(", "ln(");
        };
    }

    private Token numericToken(int index) {
        return index == 0 ? token("Abs(", "abs(") : token("Rnd(", "rnd(");
    }

    private Token angleToken(int index) {
        return switch (index) {
            case 0 -> token("°(", "deg(");
            case 1 -> token("ʳ(", "rad(");
            case 2 -> token("ᵍ(", "grad(");
            case 3 -> token("Pol(", "pol(");
            case 4 -> token("Rec(", "rec(");
            default -> token("DMS(", "dms(");
        };
    }

    private Token trigToken(int index) {
        return switch (index) {
            case 0 -> token("sinh("); case 1 -> token("cosh(");
            case 2 -> token("tanh("); case 3 -> token("sinh⁻¹(", "asinh(");
            case 4 -> token("cosh⁻¹(", "acosh(");
            case 5 -> token("tanh⁻¹(", "atanh(");
            case 6 -> token("sin("); case 7 -> token("cos(");
            case 8 -> token("tan("); case 9 -> token("sin⁻¹(", "asin(");
            case 10 -> token("cos⁻¹(", "acos(");
            default -> token("tan⁻¹(", "atan(");
        };
    }

    private Token engineeringToken(int index) {
        String[] symbols = {"m", "μ", "n", "p", "f", "k", "M", "G", "T", "P", "E"};
        double[] factors = {1e-3, 1e-6, 1e-9, 1e-12, 1e-15,
                1e3, 1e6, 1e9, 1e12, 1e15, 1e18};
        int safe = Math.max(0, Math.min(index, symbols.length - 1));
        return token(symbols[safe], "*" + Double.toString(factors[safe]), true);
    }

    private Token constantToken(int index) {
        List<List<ScientificConstants.Constant>> groups =
                new ArrayList<>(ScientificConstants.categories().values());
        int group = Math.max(0, Math.min(catalogCategoryIndex, groups.size() - 1));
        List<ScientificConstants.Constant> values = groups.get(group);
        ScientificConstants.Constant value = values.get(Math.max(0, Math.min(index, values.size() - 1)));
        return token(value.symbol(), Double.toString(value.value()));
    }

    private Token complexToken(int index) {
        return switch (index) {
            case 0 -> token("Conjg(", "conj(");
            case 1 -> token("Arg(", "arg(");
            case 2 -> token("Re(", "re(");
            default -> token("Im(", "im(");
        };
    }

    private Token probabilityToken(int index) {
        return switch (index) {
            case 0 -> token("%", "%");
            case 1 -> token("!");
            case 2 -> token("nPr", "nPr", true);
            case 3 -> token("nCr", "nCr", true);
            case 4 -> token("Ran#", "ran()");
            // ScalarExpressionEngine's clean-room spelling is ranint(...).
            // The display keeps the manual's RanInt# label.
            default -> token("RanInt#(", "ranint(");
        };
    }

    private Token relationToken(int index) {
        return switch (index) {
            case 0 -> token("=", "=", true);
            case 1 -> token("≠", "!=", true);
            case 2 -> token("<", "<", true);
            case 3 -> token("≤", "<=", true);
            case 4 -> token(">", ">", true);
            case 5 -> token("≥", ">=", true);
            case 6 -> token("→", "->", true);
            case 7 -> token(":", ":", true);
            case 8 -> token("Ans");
            case 9 -> token("π", "pi");
            case 10 -> token("e");
            case 11 -> token("√(", "sqrt(");
            case 12 -> token("√[ ](", "root(");
            case 13 -> token("⁻¹", "^(-1)");
            case 14 -> token("²", "^2");
            case 15 -> token("^", "^");
            case 16 -> token("(−)", "-");
            case 17 -> token(",");
            case 18 -> token("(");
            case 19 -> token(")");
            default -> {
                int variable = Math.max(0, Math.min(index - 20, VARIABLE_NAMES.size() - 1));
                String name = VARIABLE_NAMES.get(variable);
                yield token("→" + name, "->" + name, true);
            }
        };
    }

    private Token variableToken(int index) {
        if (index < VARIABLE_NAMES.size()) return token(VARIABLE_NAMES.get(index));
        return token("Ans");
    }

    private Token conversionToken(int index) {
        List<List<String>> groups = new ArrayList<>(UnitConverter.categories().values());
        int category = Math.max(0, Math.min(conversionCategoryIndex, groups.size() - 1));
        List<String> categoryCommands = groups.get(category);
        int safe = Math.max(0, Math.min(index, categoryCommands.size() - 1));
        String command = categoryCommands.get(safe);
        int global = new ArrayList<>(UnitConverter.catalog().keySet()).indexOf(command);
        return token(command + "(", "conv" + global + "(");
    }

    private List<CnCwCommand> currentMenuItems() {
        return switch (screen) {
            case CALCULATOR_ID -> com.codex.fx991.core.Compat.list(
                    command("id", model.displayName(),
                            model.name() + " · " + sizeLabel(model.applications().size())));
            case SETTINGS -> com.codex.fx991.core.Compat.list(command("calc", "计算设置", "输入、角度与显示"),
                    command("system", "系统设置", "语言、对比度与字体"),
                    command("reset", "复位", "设置与数据"));
            case RESET_CONFIRM -> com.codex.fx991.core.Compat.list(
                    command("yes", "确认复位", "清除设置、变量和数据"),
                    command("no", "取消", "返回设置菜单"));
            case SETTINGS_INPUT_OUTPUT -> com.codex.fx991.core.Compat.list(
                    command("io", "输入/输出", settingValue(0)),
                    command("angle", "角度单位", settingValue(1)),
                    command("display", "显示格式", settingValue(2)),
                    command("engineering", "工程符号", settingValue(3)),
                    command("fraction", "分数结果", settingValue(4)),
                    command("complex", "复数结果", settingValue(5)),
                    command("decimal", "小数点显示", settingValue(6)),
                     command("separator", "数字分隔符", settingValue(7)));
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> com.codex.fx991.core.Compat.list(
                    command("math-math", radio(settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH,
                            "数学输入/数学输出"), "自然书写与精确结果"),
                    command("math-decimal", radio(settings.inputOutput() == CnCwSettings.InputOutput.MATH_DECIMAL,
                            "数学输入/小数输出"), "自然书写与小数结果"),
                    command("linear-linear", radio(settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_LINEAR,
                            "线性输入/线性输出"), "单行输入与标准结果"),
                    command("linear-decimal", radio(settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_DECIMAL,
                            "线性输入/小数输出"), "单行输入与小数结果"));
            case SETTINGS_ANGLE_OPTIONS -> com.codex.fx991.core.Compat.list(
                    command("degree", radio(settings.angleUnit() == AngleUnit.DEG, "度(D)"), "360°"),
                    command("radian", radio(settings.angleUnit() == AngleUnit.RAD, "弧度(R)"), "2π"),
                    command("grad", radio(settings.angleUnit() == AngleUnit.GRAD, "百分度(G)"), "400g"));
            case SETTINGS_FORMAT -> com.codex.fx991.core.Compat.list(
                    command("norm1", "Norm 1", "常规显示"),
                    command("norm2", "Norm 2", "常规显示"),
                    command("fix", "Fix", "固定小数位 0–9"),
                    command("sci", "Sci", "有效数字 1–10"));
            case SETTINGS_FIX_DIGITS -> digitCommands(false);
            case SETTINGS_SCI_DIGITS -> digitCommands(true);
            case SETTINGS_DISPLAY -> com.codex.fx991.core.Compat.list(
                    command("contrast", "对比度", "跟随手机显示"),
                    command("language", "语言", "中文"),
                    command("font", "多行字体", settings.multiLineFont().name()),
                    command("about", "关于", model.displayName()));
            case CATALOG -> catalogRootCommands();
            case CATALOG_FUNCTIONS -> com.codex.fx991.core.Compat.list(
                    command("diff", "d/dx(", "导数"), command("integral", "∫(", "积分"),
                    command("sum", "Σ(", "求和"), command("remainder", "÷R", "商和余数"),
                    command("simp", "Simp(", "分数化简"), command("logbase", "logₐ(", "指定底数"),
                    command("log", "log(", "常用对数"), command("ln", "ln(", "自然对数"));
            case CATALOG_NUMERIC -> com.codex.fx991.core.Compat.list(
                    command("abs", "Abs(", "绝对值"), command("rnd", "Rnd(", "按显示格式四舍五入"));
            case CATALOG_ANGLE -> com.codex.fx991.core.Compat.list(
                    command("degree", "度(°)", "指定度"), command("radian", "弧度(r)", "指定弧度"),
                    command("gradian", "百分度(g)", "指定百分度"),
                    command("pol", "Pol(", "直角坐标转极坐标"),
                    command("rec", "Rec(", "极坐标转直角坐标"),
                    command("dms", "DMS(", "度、分、秒"));
            case CATALOG_TRIG -> com.codex.fx991.core.Compat.list(
                    command("sinh", "sinh(", "双曲正弦"), command("cosh", "cosh(", "双曲余弦"),
                    command("tanh", "tanh(", "双曲正切"), command("asinh", "sinh⁻¹(", "反双曲正弦"),
                    command("acosh", "cosh⁻¹(", "反双曲余弦"), command("atanh", "tanh⁻¹(", "反双曲正切"),
                    command("sin", "sin(", "正弦"), command("cos", "cos(", "余弦"),
                    command("tan", "tan(", "正切"), command("asin", "sin⁻¹(", "反正弦"),
                    command("acos", "cos⁻¹(", "反余弦"), command("atan", "tan⁻¹(", "反正切"));
            case CATALOG_ENGINEERING -> engineeringCommands();
            case CATALOG_CONSTANTS -> constantCategoryCommands();
            case CATALOG_CONSTANT_ITEMS -> constantCommands();
            case CATALOG_CONVERSIONS -> conversionCategoryCommands();
            case CATALOG_CONVERSION_ITEMS -> conversionCommands();
            case CATALOG_COMPLEX -> com.codex.fx991.core.Compat.list(
                    command("conj", "Conjg(", "共轭复数"),
                    command("arg", "Arg(", "辐角"),
                    command("re", "Re(", "实部"),
                    command("im", "Im(", "虚部"));
            case CATALOG_PROBABILITY -> com.codex.fx991.core.Compat.list(
                    command("percent", "%", "百分数"), command("factorial", "!", "阶乘"),
                    command("npr", "nPr", "排列"), command("ncr", "nCr", "组合"),
                    command("random", "Ran#", "随机数"),
                    command("randomInt", "RanInt#(", "随机整数"));
            case CATALOG_RELATIONS -> com.codex.fx991.core.Compat.list(
                    command("eq", "=", "等于"), command("ne", "≠", "不等于"),
                    command("lt", "<", "小于"), command("le", "≤", "小于等于"),
                    command("gt", ">", "大于"), command("ge", "≥", "大于等于"),
                    command("store", "→", "赋值到变量"), command("next", ":", "下一语句"),
                    command("ans", "Ans", "上次结果"), command("pi", "π", "圆周率"),
                    command("e", "e", "自然常数"), command("sqrt", "√(", "平方根"),
                    command("root", "ⁿ√(", "n 次根"), command("inverse", "x⁻¹", "倒数"),
                    command("square", "x²", "平方"), command("power", "xʸ", "乘方"),
                    command("negative", "(−)", "负号"), command("comma", ",", "分隔符"),
                    command("open", "(", "左括号"), command("close", ")", "右括号"),
                    command("store-a", "→A", "赋值"), command("store-b", "→B", "赋值"),
                    command("store-c", "→C", "赋值"), command("store-d", "→D", "赋值"),
                    command("store-e", "→E", "赋值"), command("store-f", "→F", "赋值"),
                    command("store-x", "→x", "赋值"), command("store-y", "→y", "赋值"),
                    command("store-z", "→z", "赋值"));
            case TOOLS -> supportsVerification()
                    ? com.codex.fx991.core.Compat.list(command("undo", "撤消", "恢复上次编辑"),
                    command("simplify", "化简", manualSimplification ? "手动" : "自动"),
                    command("verify", "运算验证", verificationMode ? "开" : "关"))
                    : com.codex.fx991.core.Compat.list(command("undo", "撤消", "恢复上次编辑"),
                    command("simplify", "化简", manualSimplification ? "手动" : "自动"));
            case TOOLS_CONVERSION -> com.codex.fx991.core.Compat.list(
                    command("in-cm", "in → cm", "×2.54"), command("cm-in", "cm → in", "÷2.54"),
                    command("lb-kg", "lb → kg", "×0.45359237"), command("kg-lb", "kg → lb", "÷0.45359237"),
                    command("ft-m", "ft → m", "×0.3048"), command("m-ft", "m → ft", "÷0.3048"));
            case VARIABLES -> com.codex.fx991.core.Compat.list(
                    variableCommand("A"), variableCommand("B"), variableCommand("C"),
                    variableCommand("D"), variableCommand("E"), variableCommand("F"),
                    variableCommand("x"), variableCommand("y"), variableCommand("z"),
                    command("Ans", "Ans", hasAns ? formatNumber(ans) : "未定义"));
            case FUNCTIONS -> com.codex.fx991.core.Compat.list(
                    command("call-f", "f(", "调用 f(x)"),
                    command("call-g", "g(", "调用 g(x)"),
                    command("define-f", "定义 f(x)", functionFSource.isEmpty() ? "未定义" : functionFSource),
                    command("define-g", "定义 g(x)", functionGSource.isEmpty() ? "未定义" : functionGSource));
            case FORMAT -> formatCommands();
            default -> com.codex.fx991.core.Compat.list();
        };
    }

    private List<CnCwCommand> catalogRootCommands() {
        List<CnCwCommand> items = new ArrayList<>(com.codex.fx991.core.Compat.list(
                command("analysis", "函数与分析", "导数、积分、求和、对数"),
                command("probability", "概率", "%、阶乘、排列组合、随机数"),
                command("numeric", "数值计算", "绝对值与四舍五入"),
                command("angle", "角度/坐标/六十进制", "角度单位、Pol、Rec、DMS"),
                command("trig", "双曲/反双曲/三角", "12 个函数"),
                command("engineering", "工程符号", "m、μ、n、p、f、k…"),
                command("constants", "科学常数", "47 个 CODATA 2018 常数"),
                command("conversion", "单位换算", "40 个换算命令"),
                command("other", "其他", "关系、多语句与存储")));
        if (application == ApplicationMode.COMPLEX) {
            items.add(command("complex", "复数", "Conjg、Arg、Re、Im"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static List<CnCwCommand> engineeringCommands() {
        String[] symbols = {"m", "μ", "n", "p", "f", "k", "M", "G", "T", "P", "E"};
        String[] names = {"毫", "微", "纳", "皮", "飞", "千", "兆", "吉", "太", "拍", "艾"};
        List<CnCwCommand> items = new ArrayList<>(symbols.length);
        for (int index = 0; index < symbols.length; index++) {
            items.add(command("eng-" + index, names[index] + "(" + symbols[index] + ")",
                    "工程符号"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> digitCommands(boolean scientific) {
        int start = scientific ? 1 : 0;
        int end = scientific ? 10 : 9;
        List<CnCwCommand> items = new ArrayList<>();
        for (int digits = start; digits <= end; digits++) {
            boolean selected = settings.displayMode() == (scientific
                    ? CnCwSettings.DisplayMode.SCI : CnCwSettings.DisplayMode.FIX)
                    && settings.displayDigits() == digits;
            items.add(command("digits-" + digits, radio(selected, Integer.toString(digits)),
                    scientific ? "有效数字" : "小数位"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static String radio(boolean selected, String label) {
        return (selected ? "● " : "○ ") + label;
    }

    private List<CnCwCommand> formatCommands() {
        CnCwCalculationState valueState = formatSourceState();
        if (valueState.scalarValue() == null || !valueState.isResult()
                || valueState.hasApplicationResult()) return com.codex.fx991.core.Compat.list();
        double value = valueState.scalarValue();
        ExactValue exact = valueState.exactValue();
        ComplexValue complex = valueState.complexValue();
        List<CnCwCommand> items = new ArrayList<>();
        items.add(command("standard", "标准", "分数、π、√ 格式"));
        items.add(command("decimal", "小数", "小数结果"));
        if (complex != null) {
            items.add(command("rectangular", "代数形式", "a+bi"));
            items.add(command("polar", "极坐标形式", "r∠θ"));
            if (complex.imaginary() != 0.0) return com.codex.fx991.core.Compat.copyList(items);
        }
        if (value > 0.0 && value == Math.rint(value) && value <= 9_999_999_999L) {
            items.add(command("factor", "质因数分解", "最多 10 位正整数"));
        }
        Rational fraction = exact == null ? null : exact.rational();
        if (fraction != null) {
            items.add(command("improper", "假分数", "a/b"));
            items.add(command("mixed", "带分数", "a b/c"));
        }
        if (Double.isFinite(value)) {
            items.add(command("engineering", "工程记数法", "用 ←/→ 移动小数点"));
        }
        if (Math.abs(value) <= 9_999_999.999999) {
            items.add(command("dms", "六十进制", "度、分、秒"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static List<CnCwCommand> constantCategoryCommands() {
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<ScientificConstants.Constant>> entry
                : ScientificConstants.categories().entrySet()) {
            items.add(command("constant-group-" + index++, entry.getKey(),
                    entry.getValue().size() + " 个常数"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> constantCommands() {
        List<List<ScientificConstants.Constant>> groups =
                new ArrayList<>(ScientificConstants.categories().values());
        int group = Math.max(0, Math.min(catalogCategoryIndex, groups.size() - 1));
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (ScientificConstants.Constant value : groups.get(group)) {
            items.add(command("constant-" + index++, value.symbol(),
                    value.name() + (value.unit().isEmpty() ? "" : " · " + value.unit())));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static List<CnCwCommand> conversionCategoryCommands() {
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> category : UnitConverter.categories().entrySet()) {
            items.add(command("conversion-group-" + index++, category.getKey(),
                    category.getValue().size() + " 个换算"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> conversionCommands() {
        List<List<String>> groups = new ArrayList<>(UnitConverter.categories().values());
        int category = Math.max(0, Math.min(conversionCategoryIndex, groups.size() - 1));
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (String name : groups.get(category)) {
            items.add(command("conversion-" + index++, name, "单位换算"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> homeItems() {
        List<CnCwCommand> items = new ArrayList<>();
        for (ApplicationMode mode : model.applications()) {
            items.add(command(mode.name(), mode.chineseName(), applicationDescription(mode)));
        }
        return items;
    }

    private List<CnCwCommand> modeCommands(ApplicationMode mode) {
        if (mode == null) return com.codex.fx991.core.Compat.list();
        return switch (mode) {
            case CALCULATE -> com.codex.fx991.core.Compat.list(command("calculate", "计算", "输入表达式"));
            case STATISTICS -> com.codex.fx991.core.Compat.list(
                    command("one", "单变量", "x 数据"),
                    command("two", "双变量", "x / y 数据"),
                    command("reg-linear", "线性回归", "y=a·x+b"),
                    command("reg-quadratic", "二次回归", "y=a·x²+b·x+c"),
                    command("reg-logarithmic", "对数回归", "y=a+b·ln(x)"),
                    command("reg-e-exponential", "e 指数回归", "y=a·e^(b·x)"),
                    command("reg-ab-exponential", "ab^x 回归", "y=a·b^x"),
                    command("reg-power", "幂回归", "y=a·x^b"),
                    command("reg-inverse", "逆数回归", "y=a+b/x"),
                    command("one-freq", "单变量（频数）", "x / 频数"),
                    command("two-freq", "双变量（频数）", "x / y / 频数"),
                    command("reg-linear-freq", "线性回归（频数）", "x / y / 频数"),
                    command("reg-quadratic-freq", "二次回归（频数）", "x / y / 频数"),
                    command("reg-logarithmic-freq", "对数回归（频数）", "x / y / 频数"),
                    command("reg-e-exponential-freq", "e 指数回归（频数）", "x / y / 频数"),
                    command("reg-ab-exponential-freq", "ab^x 回归（频数）", "x / y / 频数"),
                    command("reg-power-freq", "幂回归（频数）", "x / y / 频数"),
                    command("reg-inverse-freq", "逆数回归（频数）", "x / y / 频数"));
            case DISTRIBUTION -> com.codex.fx991.core.Compat.list(command("normal", "正态分布", "PDF / CDF / 逆分布"),
                    command("binomial", "二项分布", "概率 / 累计概率"),
                    command("poisson", "泊松分布", "概率 / 累计概率"));
            case SPREADSHEET -> com.codex.fx991.core.Compat.list(command("sheet", "A1:E45", "45 行 × 5 列"),
                    command("fill", "填充", "公式或数值"), command("recalc", "重新计算", "更新公式"));
            case FUNCTION_TABLE -> com.codex.fx991.core.Compat.list(command("fg", "f(x) 与 g(x)", "最多 30 行"),
                    command("single", "单函数", "最多 45 行"));
            case EQUATION -> com.codex.fx991.core.Compat.list(command("simultaneous", "联立方程", "2 至 4 个未知数"),
                    command("polynomial", "高阶方程", "2 至 4 次"),
                    command("solve", "求解方程", "Newton SOLVE"));
            case INEQUALITY -> com.codex.fx991.core.Compat.list(command("quadratic", "二次不等式", "四种关系"),
                    command("cubic", "三次不等式", "四种关系"),
                    command("quartic", "四次不等式", "四种关系"));
            case COMPLEX -> com.codex.fx991.core.Compat.list(command("complex", "复数计算", "a+bi / r∠θ"));
            case BASE_N -> com.codex.fx991.core.Compat.list(
                    command("decimal", "十进制", "DEC"),
                    command("hex", "十六进制", "HEX"),
                    command("binary", "二进制", "BIN"),
                    command("octal", "八进制", "OCT"),
                    command("base-convert", "进制转换", "DEC / HEX / BIN / OCT"),
                    command("base-add", "加法", "32 位有符号整数"),
                    command("base-subtract", "减法", "32 位有符号整数"),
                    command("base-multiply", "乘法", "32 位有符号整数"),
                    command("base-divide", "除法", "整数商"),
                    command("base-negate", "取负", "NEG"),
                    command("base-not", "NOT", "按位取反"),
                    command("base-and", "AND", "按位与"),
                    command("base-or", "OR", "按位或"),
                    command("base-xor", "XOR", "按位异或"),
                    command("base-xnor", "XNOR", "按位同或"));
            case MATRIX -> com.codex.fx991.core.Compat.list(
                    command("define", "定义 MatA", "最大 4×4"),
                    command("calculate", "直接矩阵", "一次性输入/结果"),
                    command("mat-b", "定义 MatB", "最大 4×4"),
                    command("mat-c", "定义 MatC", "最大 4×4"),
                    command("mat-d", "定义 MatD", "最大 4×4"),
                    command("matrix-det", "行列式", "det(Mat)"),
                    command("matrix-inverse", "逆矩阵", "Mat⁻¹"),
                    command("matrix-transpose", "转置", "Trn(Mat)"),
                    command("matrix-add", "矩阵加法", "Mat+Mat"),
                    command("matrix-subtract", "矩阵减法", "Mat−Mat"),
                    command("matrix-multiply", "矩阵乘法", "Mat×Mat"),
                    command("matrix-square", "矩阵平方", "Mat²"),
                    command("matrix-cube", "矩阵立方", "Mat³"),
                    command("matrix-identity", "单位矩阵", "Identity(n)"),
                    command("matrix-abs", "元素绝对值", "Abs(Mat)"),
                    command("matrix-ans", "MatAns", "矩阵答案存储器"));
            case VECTOR -> com.codex.fx991.core.Compat.list(
                    command("define", "定义 VctA", "2D/3D"),
                    command("calculate", "直接向量", "一次性输入/结果"),
                    command("vct-b", "定义 VctB", "2D/3D"),
                    command("vct-c", "定义 VctC", "2D/3D"),
                    command("vct-d", "定义 VctD", "2D/3D"),
                    command("vector-magnitude", "向量模", "|Vct|"),
                    command("vector-unit", "单位向量", "Unit(Vct)"),
                    command("vector-add", "向量加法", "Vct+Vct"),
                    command("vector-subtract", "向量减法", "Vct−Vct"),
                    command("vector-dot", "点积", "Vct·Vct"),
                    command("vector-cross", "叉积", "Vct×Vct"),
                    command("vector-angle", "夹角", "Angle(Vct,Vct)"),
                    command("vector-ans", "VctAns", "向量答案存储器"));
            case RATIO -> com.codex.fx991.core.Compat.list(command("a:b=x:d", "A:B=X:D", "求 X"),
                    command("a:b=c:x", "A:B=C:X", "求 X"));
        };
    }

    /**
     * Stage 6 compatibility projection. The legacy fields remain the write-side
     * source of truth in Step 1; this snapshot must therefore be lossless and
     * side-effect free.
     */
    private CnCwCalculationState calculationStateSnapshot() {
        if (!resultShown) return CnCwCalculationState.editing();
        if (committedCalculationState != null
                && committedCalculationState.display().equals(result)) {
            if (errorShown && committedCalculationState.isError()) {
                return committedCalculationState;
            }
            if (!errorShown && committedCalculationState.isResult()) {
                return committedCalculationState;
            }
        }
        if (errorShown) {
            return CnCwCalculationState.error(result, lastError, errorCursor);
        }
        if (applicationResult != null) {
            return CnCwCalculationState.applicationResult(result, applicationResult);
        }
        if (lastExactResult != null) {
            return CnCwCalculationState.exactResult(result, ans, lastExactResult);
        }
        if (hasComplexAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.complexResult(result, complexAns);
        }
        if (hasAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.scalarResult(result, ans);
        }
        return CnCwCalculationState.textResult(result);
    }

    private void publish() {
        List<CnCwCommand> menus = currentMenuItems();
        int itemCount = screen == CnCwScreen.HOME ? model.applications().size()
                : screen.isPopupMenu() ? menus.size()
                : applicationLanding ? modeCommands(application).size() : 1;
        if (itemCount > 0) selectedIndex = Math.min(selectedIndex, itemCount - 1);
        state = new CnCwUiState(model, screen, application, selectedIndex,
                menus, homeItems(), modeCommands(application), expression(), displayText(),
                naturalExpression(), cursor, semanticCursorPath(), semanticSpans(),
                selectionStartIndex(), selectionEndIndex(),
                semanticSelectionPath(selectionAnchor), semanticSelectionPath(selectionFocus),
                result, resultShown ? applicationResult : null,
                workflowSession == null ? null : workflowSession.snapshot(),
                calculationStateSnapshot(),
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
                verificationMode, engineeringMode,
                !statementSequence.isEmpty() && statementSequenceIndex < statementSequence.size(),
                !history.isEmpty() && historyIndex > 0,
                !history.isEmpty() && historyIndex < history.size() - 1,
                applicationLanding,
                resultShown, spreadsheetGrid, spreadsheetRow, spreadsheetColumn,
                spreadsheetGrid ? spreadsheetCellsSnapshot() : com.codex.fx991.core.Compat.list(),
                spreadsheetGrid ? spreadsheet.input(spreadsheetAddress()) : "",
                navigationPath());
    }

    /**
     * Returns the best semantic position for the current legacy token boundary.
     * A Stage 3 override is required at fraction entry/exit boundaries because
     * the same legacy boundary can mean either a nested slot or the root row.
     */
    private CnCwCursorPath semanticCursorPath() {
        if (semanticCursorOverride != null
                && semanticCursorOverride.legacyTokenBoundary() == cursor) {
            return semanticCursorOverride;
        }
        FractionCursor fraction = fractionCursorAt(cursor);
        if (fraction != null) {
            return CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(fraction.templateIndex),
                    fraction.slot, fraction.offset, cursor);
        }
        PowerCursor power = powerCursorAt(cursor);
        if (power != null) {
            return CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(power.templateIndex),
                    power.slot, power.offset, cursor);
        }
        RadicalCursor radical = radicalCursorAt(cursor);
        if (radical != null) {
            return CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(radical.templateIndex),
                    radical.slot, radical.offset, cursor);
        }
        FunctionCursor function = functionCursorAt(cursor);
        if (function != null) {
            return CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(function.templateIndex, function.argumentIndex),
                    CnCwCursorPath.Slot.FUNCTION_ARGUMENT, function.offset, cursor);
        }
        return CnCwCursorPath.rootBoundary(cursor);
    }

    private FractionBounds fractionBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isFractionTemplate(tokens.get(templateIndex))) return null;
        int numeratorStart = fractionNumeratorStart(templateIndex);
        int numeratorEnd = templateIndex;
        int denominatorStart = templateIndex + 1;
        int denominatorEnd = fractionDenominatorEnd(denominatorStart, tokens.size());
        return new FractionBounds(templateIndex, numeratorStart, numeratorEnd,
                denominatorStart, denominatorEnd);
    }

    /** Empty numerators remain a valid editor slot instead of absorbing a binary token. */
    private int fractionNumeratorStart(int templateIndex) {
        if (templateIndex <= 0) return Math.max(0, templateIndex);
        Token previous = tokens.get(templateIndex - 1);
        if (previous.binary) return templateIndex;
        return semanticAtomStart(templateIndex);
    }

    /** Empty denominators stop before the following top-level binary operator. */
    private int fractionDenominatorEnd(int start, int limit) {
        int safe = Math.max(0, Math.min(limit, start));
        if (safe >= limit) return safe;
        if (tokens.get(safe).binary) return safe;
        return naturalExponentEnd(safe, limit, false);
    }

    /** Finds the smallest fraction structure owning the requested insertion boundary. */
    private FractionCursor fractionCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        FractionCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds bounds = fractionBounds(template);
            if (bounds == null) continue;
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (safe >= bounds.numeratorStart && safe <= bounds.numeratorEnd) {
                slot = CnCwCursorPath.Slot.FRACTION_NUMERATOR;
                offset = safe - bounds.numeratorStart;
            } else if (safe >= bounds.denominatorStart && safe <= bounds.denominatorEnd) {
                slot = CnCwCursorPath.Slot.FRACTION_DENOMINATOR;
                offset = safe - bounds.denominatorStart;
            }
            if (slot == null) continue;
            int span = bounds.denominatorEnd - bounds.numeratorStart;
            if (span < bestSpan) {
                bestSpan = span;
                best = new FractionCursor(template, bounds.numeratorStart, bounds.numeratorEnd,
                        bounds.denominatorStart, bounds.denominatorEnd, slot, offset);
            }
        }
        return best;
    }

    private FractionCursor fractionCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.FRACTION_NUMERATOR
                && slot != CnCwCursorPath.Slot.FRACTION_DENOMINATOR) return null;
        int template = path.childPath().get(0);
        FractionBounds bounds = fractionBounds(template);
        if (bounds == null) return null;
        int length = slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? bounds.numeratorEnd - bounds.numeratorStart
                : bounds.denominatorEnd - bounds.denominatorStart;
        int offset = Math.max(0, Math.min(length, path.offset()));
        return new FractionCursor(template, bounds.numeratorStart, bounds.numeratorEnd,
                bounds.denominatorStart, bounds.denominatorEnd, slot, offset);
    }

    private FractionBounds fractionStartingAtBoundary(int boundary) {
        FractionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds value = fractionBounds(template);
            if (value == null || value.numeratorStart != boundary) continue;
            int span = value.denominatorEnd - value.numeratorStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private FractionBounds fractionEndingAtBoundary(int boundary) {
        FractionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds value = fractionBounds(template);
            if (value == null || value.denominatorEnd != boundary) continue;
            int span = value.denominatorEnd - value.numeratorStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private void setFractionCursor(FractionBounds fraction, CnCwCursorPath.Slot slot, int offset) {
        int length = slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? fraction.numeratorEnd - fraction.numeratorStart
                : fraction.denominatorEnd - fraction.denominatorStart;
        int local = Math.max(0, Math.min(length, offset));
        cursor = (slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? fraction.numeratorStart : fraction.denominatorStart) + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(fraction.templateIndex), slot, local, cursor);
    }

    private void setRootCursor(int boundary) {
        cursor = Math.max(0, Math.min(tokens.size(), boundary));
        semanticCursorOverride = CnCwCursorPath.rootBoundary(cursor);
    }

    private void finishSemanticCursorMove() {
        clearSelection();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
    }

    /**
     * Horizontal navigation has explicit same-boundary entry/exit states:
     * root-before → numerator → denominator → root-after.
     */
    private boolean moveFractionHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FractionBounds target = direction > 0
                    ? fractionStartingAtBoundary(cursor) : fractionEndingAtBoundary(cursor);
            if (target == null) return false;
            if (direction > 0) {
                setFractionCursor(target, CnCwCursorPath.Slot.FRACTION_NUMERATOR, 0);
            } else {
                setFractionCursor(target, CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                        target.denominatorEnd - target.denominatorStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            int length = bounds.numeratorEnd - bounds.numeratorStart;
            if (direction < 0) {
                if (fraction.offset == 0) setRootCursor(bounds.numeratorStart);
                else setFractionCursor(bounds, fraction.slot, fraction.offset - 1);
            } else {
                if (fraction.offset < length) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset + 1);
                } else {
                    setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 0);
                }
            }
        } else {
            int length = bounds.denominatorEnd - bounds.denominatorStart;
            if (direction < 0) {
                if (fraction.offset > 0) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset - 1);
                } else {
                    setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                            bounds.numeratorEnd - bounds.numeratorStart);
                }
            } else {
                if (fraction.offset < length) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset + 1);
                } else {
                    setRootCursor(bounds.denominatorEnd);
                }
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Moves between numerator and denominator without invoking history recall. */
    private boolean moveFractionVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (direction < 0 && fraction.slot == CnCwCursorPath.Slot.FRACTION_DENOMINATOR) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    Math.min(fraction.offset, bounds.numeratorEnd - bounds.numeratorStart));
        } else if (direction > 0
                && fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                    Math.min(fraction.offset, bounds.denominatorEnd - bounds.denominatorStart));
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * DEL inside a fraction removes only slot content. At a slot boundary it
     * navigates instead of deleting the structural separator. From root-after,
     * DEL removes the complete fraction atomically.
     */
    private boolean deleteFractionSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            if (semanticCursorOverride == null) return false;
            FractionBounds fraction = fractionEndingAtBoundary(cursor);
            if (fraction == null) return false;
            rememberUndo();
            tokens.subList(fraction.numeratorStart, fraction.denominatorEnd).clear();
            setRootCursor(fraction.numeratorStart);
            finishSemanticEditMutation();
            return true;
        }

        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            if (fraction.offset == 0) {
                setRootCursor(bounds.numeratorStart);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.numeratorStart || deleteIndex >= bounds.numeratorEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            int newTemplate = Math.max(0, fraction.templateIndex - 1);
            semanticCursorOverride = CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(newTemplate),
                    CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    Math.max(0, fraction.offset - 1), cursor);
            finishSemanticEditMutation();
            return true;
        }

        if (fraction.offset == 0) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    bounds.numeratorEnd - bounds.numeratorStart);
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.denominatorStart || deleteIndex >= bounds.denominatorEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(fraction.templateIndex),
                CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                Math.max(0, fraction.offset - 1), cursor);
        finishSemanticEditMutation();
        return true;
    }

    private void finishSemanticEditMutation() {
        clearSelection();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = applicationStatus();
    }

    private static boolean isPowerTemplate(Token token) {
        return "^".equals(token.evaluation) && "^".equals(token.display);
    }

    private PowerBounds powerBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isPowerTemplate(tokens.get(templateIndex))) return null;
        int baseStart = powerBaseStart(templateIndex);
        int baseEnd = templateIndex;
        int exponentStart = templateIndex + 1;
        int exponentEnd = powerExponentEnd(exponentStart, tokens.size());
        return new PowerBounds(templateIndex, baseStart, baseEnd, exponentStart, exponentEnd);
    }

    /** Keeps a complete fraction as the base of a power when one ends at ^. */
    private int powerBaseStart(int templateIndex) {
        if (templateIndex <= 0) return Math.max(0, templateIndex);
        Token previous = tokens.get(templateIndex - 1);
        if (previous.binary) return templateIndex;
        FractionBounds fraction = fractionEndingAtBoundary(templateIndex);
        if (fraction != null) return fraction.numeratorStart;

        // Chained powers use the complete previous power as the next base.
        PowerBounds previousPower = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < templateIndex; candidate++) {
            if (!isPowerTemplate(tokens.get(candidate))) continue;
            int candidateEnd = powerExponentEnd(candidate + 1, templateIndex);
            if (candidateEnd != templateIndex) continue;
            int candidateStart = candidate <= 0 ? candidate : semanticAtomStart(candidate);
            int span = templateIndex - candidateStart;
            if (span < bestSpan) {
                bestSpan = span;
                previousPower = new PowerBounds(candidate, candidateStart, candidate,
                        candidate + 1, templateIndex);
            }
        }
        if (previousPower != null) return previousPower.baseStart;
        return semanticAtomStart(templateIndex);
    }

    /** Empty exponents stop before the following top-level binary operator. */
    private int powerExponentEnd(int start, int limit) {
        int safe = Math.max(0, Math.min(limit, start));
        if (safe >= limit) return safe;
        if (tokens.get(safe).binary) return safe;
        return naturalExponentEnd(safe, limit, false);
    }

    private PowerCursor powerCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        PowerCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds bounds = powerBounds(template);
            if (bounds == null) continue;
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (safe >= bounds.baseStart && safe <= bounds.baseEnd) {
                slot = CnCwCursorPath.Slot.SUPERSCRIPT_BASE;
                offset = safe - bounds.baseStart;
            } else if (safe >= bounds.exponentStart && safe <= bounds.exponentEnd) {
                slot = CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT;
                offset = safe - bounds.exponentStart;
            }
            if (slot == null) continue;
            int span = bounds.exponentEnd - bounds.baseStart;
            if (span < bestSpan) {
                bestSpan = span;
                best = new PowerCursor(template, bounds.baseStart, bounds.baseEnd,
                        bounds.exponentStart, bounds.exponentEnd, slot, offset);
            }
        }
        return best;
    }

    private PowerCursor powerCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                && slot != CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT) return null;
        int template = path.childPath().get(0);
        PowerBounds bounds = powerBounds(template);
        if (bounds == null) return null;
        int length = slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? bounds.baseEnd - bounds.baseStart
                : bounds.exponentEnd - bounds.exponentStart;
        int offset = Math.max(0, Math.min(length, path.offset()));
        return new PowerCursor(template, bounds.baseStart, bounds.baseEnd,
                bounds.exponentStart, bounds.exponentEnd, slot, offset);
    }

    private PowerBounds powerStartingAtBoundary(int boundary) {
        PowerBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds value = powerBounds(template);
            if (value == null || value.baseStart != boundary) continue;
            int span = value.exponentEnd - value.baseStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private PowerBounds powerEndingAtBoundary(int boundary) {
        PowerBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds value = powerBounds(template);
            if (value == null || value.exponentEnd != boundary) continue;
            int span = value.exponentEnd - value.baseStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private void setPowerCursor(PowerBounds power, CnCwCursorPath.Slot slot, int offset) {
        int length = slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? power.baseEnd - power.baseStart
                : power.exponentEnd - power.exponentStart;
        int local = Math.max(0, Math.min(length, offset));
        cursor = (slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? power.baseStart : power.exponentStart) + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(power.templateIndex), slot, local, cursor);
    }

    /** root-before → base → exponent → root-after. */
    private boolean movePowerHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            PowerBounds target = direction > 0
                    ? powerStartingAtBoundary(cursor) : powerEndingAtBoundary(cursor);
            if (target == null) return false;
            if (direction > 0) {
                setPowerCursor(target, CnCwCursorPath.Slot.SUPERSCRIPT_BASE, 0);
            } else {
                setPowerCursor(target, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                        target.exponentEnd - target.exponentStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        PowerCursor power = powerCursorFromPath(path);
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            int length = bounds.baseEnd - bounds.baseStart;
            if (direction < 0) {
                if (power.offset == 0) setRootCursor(bounds.baseStart);
                else setPowerCursor(bounds, power.slot, power.offset - 1);
            } else {
                if (power.offset < length) {
                    setPowerCursor(bounds, power.slot, power.offset + 1);
                } else {
                    setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT, 0);
                }
            }
        } else {
            int length = bounds.exponentEnd - bounds.exponentStart;
            if (direction < 0) {
                if (power.offset > 0) {
                    setPowerCursor(bounds, power.slot, power.offset - 1);
                } else {
                    setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                            bounds.baseEnd - bounds.baseStart);
                }
            } else {
                if (power.offset < length) {
                    setPowerCursor(bounds, power.slot, power.offset + 1);
                } else {
                    setRootCursor(bounds.exponentEnd);
                }
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * UP/DOWN follow visual geometry rather than reusing the same local token
     * offset.  A superscript is drawn to the upper-right of the base, so the
     * nearest lower insertion point is the base end; conversely entering the
     * exponent from the base starts at the exponent's left edge.  Reusing the
     * numeric offset made multi-digit bases jump into their middle and could
     * make repeated base/exponent movement feel stuck on-device.
     */
    private boolean movePowerVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        PowerCursor power = powerCursorFromPath(semanticCursorPath());
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (direction < 0 && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT, 0);
        } else if (direction > 0
                && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    bounds.baseEnd - bounds.baseStart);
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * DEL never removes ^ by itself. Inside a slot it deletes slot content;
     * slot-start DEL navigates to the preceding semantic position. From an
     * explicit root-after position, DEL removes the complete power atomically.
     */
    private boolean deletePowerSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            if (semanticCursorOverride == null) return false;
            PowerBounds power = powerEndingAtBoundary(cursor);
            if (power == null) return false;
            rememberUndo();
            tokens.subList(power.baseStart, power.exponentEnd).clear();
            setRootCursor(power.baseStart);
            finishSemanticEditMutation();
            return true;
        }

        PowerCursor power = powerCursorFromPath(path);
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            if (power.offset == 0) {
                setRootCursor(bounds.baseStart);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.baseStart || deleteIndex >= bounds.baseEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            int newTemplate = Math.max(0, power.templateIndex - 1);
            semanticCursorOverride = CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(newTemplate),
                    CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    Math.max(0, power.offset - 1), cursor);
            finishSemanticEditMutation();
            return true;
        }

        if (power.offset == 0) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    bounds.baseEnd - bounds.baseStart);
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.exponentStart || deleteIndex >= bounds.exponentEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(power.templateIndex),
                CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                Math.max(0, power.offset - 1), cursor);
        finishSemanticEditMutation();
        return true;
    }

    private static boolean isSquareRootTemplate(Token token) {
        return "sqrt(".equals(token.evaluation);
    }

    private static boolean isGenericRootTemplate(Token token) {
        return "root(".equals(token.evaluation);
    }

    private static boolean isFixedRootTemplate(Token token) {
        return token.evaluation.startsWith("root(")
                && token.evaluation.endsWith(",")
                && !isGenericRootTemplate(token);
    }

    private static boolean isRadicalTemplate(Token token) {
        return isSquareRootTemplate(token) || isGenericRootTemplate(token)
                || isFixedRootTemplate(token);
    }

    /** Closing parenthesis owned by sqrt/root, or -1 for the live unclosed slot. */
    private int radicalCloseIndex(int templateIndex) {
        int depth = 0;
        for (int index = templateIndex + 1; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth == 0) return index;
                depth--;
            } else if (opensParenthesis(token)) {
                depth++;
            }
        }
        return -1;
    }

    /** First top-level comma separating root(index, content). */
    private int rootSeparatorIndex(int templateIndex, int innerEnd) {
        int depth = 0;
        for (int index = templateIndex + 1; index < innerEnd; index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth > 0) depth--;
                continue;
            }
            if (depth == 0 && ",".equals(token.evaluation)) return index;
            if (opensParenthesis(token)) depth++;
        }
        return -1;
    }

    private RadicalBounds radicalBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()) return null;
        Token template = tokens.get(templateIndex);
        if (!isRadicalTemplate(template)) return null;
        int close = radicalCloseIndex(templateIndex);
        int innerEnd = close >= 0 ? close : tokens.size();
        int endExclusive = close >= 0 ? close + 1 : innerEnd;
        if (isGenericRootTemplate(template)) {
            int separator = rootSeparatorIndex(templateIndex, innerEnd);
            if (separator >= 0) {
                return new RadicalBounds(templateIndex, templateIndex + 1, separator,
                        separator, separator + 1, innerEnd, close, endExclusive);
            }
            return new RadicalBounds(templateIndex, templateIndex + 1, innerEnd,
                    -1, innerEnd, innerEnd, close, endExclusive);
        }
        return new RadicalBounds(templateIndex, -1, -1, -1,
                templateIndex + 1, innerEnd, close, endExclusive);
    }

    /** Finds the smallest root/radical slot owning an insertion boundary. */
    private RadicalCursor radicalCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        RadicalCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds bounds = radicalBounds(template);
            if (bounds == null) continue;
            Token token = tokens.get(template);
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (isGenericRootTemplate(token)) {
                if (safe >= bounds.indexStart && safe <= bounds.indexEnd) {
                    slot = CnCwCursorPath.Slot.ROOT_INDEX;
                    offset = safe - bounds.indexStart;
                } else if (bounds.separatorIndex >= 0
                        && safe >= bounds.contentStart && safe <= bounds.contentEnd) {
                    slot = CnCwCursorPath.Slot.ROOT_CONTENT;
                    offset = safe - bounds.contentStart;
                }
            } else if (safe >= bounds.contentStart && safe <= bounds.contentEnd) {
                slot = isSquareRootTemplate(token)
                        ? CnCwCursorPath.Slot.RADICAL_CONTENT
                        : CnCwCursorPath.Slot.ROOT_CONTENT;
                offset = safe - bounds.contentStart;
            }
            if (slot == null) continue;
            int span = bounds.endExclusive - bounds.templateIndex;
            if (span < bestSpan) {
                bestSpan = span;
                best = new RadicalCursor(template, slot, offset);
            }
        }
        return best;
    }

    private RadicalCursor radicalCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.RADICAL_CONTENT
                && slot != CnCwCursorPath.Slot.ROOT_INDEX
                && slot != CnCwCursorPath.Slot.ROOT_CONTENT) return null;
        int template = path.childPath().get(0);
        RadicalBounds bounds = radicalBounds(template);
        if (bounds == null) return null;
        Token token = tokens.get(template);
        int length;
        if (slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            if (!isGenericRootTemplate(token)) return null;
            length = bounds.indexEnd - bounds.indexStart;
        } else {
            if (slot == CnCwCursorPath.Slot.RADICAL_CONTENT
                    && !isSquareRootTemplate(token)) return null;
            if (slot == CnCwCursorPath.Slot.ROOT_CONTENT
                    && isSquareRootTemplate(token)) return null;
            if (isGenericRootTemplate(token) && bounds.separatorIndex < 0) return null;
            length = bounds.contentEnd - bounds.contentStart;
        }
        return new RadicalCursor(template, slot,
                Math.max(0, Math.min(length, path.offset())));
    }

    private RadicalBounds radicalStartingAtBoundary(int boundary) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null || value.templateIndex != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private RadicalBounds radicalEndingAtBoundary(int boundary) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null || value.endExclusive != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private RadicalBounds radicalContainingToken(int tokenIndex) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null) continue;
            boolean inIndex = value.indexStart >= 0
                    && tokenIndex >= value.indexStart && tokenIndex < value.indexEnd;
            boolean inContent = tokenIndex >= value.contentStart && tokenIndex < value.contentEnd;
            if (!inIndex && !inContent) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private boolean selectionWithinRadicalSlot(int start, int end, RadicalBounds bounds) {
        boolean withinIndex = bounds.indexStart >= 0
                && start >= bounds.indexStart && end <= bounds.indexEnd;
        boolean withinContent = start >= bounds.contentStart && end <= bounds.contentEnd;
        return withinIndex || withinContent;
    }

    private void setRadicalCursor(RadicalBounds radical, CnCwCursorPath.Slot slot, int offset) {
        int start;
        int length;
        if (slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            start = radical.indexStart;
            length = radical.indexEnd - radical.indexStart;
        } else {
            start = radical.contentStart;
            length = radical.contentEnd - radical.contentStart;
        }
        int local = Math.max(0, Math.min(length, offset));
        cursor = start + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(radical.templateIndex), slot, local, cursor);
    }

    /** root-before -> content, or root-before -> index -> content for n-th root. */
    private boolean moveRadicalHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            RadicalBounds target = direction > 0
                    ? radicalStartingAtBoundary(cursor) : radicalEndingAtBoundary(cursor);
            if (target == null) return false;
            Token template = tokens.get(target.templateIndex);
            if (direction > 0) {
                if (isGenericRootTemplate(template)) {
                    setRadicalCursor(target, CnCwCursorPath.Slot.ROOT_INDEX, 0);
                } else {
                    setRadicalCursor(target,
                            isSquareRootTemplate(template)
                                    ? CnCwCursorPath.Slot.RADICAL_CONTENT
                                    : CnCwCursorPath.Slot.ROOT_CONTENT,
                            0);
                }
            } else if (isGenericRootTemplate(template) && target.separatorIndex < 0) {
                setRadicalCursor(target, CnCwCursorPath.Slot.ROOT_INDEX,
                        target.indexEnd - target.indexStart);
            } else {
                setRadicalCursor(target,
                        isSquareRootTemplate(template)
                                ? CnCwCursorPath.Slot.RADICAL_CONTENT
                                : CnCwCursorPath.Slot.ROOT_CONTENT,
                        target.contentEnd - target.contentStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;
        Token template = tokens.get(bounds.templateIndex);

        if (radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            int length = bounds.indexEnd - bounds.indexStart;
            if (direction < 0) {
                if (radical.offset == 0) setRootCursor(bounds.templateIndex);
                else setRadicalCursor(bounds, radical.slot, radical.offset - 1);
            } else if (radical.offset < length) {
                setRadicalCursor(bounds, radical.slot, radical.offset + 1);
            } else if (bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_CONTENT, 0);
            } else {
                setRootCursor(bounds.endExclusive);
            }
            finishSemanticCursorMove();
            return true;
        }

        int length = bounds.contentEnd - bounds.contentStart;
        if (direction < 0) {
            if (radical.offset > 0) {
                setRadicalCursor(bounds, radical.slot, radical.offset - 1);
            } else if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        bounds.indexEnd - bounds.indexStart);
            } else {
                setRootCursor(bounds.templateIndex);
            }
        } else if (radical.offset < length) {
            setRadicalCursor(bounds, radical.slot, radical.offset + 1);
        } else {
            setRootCursor(bounds.endExclusive);
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Root index is visually above content; simple/fixed roots consume arrows in-place. */
    private boolean moveRadicalVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;
        Token template = tokens.get(bounds.templateIndex);
        if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
            if (direction < 0 && radical.slot == CnCwCursorPath.Slot.ROOT_CONTENT) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        Math.min(radical.offset, bounds.indexEnd - bounds.indexStart));
            } else if (direction > 0 && radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_CONTENT,
                        Math.min(radical.offset, bounds.contentEnd - bounds.contentStart));
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Never delete sqrt/root templates, commas, or their closing parenthesis piecemeal. */
    private boolean deleteRadicalSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            RadicalBounds radical = radicalEndingAtBoundary(cursor);
            if (radical == null) return false;
            rememberUndo();
            tokens.subList(radical.templateIndex, radical.endExclusive).clear();
            setRootCursor(radical.templateIndex);
            finishSemanticEditMutation();
            return true;
        }

        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;

        if (radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            if (radical.offset == 0) {
                setRootCursor(bounds.templateIndex);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.indexStart || deleteIndex >= bounds.indexEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            RadicalBounds updated = radicalBounds(radical.templateIndex);
            if (updated == null) return false;
            setRadicalCursor(updated, CnCwCursorPath.Slot.ROOT_INDEX,
                    Math.max(0, radical.offset - 1));
            finishSemanticEditMutation();
            return true;
        }

        if (radical.offset == 0) {
            Token template = tokens.get(bounds.templateIndex);
            if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        bounds.indexEnd - bounds.indexStart);
            } else {
                setRootCursor(bounds.templateIndex);
            }
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.contentStart || deleteIndex >= bounds.contentEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        RadicalBounds updated = radicalBounds(radical.templateIndex);
        if (updated == null) return false;
        setRadicalCursor(updated, radical.slot, Math.max(0, radical.offset - 1));
        finishSemanticEditMutation();
        return true;
    }

    /** Ordinary parenthesized function; radicals keep their dedicated Step 4 semantics. */
    private static boolean isFunctionTemplate(Token token) {
        String value = token.evaluation;
        if (!value.endsWith("(") || "(".equals(value) || isRadicalTemplate(token)) return false;
        // These are exponent-entry templates rather than ordinary function calls.
        return !"e^(".equals(value) && !"*10^(".equals(value);
    }

    private FunctionBounds functionBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isFunctionTemplate(tokens.get(templateIndex))) return null;
        int close = matchingClose(templateIndex);
        int innerEnd = close >= 0 ? close : tokens.size();
        int endExclusive = close >= 0 ? close + 1 : innerEnd;
        List<FunctionArgumentBounds> arguments = new ArrayList<>();
        int argumentStart = templateIndex + 1;
        int depth = 0;
        for (int index = argumentStart; index < innerEnd; index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth > 0) depth--;
                continue;
            }
            if (depth == 0 && ",".equals(token.evaluation)) {
                arguments.add(new FunctionArgumentBounds(argumentStart, index));
                argumentStart = index + 1;
                continue;
            }
            if (opensParenthesis(token)) depth++;
        }
        // Even an empty function owns one editable argument slot.
        arguments.add(new FunctionArgumentBounds(argumentStart, innerEnd));
        return new FunctionBounds(templateIndex,
                com.codex.fx991.core.Compat.copyList(arguments), close, endExclusive);
    }

    /** Smallest ordinary function owning this insertion boundary. */
    private FunctionCursor functionCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        FunctionCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds bounds = functionBounds(template);
            if (bounds == null) continue;
            for (int argumentIndex = 0; argumentIndex < bounds.arguments.size(); argumentIndex++) {
                FunctionArgumentBounds argument = bounds.arguments.get(argumentIndex);
                if (safe < argument.start || safe > argument.end) continue;
                int span = bounds.endExclusive - bounds.templateIndex;
                if (span < bestSpan) {
                    bestSpan = span;
                    best = new FunctionCursor(template, argumentIndex,
                            safe - argument.start);
                }
            }
        }
        return best;
    }

    private FunctionCursor functionCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary()
                || path.slot() != CnCwCursorPath.Slot.FUNCTION_ARGUMENT
                || path.childPath().size() < 2) return null;
        int template = path.childPath().get(0);
        int argumentIndex = path.childPath().get(1);
        FunctionBounds bounds = functionBounds(template);
        if (bounds == null || argumentIndex < 0 || argumentIndex >= bounds.arguments.size()) {
            return null;
        }
        FunctionArgumentBounds argument = bounds.arguments.get(argumentIndex);
        int length = argument.end - argument.start;
        return new FunctionCursor(template, argumentIndex,
                Math.max(0, Math.min(length, path.offset())));
    }

    private FunctionBounds functionStartingAtBoundary(int boundary) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null || value.templateIndex != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private FunctionBounds functionEndingAtBoundary(int boundary) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null || value.endExclusive != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    /** Smallest function whose concrete argument token contains tokenIndex. */
    private FunctionBounds functionContainingToken(int tokenIndex) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null) continue;
            boolean inArgument = false;
            for (FunctionArgumentBounds argument : value.arguments) {
                if (tokenIndex >= argument.start && tokenIndex < argument.end) {
                    inArgument = true;
                    break;
                }
            }
            if (!inArgument) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private boolean selectionWithinFunctionArgument(int start, int end, FunctionBounds function) {
        for (FunctionArgumentBounds argument : function.arguments) {
            if (start >= argument.start && end <= argument.end) return true;
        }
        return false;
    }


    /**
     * True when a touch range remains inside the smallest nested editable slot
     * owning tokenIndex. This keeps fine selection inside radical/function
     * content while still snapping across structural commas or parentheses.
     */
    private boolean selectionWithinSemanticEditableSlot(int start, int end, int tokenIndex) {
        RadicalBounds radical = radicalContainingToken(tokenIndex);
        if (radical != null && selectionWithinRadicalSlot(start, end, radical)) return true;
        FunctionBounds function = functionContainingToken(tokenIndex);
        return function != null && selectionWithinFunctionArgument(start, end, function);
    }

    /**
     * Publishes every currently editable nested slot. The Android adapter uses
     * these spans for geometry-aware hit testing while legacy token boundaries
     * remain available as a fallback.
     */
    private List<CnCwSemanticSpan> semanticSpans() {
        List<CnCwSemanticSpan> spans = new ArrayList<>();
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds fraction = fractionBounds(template);
            if (fraction != null) {
                spans.add(new CnCwSemanticSpan(
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                        fraction.numeratorStart, fraction.numeratorEnd,
                        fraction.numeratorStart, fraction.denominatorEnd));
                spans.add(new CnCwSemanticSpan(
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                        fraction.denominatorStart, fraction.denominatorEnd,
                        fraction.numeratorStart, fraction.denominatorEnd));
            }

            PowerBounds power = powerBounds(template);
            if (power != null) {
                spans.add(new CnCwSemanticSpan(
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                        power.baseStart, power.baseEnd,
                        power.baseStart, power.exponentEnd));
                spans.add(new CnCwSemanticSpan(
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                        power.exponentStart, power.exponentEnd,
                        power.baseStart, power.exponentEnd));
            }

            RadicalBounds radical = radicalBounds(template);
            if (radical != null) {
                Token token = tokens.get(template);
                if (radical.indexStart >= 0) {
                    spans.add(new CnCwSemanticSpan(
                            com.codex.fx991.core.Compat.list(template),
                            CnCwCursorPath.Slot.ROOT_INDEX,
                            radical.indexStart, radical.indexEnd,
                            radical.templateIndex, radical.endExclusive));
                }
                CnCwCursorPath.Slot contentSlot = isSquareRootTemplate(token)
                        ? CnCwCursorPath.Slot.RADICAL_CONTENT
                        : CnCwCursorPath.Slot.ROOT_CONTENT;
                spans.add(new CnCwSemanticSpan(
                        com.codex.fx991.core.Compat.list(template), contentSlot,
                        radical.contentStart, radical.contentEnd,
                        radical.templateIndex, radical.endExclusive));
            }

            FunctionBounds function = functionBounds(template);
            if (function != null) {
                for (int argumentIndex = 0; argumentIndex < function.arguments.size();
                     argumentIndex++) {
                    FunctionArgumentBounds argument = function.arguments.get(argumentIndex);
                    spans.add(new CnCwSemanticSpan(
                            com.codex.fx991.core.Compat.list(template, argumentIndex),
                            CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                            argument.start, argument.end,
                            function.templateIndex, function.endExclusive));
                }
            }
        }
        return com.codex.fx991.core.Compat.copyList(spans);
    }

    /** Smallest semantic slot containing the complete normalized selection. */
    private SemanticSelectionScope semanticSelectionScope(int start, int end) {
        if (start < 0 || end < 0 || start == end) return null;
        int lo = Math.min(start, end);
        int hi = Math.max(start, end);
        SemanticSelectionScope best = null;

        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds fraction = fractionBounds(template);
            if (fraction != null) {
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                        fraction.numeratorStart, fraction.numeratorEnd));
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                        fraction.denominatorStart, fraction.denominatorEnd));
            }

            PowerBounds power = powerBounds(template);
            if (power != null) {
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                        power.baseStart, power.baseEnd));
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                        power.exponentStart, power.exponentEnd));
            }

            RadicalBounds radical = radicalBounds(template);
            if (radical != null) {
                if (radical.indexStart >= 0) {
                    best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                            com.codex.fx991.core.Compat.list(template),
                            CnCwCursorPath.Slot.ROOT_INDEX,
                            radical.indexStart, radical.indexEnd));
                }
                CnCwCursorPath.Slot contentSlot = isSquareRootTemplate(tokens.get(template))
                        ? CnCwCursorPath.Slot.RADICAL_CONTENT
                        : CnCwCursorPath.Slot.ROOT_CONTENT;
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template), contentSlot,
                        radical.contentStart, radical.contentEnd));
            }

            FunctionBounds function = functionBounds(template);
            if (function != null) {
                for (int argumentIndex = 0; argumentIndex < function.arguments.size(); argumentIndex++) {
                    FunctionArgumentBounds argument = function.arguments.get(argumentIndex);
                    best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                            com.codex.fx991.core.Compat.list(template, argumentIndex),
                            CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                            argument.start, argument.end));
                }
            }
        }
        return best;
    }

    private SemanticSelectionScope containedSelectionScope(int start, int end,
                                                            List<Integer> childPath,
                                                            CnCwCursorPath.Slot slot,
                                                            int slotStart, int slotEnd) {
        if (slotStart < 0 || slotEnd < slotStart || start < slotStart || end > slotEnd) {
            return null;
        }
        return new SemanticSelectionScope(childPath, slot, slotStart, slotEnd);
    }

    private SemanticSelectionScope preferSelectionScope(SemanticSelectionScope current,
                                                         SemanticSelectionScope candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        int currentSpan = current.slotEnd - current.slotStart;
        int candidateSpan = candidate.slotEnd - candidate.slotStart;
        if (candidateSpan < currentSpan) return candidate;
        if (candidateSpan == currentSpan
                && candidate.childPath.size() > current.childPath.size()) return candidate;
        return current;
    }

    /** Semantic facade for legacy selection anchor/focus boundaries. */
    private CnCwCursorPath semanticSelectionPath(int boundary) {
        if (!selectionActive()) return semanticCursorPath();
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        SemanticSelectionScope scope = semanticSelectionScope(selectionStart(), selectionEnd());
        if (scope == null || safe < scope.slotStart || safe > scope.slotEnd) {
            return CnCwCursorPath.rootBoundary(safe);
        }
        return CnCwCursorPath.nested(scope.childPath, scope.slot,
                safe - scope.slotStart, safe);
    }

    private void setFunctionCursor(FunctionBounds function, int argumentIndex, int offset) {
        if (function.arguments.isEmpty()) {
            setRootCursor(function.templateIndex);
            return;
        }
        int safeArgument = Math.max(0, Math.min(function.arguments.size() - 1, argumentIndex));
        FunctionArgumentBounds argument = function.arguments.get(safeArgument);
        int length = argument.end - argument.start;
        int local = Math.max(0, Math.min(length, offset));
        cursor = argument.start + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(function.templateIndex, safeArgument),
                CnCwCursorPath.Slot.FUNCTION_ARGUMENT, local, cursor);
    }

    /** root-before -> arg0 -> arg1 ... -> root-after, never landing on a separator comma. */
    private boolean moveFunctionHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FunctionBounds target = direction > 0
                    ? functionStartingAtBoundary(cursor) : functionEndingAtBoundary(cursor);
            if (target == null || target.arguments.isEmpty()) return false;
            if (direction > 0) {
                setFunctionCursor(target, 0, 0);
            } else {
                int last = target.arguments.size() - 1;
                FunctionArgumentBounds argument = target.arguments.get(last);
                setFunctionCursor(target, last, argument.end - argument.start);
            }
            finishSemanticCursorMove();
            return true;
        }

        FunctionCursor function = functionCursorFromPath(path);
        if (function == null) return false;
        FunctionBounds bounds = functionBounds(function.templateIndex);
        if (bounds == null || function.argumentIndex >= bounds.arguments.size()) return false;
        FunctionArgumentBounds argument = bounds.arguments.get(function.argumentIndex);
        int length = argument.end - argument.start;
        if (direction < 0) {
            if (function.offset > 0) {
                setFunctionCursor(bounds, function.argumentIndex, function.offset - 1);
            } else if (function.argumentIndex > 0) {
                int previousIndex = function.argumentIndex - 1;
                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);
                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);
            } else {
                setRootCursor(bounds.templateIndex);
            }
        } else {
            if (function.offset < length) {
                setFunctionCursor(bounds, function.argumentIndex, function.offset + 1);
            } else if (function.argumentIndex + 1 < bounds.arguments.size()) {
                setFunctionCursor(bounds, function.argumentIndex + 1, 0);
            } else {
                setRootCursor(bounds.endExclusive);
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * DEL removes only the current function argument content. At an argument
     * boundary it navigates over the structural comma/template instead of
     * deleting it. From root-after a complete function is removed atomically.
     */
    private boolean deleteFunctionSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FunctionBounds function = functionEndingAtBoundary(cursor);
            if (function == null) return false;
            rememberUndo();
            tokens.subList(function.templateIndex, function.endExclusive).clear();
            setRootCursor(function.templateIndex);
            finishSemanticEditMutation();
            return true;
        }

        FunctionCursor function = functionCursorFromPath(path);
        if (function == null) return false;
        FunctionBounds bounds = functionBounds(function.templateIndex);
        if (bounds == null || function.argumentIndex >= bounds.arguments.size()) return false;
        FunctionArgumentBounds argument = bounds.arguments.get(function.argumentIndex);
        if (function.offset == 0) {
            if (function.argumentIndex > 0) {
                int previousIndex = function.argumentIndex - 1;
                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);
                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);
                finishSemanticCursorMove();
                return true;
            }
            // Stage 2 contract: a freshly inserted bare function token such as
            // sin( is one semantic token, so DEL removes it in one press.
            if (bounds.closeIndex < 0 && bounds.arguments.size() == 1
                    && argument.start == argument.end
                    && bounds.endExclusive == bounds.templateIndex + 1) {
                rememberUndo();
                tokens.remove(bounds.templateIndex);
                setRootCursor(bounds.templateIndex);
                finishSemanticEditMutation();
                return true;
            }
            setRootCursor(bounds.templateIndex);
            finishSemanticCursorMove();
            return true;
        }

        int deleteIndex = cursor - 1;
        if (deleteIndex < argument.start || deleteIndex >= argument.end) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        FunctionBounds updated = functionBounds(function.templateIndex);
        if (updated == null || function.argumentIndex >= updated.arguments.size()) {
            setRootCursor(Math.min(cursor, tokens.size()));
        } else {
            setFunctionCursor(updated, function.argumentIndex, Math.max(0, function.offset - 1));
        }
        finishSemanticEditMutation();
        return true;
    }

    private List<String> spreadsheetCellsSnapshot() {
        List<String> values = new ArrayList<>(SpreadsheetModel.ROWS * SpreadsheetModel.COLUMNS);
        for (int row = 0; row < SpreadsheetModel.ROWS; row++) {
            for (int column = 0; column < SpreadsheetModel.COLUMNS; column++) {
                String address = (char) ('A' + column) + Integer.toString(row + 1);
                try {
                    String input = spreadsheet.input(address);
                    values.add(input.isEmpty() ? "" : formatNumber(spreadsheet.value(address)));
                } catch (RuntimeException error) {
                    values.add("错误");
                }
            }
        }
        return values;
    }

    /**
     * Publishes the current semantic-token editor as a renderer-owned tree.
     * Compound templates will replace the individual text leaves as their
     * cursor model lands; the cursor leaf keeps this intermediate form useful
     * to both the legacy string renderer and the natural-display renderer.
     */
    private CnCwExpressionNode naturalExpression() {
        CnCwCursorPath semantic = semanticCursorPath();
        if (semanticCursorOverride != null && semantic.isRootBoundary()) {
            CnCwExpressionNode before = naturalRow(0, cursor, -1);
            CnCwExpressionNode after = naturalRow(cursor, tokens.size(), -1);
            List<CnCwExpressionNode> children = new ArrayList<>(
                    before.children().size() + after.children().size() + 1);
            children.addAll(before.children());
            children.add(CnCwExpressionNode.cursor());
            children.addAll(after.children());
            return CnCwExpressionNode.row(children);
        }
        return naturalRow(0, tokens.size(), cursor);
    }

    /** Compatibility wrapper for non-Stage-3 callers inside this class. */
    private CnCwExpressionNode naturalRow(int start, int end) {
        return naturalRow(start, end, cursor);
    }

    /** Builds a visual tree without changing the semantic expression tokens. */
    private CnCwExpressionNode naturalRow(int start, int end, int renderCursor) {
        List<CnCwExpressionNode> children = new ArrayList<>(Math.max(1, end - start + 1));
        int cursorHandledAt = -1;
        int index = start;
        while (index < end) {
            if (index == renderCursor && index != cursorHandledAt) {
                children.add(CnCwExpressionNode.cursor());
            }
            Token token = tokens.get(index);
            if (isPowerTemplate(token)) {
                PowerBounds bounds = powerBounds(index);
                if (bounds != null) {
                    int baseStart = Math.max(start, bounds.baseStart);
                    int exponentEnd = Math.min(end, bounds.exponentEnd);
                    CnCwCursorPath semantic = semanticCursorPath();
                    boolean ownsCursor = !semantic.isRootBoundary()
                            && !semantic.childPath().isEmpty()
                            && semantic.childPath().get(0) == index
                            && (semantic.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                            || semantic.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT);
                    int prefixCursor = ownsCursor ? -1 : renderCursor;
                    int baseCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                            ? -1 : renderCursor;
                    int exponentCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT
                            ? -1 : renderCursor;
                    CnCwExpressionNode prefix = naturalRow(start, baseStart, prefixCursor);
                    CnCwExpressionNode base = naturalRow(baseStart, index, baseCursor);
                    CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, exponentCursor);
                    children.clear();
                    children.addAll(prefix.children());
                    children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                            com.codex.fx991.core.Compat.list(base, exponent), false));
                    if (base.containsCursor() || exponent.containsCursor()) {
                        cursorHandledAt = renderCursor;
                    }
                    index = exponentEnd;
                    continue;
                }
            }
            if (isFractionTemplate(token)) {
                FractionBounds bounds = fractionBounds(index);
                if (bounds != null) {
                    int numeratorStart = Math.max(start, bounds.numeratorStart);
                    int denominatorEnd = Math.min(end, bounds.denominatorEnd);
                    CnCwCursorPath semantic = semanticCursorPath();
                    boolean ownsCursor = !semantic.isRootBoundary()
                            && !semantic.childPath().isEmpty()
                            && semantic.childPath().get(0) == index;
                    int prefixCursor = ownsCursor ? -1 : renderCursor;
                    int numeratorCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.FRACTION_NUMERATOR
                            ? -1 : renderCursor;
                    int denominatorCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.FRACTION_DENOMINATOR
                            ? -1 : renderCursor;
                    CnCwExpressionNode prefix = naturalRow(start, numeratorStart, prefixCursor);
                    CnCwExpressionNode numerator = naturalRow(numeratorStart, index, numeratorCursor);
                    CnCwExpressionNode denominator = naturalRow(index + 1, denominatorEnd,
                            denominatorCursor);
                    children.clear();
                    children.addAll(prefix.children());
                    children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.FRACTION,
                            com.codex.fx991.core.Compat.list(numerator, denominator), false));
                    if (numerator.containsCursor() || denominator.containsCursor()) {
                        cursorHandledAt = renderCursor;
                    }
                    index = denominatorEnd;
                    continue;
                }
            }
            if ("*10^(".equals(token.evaluation) && index != renderCursor) {
                int exponentEnd = naturalExponentEnd(index + 1, end, true);
                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, renderCursor);
                CnCwExpressionNode ten = CnCwExpressionNode.text("\u00d710", false);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                        com.codex.fx991.core.Compat.list(ten, exponent), false));
                if (exponent.containsCursor() && renderCursor == exponentEnd) {
                    cursorHandledAt = exponentEnd;
                }
                index = exponentEnd;
                if (index < end && ")".equals(tokens.get(index).evaluation)) index++;
                continue;
            }
            children.add(CnCwExpressionNode.text(token.display, isTokenSelected(index)));
            index++;
        }
        if (renderCursor == end && renderCursor != cursorHandledAt) {
            children.add(CnCwExpressionNode.cursor());
        }
        return CnCwExpressionNode.row(children);
    }

    private static boolean isFractionTemplate(Token token) {
        return "/".equals(token.evaluation) && "a/b".equals(token.display);
    }

    private boolean isTokenSelected(int index) {
        return selectionActive() && index >= selectionStart() && index < selectionEnd();
    }

    /**
     * Finds the visually attached exponent.  Normal powers end at the next
     * top-level binary operator.  The EXP template owns an implicit opening
     * parenthesis, so its matching close key is kept out of the exponent tree.
     */
    private int naturalExponentEnd(int start, int limit, boolean engineeringTemplate) {
        int depth = 0;
        boolean hasContent = false;
        for (int index = start; index < limit; index++) {
            Token token = tokens.get(index);
            String evaluation = token.evaluation;
            if (")".equals(evaluation)) {
                if (depth == 0 && engineeringTemplate) return index;
                if (depth == 0) return index;
                depth--;
                hasContent = true;
                continue;
            }
            if (hasContent && depth == 0 && token.binary) return index;
            if (evaluation.endsWith("(")) depth++;
            if (!token.binary || hasContent) hasContent = true;
        }
        return limit;
    }

    private String expression() {
        StringBuilder text = new StringBuilder(tokens.size() * 2);
        for (Token token : tokens) text.append(token.evaluation);
        return text.toString();
    }

    /**
     * Preserves semantic token boundaries for the scalar lexer.  The
     * invisible separator is ignored as whitespace, but prevents A+B key
     * tokens from collapsing into identifier AB and 1+E from becoming a
     * malformed scientific literal. Consecutive numeric-entry keys remain a
     * single number.
     */
    private String evaluationSource() {
        StringBuilder text = new StringBuilder(tokens.size() * 3);
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0 && needsLexicalBoundary(tokens.get(index - 1), tokens.get(index))) {
                text.append('\u2063');
            }
            text.append(tokens.get(index).evaluation);
        }
        return text.toString();
    }

    private static boolean needsLexicalBoundary(Token left, Token right) {
        return !(isNumericFragment(left) && isNumericFragment(right));
    }

    private static boolean isNumericFragment(Token token) {
        return token.evaluation.length() == 1
                && (Character.isDigit(token.evaluation.charAt(0))
                || token.evaluation.charAt(0) == '.');
    }

    private String displayText() {
        if (!screen.isApplication()) return "";
        if (tokens.isEmpty()) return "│";
        StringBuilder text = new StringBuilder(tokens.size() * 2 + 1);
        for (int i = 0; i <= tokens.size(); i++) {
            if (i == cursor) text.append('│');
            if (i < tokens.size()) text.append(tokens.get(i).display);
        }
        return text.toString();
    }

    private List<CnCwScreen> navigationPath() {
        List<CnCwScreen> path = new ArrayList<>();
        for (Navigation value : navigation) path.add(0, value.screen);
        path.add(screen);
        return path;
    }

    private String formatResult(double value, ExactValue exactValue, String source,
                                boolean forceExact) {
        boolean exactCandidate = forceExact
                || settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH
                || settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_LINEAR;
        if (exactCandidate && exactValue != null && exactValue.isDisplayable()) {
            Rational rational = exactValue.rational();
            if (rational != null) {
                if (rational.isInteger()) return rational.numerator().toString();
                return settings.fractionMode() == CnCwSettings.FractionMode.MIXED
                        ? rational.mixedString() : rational.improperString();
            }
            return exactValue.display();
        }
        if (exactCandidate && source.matches("[0-9+\\-*/(). ]+") && source.contains("/")) {
            Rational rational = Rational.approximate(value, 1_000_000L, 1e-12);
            return settings.fractionMode() == CnCwSettings.FractionMode.MIXED
                    ? rational.mixedString() : rational.improperString();
        }
        return formatNumber(value);
    }

    private String formatNumber(double value) {
        if (settings.engineeringSymbols() && value != 0.0 && Double.isFinite(value)) {
            ManualFunctions.EngineeringValue engineering = ManualFunctions.engineering(value);
            if (!engineering.symbol().isEmpty()) {
                return formatPlainNumber(engineering.mantissa()) + engineering.symbol();
            }
        }
        return formatPlainNumber(value);
    }

    private String formatPlainNumber(double value) {
        String text;
        switch (settings.displayMode()) {
            case FIX -> text = String.format(Locale.ROOT, "%." + settings.displayDigits() + "f", value);
            case SCI -> text = String.format(Locale.ROOT, "%." + Math.max(0, settings.displayDigits() - 1) + "E", value);
            case NORM_1, NORM_2 -> {
                double absolute = Math.abs(value);
                double small = settings.displayMode() == CnCwSettings.DisplayMode.NORM_1 ? 1e-2 : 1e-9;
                if (absolute != 0.0 && (absolute < small || absolute >= 1e10)) {
                    text = String.format(Locale.ROOT, "%.9E", value).replaceAll("0+E", "E");
                } else {
                    text = trimDouble(value);
                }
            }
            default -> text = trimDouble(value);
        }
        if (settings.digitSeparator() && !text.contains("E")) text = separateDigits(text);
        if (settings.decimalMark() == CnCwSettings.DecimalMark.COMMA) text = text.replace('.', ',');
        return text;
    }

    private String formatComplex(ComplexValue value) {
        return formatComplex(value, settings.complexMode() == CnCwSettings.ComplexMode.POLAR);
    }

    private static boolean needsComplexEvaluation(String source) {
        if (containsImaginaryUnit(source)) return true;
        String compact = source == null ? "" : source.replace(" ", "");
        return compact.contains("sqrt(-") || compact.contains("root(") && compact.contains(",-" )
                || compact.contains("^") && compact.contains("-");
    }

    private static boolean containsImaginaryUnit(String source) {
        if (source == null) return false;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) != 'i') continue;
            boolean before = index > 0 && Character.isLetter(source.charAt(index - 1));
            boolean after = index + 1 < source.length() && Character.isLetter(source.charAt(index + 1));
            if (!before && !after) return true;
        }
        return false;
    }

    private String formatComplex(ComplexValue value, boolean polar) {
        if (polar) {
            double angle = switch (settings.angleUnit()) {
                case DEG -> Math.toDegrees(value.argument());
                case GRAD -> value.argument() * 200.0 / Math.PI;
                case RAD -> value.argument();
            };
            return formatNumber(value.abs()) + "∠" + formatNumber(angle);
        }
        if (value.imaginary() == 0.0) return formatNumber(value.real());
        if (value.real() == 0.0) {
            if (Math.abs(value.imaginary() - 1.0) < 1e-14) return "i";
            if (Math.abs(value.imaginary() + 1.0) < 1e-14) return "−i";
            return formatNumber(value.imaginary()) + "i";
        }
        String imaginary = Math.abs(value.imaginary() - 1.0) < 1e-14
                ? "i" : Math.abs(value.imaginary() + 1.0) < 1e-14
                ? "i" : formatNumber(Math.abs(value.imaginary())) + "i";
        return formatNumber(value.real()) + (value.imaginary() < 0 ? "−" : "+") + imaginary;
    }

    private String engineeringResult(double value) {
        ManualFunctions.EngineeringValue engineering = ManualFunctions.engineering(value);
        if (engineering.exponent() == 0) return formatNumber(engineering.mantissa());
        return formatNumber(engineering.mantissa()) + "×10^" + engineering.exponent()
                + (engineering.symbol().isEmpty() ? "" : " (" + engineering.symbol() + ")");
    }

    private String primeFactors(double value) {
        if (value != Math.rint(value) || value <= 0.0 || value > 9_999_999_999L) {
            return "Math ERROR";
        }
        long remaining = (long) value;
        if (remaining < 2L) return Long.toString(remaining);
        List<Long> factors = new ArrayList<>();
        for (long factor = 2L; factor <= remaining / factor;
             factor += factor == 2L ? 1L : 2L) {
            while (remaining % factor == 0L) {
                factors.add(factor);
                remaining /= factor;
            }
        }
        if (remaining > 1L) factors.add(remaining);

        int largeCount = 0;
        boolean hasManualLimitFactor = false;
        for (long factor : factors) {
            if (factor > 999L) largeCount++;
            if (factor >= 1_018_081L) hasManualLimitFactor = true;
        }
        long unresolved = 1L;
        Map<Long, Integer> displayFactors = new java.util.LinkedHashMap<>();
        for (long factor : factors) {
            boolean defer = factor >= 1_018_081L || (largeCount >= 2 && factor > 999L);
            if (defer) unresolved *= factor;
            else displayFactors.merge(factor, 1, Integer::sum);
        }
        if (hasManualLimitFactor && unresolved == 1L) {
            unresolved = factors.get(factors.size() - 1);
            displayFactors.remove(unresolved);
        }
        StringBuilder output = new StringBuilder();
        for (Map.Entry<Long, Integer> factor : displayFactors.entrySet()) {
            if (output.length() > 0) output.append('×');
            output.append(factor.getKey());
            if (factor.getValue() > 1) output.append('^').append(factor.getValue());
        }
        if (unresolved > 1L) {
            if (output.length() > 0) output.append('×');
            output.append('(').append(unresolved).append(')');
        }
        return output.toString();
    }

    private String settingValue(int index) {
        return switch (index) {
            case 0 -> settings.inputOutput().name();
            case 1 -> settings.angleUnit().name();
            case 2 -> settings.displayMode().name() + ((settings.displayMode() == CnCwSettings.DisplayMode.FIX
                    || settings.displayMode() == CnCwSettings.DisplayMode.SCI)
                    ? " " + settings.displayDigits() : "");
            case 3 -> settings.engineeringSymbols() ? "开" : "关";
            case 4 -> settings.fractionMode() == CnCwSettings.FractionMode.MIXED ? "带分数" : "假分数";
            case 5 -> settings.complexMode() == CnCwSettings.ComplexMode.RECTANGULAR ? "a+bi" : "r∠θ";
            case 6 -> settings.decimalMark() == CnCwSettings.DecimalMark.POINT ? "句点" : "逗号";
            default -> settings.digitSeparator() ? "开" : "关";
        };
    }

    private String applicationStatus() {
        return application == null ? "HOME" : application.chineseName();
    }

    private static String applicationDescription(ApplicationMode mode) {
        return switch (mode) {
            case CALCULATE -> "基本与高级计算"; case STATISTICS -> "统计与回归";
            case DISTRIBUTION -> "正态、二项、泊松"; case SPREADSHEET -> "A1:E45";
            case FUNCTION_TABLE -> "一个或两个函数"; case EQUATION -> "联立、高阶与 SOLVE";
            case INEQUALITY -> "二至四次"; case COMPLEX -> "复数";
            case BASE_N -> "BIN/OCT/DEC/HEX"; case MATRIX -> "最大 4×4";
            case VECTOR -> "2D/3D"; case RATIO -> "比例式";
        };
    }

    private static String sizeLabel(int count) { return count + " applications"; }

    private static boolean isStructuredWorkflow(ApplicationMode mode) {
        return mode == ApplicationMode.STATISTICS || mode == ApplicationMode.DISTRIBUTION
                || mode == ApplicationMode.FUNCTION_TABLE || mode == ApplicationMode.EQUATION
                || mode == ApplicationMode.INEQUALITY || mode == ApplicationMode.MATRIX
                || mode == ApplicationMode.VECTOR || mode == ApplicationMode.RATIO;
    }

    private CnCwModeEngine.ModeResult spreadsheetWorkflow(String source) {
        List<String> fields = CnCwModeEngine.splitTopLevel(source);
        if (activeCommandId.equals("sheet")) {
            if (fields.size() == 1) {
                String field = fields.get(0);
                if (spreadsheetGrid && !field.matches("(?i)\\$?[A-E]\\$?\\d{1,2}")) {
                    String address = spreadsheetAddress();
                    String input = field;
                    if (!input.startsWith("=")
                            && input.matches(".*[A-Ea-e][1-9][0-9]?.*")) input = "=" + input;
                    spreadsheet.set(address, input);
                    double value = spreadsheet.value(address);
                    return new CnCwModeEngine.ModeResult(address + "=" + formatNumber(value)
                            + "\n剩余 " + spreadsheet.remainingBytes() + " bytes", value);
                }
                double value = spreadsheet.value(field);
                return new CnCwModeEngine.ModeResult(field.toUpperCase(Locale.ROOT)
                        + "=" + formatNumber(value), value);
            }
            String address = fields.get(0).toUpperCase(Locale.ROOT);
            String input = com.codex.fx991.core.Compat.join(",",
                    fields.subList(1, fields.size()));
            if (!input.startsWith("=") && input.matches(".*[A-Ea-e][1-9][0-9]?.*")) input = "=" + input;
            spreadsheet.set(address, input);
            double value = spreadsheet.value(address);
            return new CnCwModeEngine.ModeResult(address + "=" + formatNumber(value)
                    + "\n剩余 " + spreadsheet.remainingBytes() + " bytes", value);
        }
        if (activeCommandId.equals("fill")) {
            if (fields.size() < 3) throw new IllegalArgumentException("Enter start,end,formula");
            String range = fields.get(0).toUpperCase(Locale.ROOT) + ":"
                    + fields.get(1).toUpperCase(Locale.ROOT);
            String formula = com.codex.fx991.core.Compat.join(",",
                    fields.subList(2, fields.size()));
            spreadsheet.fillFormula(range, formula.startsWith("=") ? formula.substring(1) : formula);
            return new CnCwModeEngine.ModeResult(range + " 已填充\n剩余 "
                    + spreadsheet.remainingBytes() + " bytes", null);
        }
        spreadsheet.recalculate();
        return new CnCwModeEngine.ModeResult("重新计算完成\n剩余 "
                + spreadsheet.remainingBytes() + " bytes", null);
    }

    private static String workflowPrompt(ApplicationMode mode, CnCwCommand command) {
        String syntax = switch (mode) {
            case STATISTICS -> command.id().equals("one")
                    ? "输入 x1,x2,…" : "输入 x1,y1,x2,y2,…";
            case DISTRIBUTION -> switch (command.id()) {
                case "normal" -> "PDF: x,μ,σ；CDF: 下限,上限,μ,σ";
                case "binomial" -> "输入 x,n,p";
                default -> "输入 x,λ";
            };
            case FUNCTION_TABLE -> command.id().equals("fg")
                    ? "输入 f(x),g(x),开始,结束,步长" : "输入 f(x),开始,结束,步长";
            case EQUATION -> switch (command.id()) {
                case "polynomial" -> "输入最高次到常数项系数";
                case "simultaneous" -> "输入 n,每行系数与常数";
                default -> "输入 f(x),初值";
            };
            case INEQUALITY -> "输入关系码(1>,2<,3≥,4≤),系数";
            case BASE_N -> "输入 " + command.label() + " 整数";
            case MATRIX -> "输入 行,列,逐行元素";
            case VECTOR -> "输入一个或两个 2D/3D 向量";
            case RATIO -> "输入三个已知数 A,B,D/C";
            case SPREADSHEET -> "输入单元格和公式";
            default -> command.description();
        };
        return command.label() + " · " + syntax;
    }

    private static String titleFor(CnCwScreen value) {
        return switch (value) {
            case HOME -> "HOME"; case CALCULATOR_ID -> "计算器 ID"; case SETTINGS -> "设置";
            case SETTINGS_INPUT_OUTPUT -> "计算设置"; case SETTINGS_FORMAT -> "显示格式";
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> "输入/输出";
            case SETTINGS_ANGLE_OPTIONS -> "角度单位";
            case SETTINGS_FIX_DIGITS -> "Fix 小数位";
            case SETTINGS_SCI_DIGITS -> "Sci 有效数字";
            case SETTINGS_DISPLAY -> "系统设置";
            case RESET_CONFIRM -> "复位确认";
            case CATALOG -> "目录"; case CATALOG_FUNCTIONS -> "函数";
            case CATALOG_NUMERIC -> "数值计算"; case CATALOG_ANGLE -> "角度与坐标";
            case CATALOG_TRIG -> "双曲与三角"; case CATALOG_ENGINEERING -> "工程符号";
            case CATALOG_CONSTANTS -> "科学常数";
            case CATALOG_CONSTANT_ITEMS -> "科学常数";
            case CATALOG_CONVERSIONS -> "单位换算";
            case CATALOG_CONVERSION_ITEMS -> "单位换算";
            case CATALOG_PROBABILITY -> "概率";
            case CATALOG_COMPLEX -> "复数";
            case CATALOG_RELATIONS -> "关系符号";
            case TOOLS -> "工具"; case TOOLS_CONVERSION -> "单位换算";
            case VARIABLES -> "变量"; case FUNCTIONS -> "f(x) / g(x)";
            case FORMAT -> "格式"; default -> value.applicationMode() == null
                    ? value.name() : value.applicationMode().chineseName();
        };
    }

    private CnCwCommand variableCommand(String name) {
        return command(name, name, formatNumber(variables.getOrDefault(name, 0.0)));
    }

    private static CnCwCommand command(String id, String label, String description) {
        return new CnCwCommand(id, label, description);
    }

    private static Token token(String text) { return new Token(text, text, false); }
    private static Token token(String display, String evaluation) {
        return new Token(display, evaluation, false);
    }
    private static Token token(String display, String evaluation, boolean binary) {
        return new Token(display, evaluation, binary);
    }

    private static boolean isEntryKey(CnCwKey key) { return switch (key) {
        case DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6,
                DIGIT_7, DIGIT_8, DIGIT_9, DOT, COMMA, ADD, PLUS, SUBTRACT,
                MINUS, MULTIPLY, DIVIDE, FRACTION, POWER, OPEN_PAREN, OPEN, CLOSE_PAREN,
                CLOSE, SIN, COS, TAN, LOG, LN, EXP, SQRT, ROOT, ABS, RECIPROCAL,
                SQUARE, CUBE, FACTORIAL, PERCENT, NPR, NCR, NEGATE, PI, E, ANS,
                RAN, VAR_A, VAR_B, VAR_C, VAR_D, VAR_E, VAR_F, VAR_X, VAR_Y,
                VAR_Z, EQUALS -> true;
        default -> false;
    }; }

    private static int wrap(int value, int size) {
        if (size <= 0) return 0;
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private static String trimDouble(double value) {
        if (!Double.isFinite(value)) return Double.toString(value);
        if (value == 0.0) return "0";
        return BigDecimal.valueOf(value).round(new java.math.MathContext(10, RoundingMode.HALF_UP))
                .stripTrailingZeros().toPlainString();
    }

    private static String separateDigits(String input) {
        int dot = input.indexOf('.');
        String whole = dot >= 0 ? input.substring(0, dot) : input;
        String fraction = dot >= 0 ? input.substring(dot) : "";
        boolean negative = whole.startsWith("-");
        String digits = negative ? whole.substring(1) : whole;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return (negative ? "-" : "") + out + fraction;
    }

    private record Token(String display, String evaluation, boolean binary) { }
    private record Navigation(CnCwScreen screen, int selectedIndex) { }
    private record HistoryEntry(List<Token> tokens, String result, String processDisplay,
                                CnCwModeEngine.ModeResult applicationResult,
                                CnCwCalculationState calculationState) { }
    private record CoordinateCall(boolean polar, String first, String second) { }
    private record FractionBounds(int templateIndex, int numeratorStart, int numeratorEnd,
                                  int denominatorStart, int denominatorEnd) { }
    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,
                                  int denominatorStart, int denominatorEnd,
                                  CnCwCursorPath.Slot slot, int offset) { }
    private record PowerBounds(int templateIndex, int baseStart, int baseEnd,
                               int exponentStart, int exponentEnd) { }
    private record PowerCursor(int templateIndex, int baseStart, int baseEnd,
                               int exponentStart, int exponentEnd,
                               CnCwCursorPath.Slot slot, int offset) { }
    private record RadicalBounds(int templateIndex, int indexStart, int indexEnd,
                                 int separatorIndex, int contentStart, int contentEnd,
                                 int closeIndex, int endExclusive) { }
    private record RadicalCursor(int templateIndex, CnCwCursorPath.Slot slot, int offset) { }
    private record FunctionArgumentBounds(int start, int end) { }
    private record FunctionBounds(int templateIndex, List<FunctionArgumentBounds> arguments,
                                  int closeIndex, int endExclusive) { }
    private record FunctionCursor(int templateIndex, int argumentIndex, int offset) { }
    private record SemanticSelectionScope(List<Integer> childPath, CnCwCursorPath.Slot slot,
                                          int slotStart, int slotEnd) { }
    private record SelectionRange(int start, int end) { }
    private record SimplificationResult(long originalNumerator, long originalDenominator,
                                        long displayNumerator, long displayDenominator,
                                        boolean canContinue, double value) { }
}
