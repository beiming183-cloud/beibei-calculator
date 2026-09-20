package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.ComplexValue;
import com.codex.fx991.core.math.ExactValue;

/**
 * Immutable Stage 6 calculation-session snapshot.
 *
 * <p>The first migration step deliberately mirrors the legacy machine fields.
 * Later steps make this type the write-side source of truth while retaining the
 * old UiState getters as compatibility views.</p>
 */
public final class CnCwCalculationState {
    public enum Phase {
        EDITING,
        RESULT,
        ERROR
    }

    public enum ResultKind {
        NONE,
        TEXT,
        SCALAR,
        EXACT,
        COMPLEX,
        APPLICATION
    }

    private final Phase phase;
    private final ResultKind resultKind;
    private final String display;
    private final Double scalarValue;
    private final ExactValue exactValue;
    private final ComplexValue complexValue;
    private final CnCwModeEngine.ModeResult applicationResult;
    private final CalculationError error;
    private final int errorCursor;

    private CnCwCalculationState(Phase phase,
                                 ResultKind resultKind,
                                 String display,
                                 Double scalarValue,
                                 ExactValue exactValue,
                                 ComplexValue complexValue,
                                 CnCwModeEngine.ModeResult applicationResult,
                                 CalculationError error,
                                 int errorCursor) {
        this.phase = phase;
        this.resultKind = resultKind;
        this.display = display == null ? "" : display;
        this.scalarValue = scalarValue;
        this.exactValue = exactValue;
        this.complexValue = complexValue;
        this.applicationResult = applicationResult;
        this.error = error;
        this.errorCursor = Math.max(0, errorCursor);
    }

    public static CnCwCalculationState editing() {
        return new CnCwCalculationState(Phase.EDITING, ResultKind.NONE, "",
                null, null, null, null, null, 0);
    }

    public static CnCwCalculationState textResult(String display) {
        return new CnCwCalculationState(Phase.RESULT, ResultKind.TEXT, display,
                null, null, null, null, null, 0);
    }

    public static CnCwCalculationState scalarResult(String display, double value) {
        return new CnCwCalculationState(Phase.RESULT, ResultKind.SCALAR, display,
                value, null, null, null, null, 0);
    }

    public static CnCwCalculationState exactResult(String display, double value,
                                                   ExactValue exactValue) {
        return new CnCwCalculationState(Phase.RESULT, ResultKind.EXACT, display,
                value, exactValue, null, null, null, 0);
    }

    public static CnCwCalculationState complexResult(String display, ComplexValue value) {
        return new CnCwCalculationState(Phase.RESULT, ResultKind.COMPLEX, display,
                value == null ? null : value.real(), null, value, null, null, 0);
    }

    public static CnCwCalculationState applicationResult(String display,
                                                         CnCwModeEngine.ModeResult result) {
        return new CnCwCalculationState(Phase.RESULT, ResultKind.APPLICATION, display,
                result == null ? null : result.primaryValue(), null, null, result, null, 0);
    }

    public static CnCwCalculationState error(String display, CalculationError error,
                                             int errorCursor) {
        return new CnCwCalculationState(Phase.ERROR, ResultKind.NONE, display,
                null, null, null, null, error, errorCursor);
    }

    public Phase phase() { return phase; }

    /** Changes presentation without replacing this result's value with the live Ans register. */
    public CnCwCalculationState withDisplay(String nextDisplay) {
        return new CnCwCalculationState(phase, resultKind, nextDisplay, scalarValue,
                exactValue, complexValue, applicationResult, error, errorCursor);
    }
    public ResultKind resultKind() { return resultKind; }
    public String display() { return display; }
    public Double scalarValue() { return scalarValue; }
    public ExactValue exactValue() { return exactValue; }
    public ComplexValue complexValue() { return complexValue; }
    public CnCwModeEngine.ModeResult applicationResult() { return applicationResult; }
    public CalculationError error() { return error; }
    public int errorCursor() { return errorCursor; }

    public boolean isEditing() { return phase == Phase.EDITING; }
    public boolean isResult() { return phase == Phase.RESULT; }
    public boolean isError() { return phase == Phase.ERROR; }
    public boolean hasApplicationResult() { return applicationResult != null; }
}
