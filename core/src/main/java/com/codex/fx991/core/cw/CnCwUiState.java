package com.codex.fx991.core.cw;

import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rendering snapshot for the CN CW interaction shell.
 *
 * <p>The Android layer only reads this object. It never owns menu selection,
 * expression editing, modifier, application, or result state.</p>
 */
public final class CnCwUiState {
    /** Number of application tiles visible in the CW home viewport. */
    public static final int HOME_VIEWPORT_SIZE = 6;
    /** Home tiles are laid out in two rows of three. */
    public static final int HOME_VIEWPORT_COLUMNS = 3;
    private final CnCwModel model;
    private final CnCwScreen screen;
    private final ApplicationMode application;
    private final int selectedIndex;
    private final List<CnCwCommand> menuItems;
    private final List<CnCwCommand> homeItems;
    private final List<CnCwCommand> modeCommands;
    private final String expression;
    private final String displayText;
    private final CnCwExpressionNode naturalExpression;
    private final int cursor;
    /** Stage 3 compatibility view of the cursor as a semantic editor position. */
    private final CnCwCursorPath semanticCursor;
    /** Editable semantic regions used by geometry-aware platform hit testing. */
    private final List<CnCwSemanticSpan> semanticSpans;
    private final int selectionStart;
    private final int selectionEnd;
    /** Stage 3 semantic facade for the selection's directional anchor. */
    private final CnCwCursorPath semanticSelectionAnchor;
    /** Stage 3 semantic facade for the selection's active focus. */
    private final CnCwCursorPath semanticSelectionFocus;
    private final String result;
    /** Structured application result; null for ordinary/text-only results. */
    private final CnCwModeEngine.ModeResult applicationResult;
    /** Stage 5 immutable input-table snapshot; null for the legacy editor. */
    private final CnCwWorkflowSession.Snapshot workflowInput;
    /** Stage 6 typed calculation phase/result/error snapshot. */
    private final CnCwCalculationState calculationState;
    private final double ans;
    private final boolean hasAns;
    private final String status;
    private final CnCwSettings settings;
    private final boolean shiftArmed;
    private final boolean poweredOn;
    private final boolean overwriteMode;
    private final boolean verificationMode;
    private final boolean engineeringMode;
    private final boolean statementSequenceActive;
    private final boolean historyPrevious;
    private final boolean historyNext;
    private final boolean applicationLanding;
    private final boolean resultShown;
    private final boolean spreadsheetGrid;
    private final int spreadsheetRow;
    private final int spreadsheetColumn;
    private final List<String> spreadsheetCells;
    private final String spreadsheetFormula;
    private final List<CnCwScreen> navigationPath;

    CnCwUiState(CnCwModel model,
                CnCwScreen screen,
                ApplicationMode application,
                int selectedIndex,
                List<CnCwCommand> menuItems,
                List<CnCwCommand> homeItems,
                List<CnCwCommand> modeCommands,
                String expression,
                String displayText,
                CnCwExpressionNode naturalExpression,
                int cursor,
                CnCwCursorPath semanticCursor,
                List<CnCwSemanticSpan> semanticSpans,
                int selectionStart,
                int selectionEnd,
                CnCwCursorPath semanticSelectionAnchor,
                CnCwCursorPath semanticSelectionFocus,
                String result,
                CnCwModeEngine.ModeResult applicationResult,
                CnCwWorkflowSession.Snapshot workflowInput,
                CnCwCalculationState calculationState,
                double ans,
                boolean hasAns,
                String status,
                CnCwSettings settings,
                boolean shiftArmed,
                boolean poweredOn,
                boolean overwriteMode,
                boolean verificationMode,
                boolean engineeringMode,
                boolean statementSequenceActive,
                boolean historyPrevious,
                boolean historyNext,
                boolean applicationLanding,
                boolean resultShown,
                boolean spreadsheetGrid,
                int spreadsheetRow,
                int spreadsheetColumn,
                List<String> spreadsheetCells,
                String spreadsheetFormula,
                List<CnCwScreen> navigationPath) {
        this.model = Objects.requireNonNull(model, "model");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.application = application;
        this.selectedIndex = Math.max(0, selectedIndex);
        this.menuItems = com.codex.fx991.core.Compat.copyList(menuItems);
        this.homeItems = com.codex.fx991.core.Compat.copyList(homeItems);
        this.modeCommands = com.codex.fx991.core.Compat.copyList(modeCommands);
        this.expression = expression == null ? "" : expression;
        this.displayText = displayText == null ? "" : displayText;
        this.naturalExpression = Objects.requireNonNull(naturalExpression, "naturalExpression");
        this.cursor = Math.max(0, cursor);
        this.semanticCursor = Objects.requireNonNull(semanticCursor, "semanticCursor");
        this.semanticSpans = com.codex.fx991.core.Compat.copyList(semanticSpans);
        this.selectionStart = Math.max(0, selectionStart);
        this.selectionEnd = Math.max(this.selectionStart, selectionEnd);
        this.semanticSelectionAnchor = Objects.requireNonNull(semanticSelectionAnchor,
                "semanticSelectionAnchor");
        this.semanticSelectionFocus = Objects.requireNonNull(semanticSelectionFocus,
                "semanticSelectionFocus");
        this.result = result == null ? "" : result;
        this.applicationResult = applicationResult;
        this.workflowInput = workflowInput;
        this.calculationState = Objects.requireNonNull(calculationState, "calculationState");
        this.ans = ans;
        this.hasAns = hasAns;
        this.status = status == null ? "" : status;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.shiftArmed = shiftArmed;
        this.poweredOn = poweredOn;
        this.overwriteMode = overwriteMode;
        this.verificationMode = verificationMode;
        this.engineeringMode = engineeringMode;
        this.statementSequenceActive = statementSequenceActive;
        this.historyPrevious = historyPrevious;
        this.historyNext = historyNext;
        this.applicationLanding = applicationLanding;
        this.resultShown = resultShown;
        this.spreadsheetGrid = spreadsheetGrid;
        this.spreadsheetRow = Math.max(0, spreadsheetRow);
        this.spreadsheetColumn = Math.max(0, spreadsheetColumn);
        this.spreadsheetCells = com.codex.fx991.core.Compat.copyList(spreadsheetCells);
        this.spreadsheetFormula = spreadsheetFormula == null ? "" : spreadsheetFormula;
        this.navigationPath = com.codex.fx991.core.Compat.copyList(navigationPath);
    }

    public CnCwModel model() { return model; }
    public CnCwScreen screen() { return screen; }
    public ApplicationMode application() { return application; }
    public int selectedIndex() { return selectedIndex; }
    public List<CnCwCommand> menuItems() { return menuItems; }
    public List<CnCwCommand> homeItems() { return homeItems; }
    /** Alias exposing the model-gated application list to newer renderers. */
    public List<CnCwCommand> homeApplications() { return homeItems; }
    /** Zero-based six-tile page containing the current selection. */
    public int homePageIndex() {
        return homeItems.isEmpty() ? 0 : selectedIndex / HOME_VIEWPORT_SIZE;
    }
    /** Short alias used by lightweight renderers. */
    public int homePage() { return homePageIndex(); }
    /** Number of six-tile pages for this product profile. */
    public int homePageCount() {
        return (homeItems.size() + HOME_VIEWPORT_SIZE - 1) / HOME_VIEWPORT_SIZE;
    }
    /** Inclusive start index of the current six-tile viewport. */
    public int homeViewportStart() { return homePageIndex() * HOME_VIEWPORT_SIZE; }
    public int homePageStart() { return homeViewportStart(); }
    /** Exclusive end index of the current viewport. */
    public int homeViewportEnd() {
        return Math.min(homeItems.size(), homeViewportStart() + HOME_VIEWPORT_SIZE);
    }
    public int homePageEndExclusive() { return homeViewportEnd(); }
    /** Six-tile page list, copied and immutable like {@link #homeItems()}. */
    public List<CnCwCommand> homeVisibleItems() {
        return homeItems.subList(homeViewportStart(), homeViewportEnd());
    }
    public List<CnCwCommand> homeViewport() { return homeVisibleItems(); }
    /** Compatibility aliases for adapters that call the page an offset/window. */
    public int homePageSize() { return HOME_VIEWPORT_SIZE; }
    public int homePageOffset() { return homeViewportStart(); }
    public int homeSelectedIndex() { return homeItems.isEmpty() ? 0 : selectedIndex % HOME_VIEWPORT_SIZE; }
    public boolean homeHasNextPage() { return homePageIndex() + 1 < homePageCount(); }
    public boolean homeHasPreviousPage() { return homePageIndex() > 0; }
    public List<CnCwCommand> modeCommands() { return modeCommands; }
    public String expression() { return expression; }
    public String displayText() { return displayText; }
    public CnCwExpressionNode naturalExpression() { return naturalExpression; }
    public int cursor() { return cursor; }
    /**
     * Semantic cursor facade introduced in Stage 3.
     *
     * <p>At the bootstrap step this mirrors the existing top-level token
     * boundary. Fraction/power/root/function work will progressively replace
     * the root-only path with nested slots without removing {@link #cursor()}.
     * </p>
     */
    public CnCwCursorPath semanticCursor() { return semanticCursor; }
    /** Semantic editable regions for platform hit testing; immutable snapshot. */
    public List<CnCwSemanticSpan> semanticSpans() { return semanticSpans; }
    public boolean hasSelection() { return selectionEnd > selectionStart; }
    public int selectionStart() { return selectionStart; }
    public int selectionEnd() { return selectionEnd; }
    public CnCwCursorPath semanticSelectionAnchor() { return semanticSelectionAnchor; }
    public CnCwCursorPath semanticSelectionFocus() { return semanticSelectionFocus; }
    public String result() { return result; }
    public CnCwModeEngine.ModeResult applicationResult() { return applicationResult; }
    public CnCwWorkflowSession.Snapshot workflowInput() { return workflowInput; }
    public boolean hasWorkflowInput() { return workflowInput != null; }
    public CnCwCalculationState calculationState() { return calculationState; }
    public boolean hasStructuredApplicationResult() {
        return applicationResult != null
                && applicationResult.layout() != CnCwModeEngine.ResultLayout.TEXT;
    }
    public double ans() { return ans; }
    public boolean hasAns() { return hasAns; }
    public String status() { return status; }
    public CnCwSettings settings() { return settings; }
    public boolean shiftArmed() { return shiftArmed; }
    public boolean poweredOn() { return poweredOn; }
    public boolean overwriteMode() { return overwriteMode; }
    public boolean verificationMode() { return verificationMode; }
    public boolean engineeringMode() { return engineeringMode; }
    public boolean statementSequenceActive() { return statementSequenceActive; }
    public boolean historyPrevious() { return historyPrevious; }
    public boolean historyNext() { return historyNext; }
    public boolean applicationLanding() { return applicationLanding; }
    public boolean resultShown() { return resultShown; }
    public boolean spreadsheetGrid() { return spreadsheetGrid; }
    public int spreadsheetRow() { return spreadsheetRow; }
    public int spreadsheetColumn() { return spreadsheetColumn; }
    public List<String> spreadsheetCells() { return spreadsheetCells; }
    public String spreadsheetFormula() { return spreadsheetFormula; }
    public List<CnCwScreen> navigationPath() { return navigationPath; }
}
