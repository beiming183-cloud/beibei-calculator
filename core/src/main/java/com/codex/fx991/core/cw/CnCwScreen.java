package com.codex.fx991.core.cw;

import com.codex.fx991.core.mode.ApplicationMode;

/**
 * Logical screens in the CN CW shell. The renderer can choose any layout;
 * the reducer only exposes this semantic screen and its navigation path.
 */
public enum CnCwScreen {
    HOME,
    CALCULATOR_ID,

    CALCULATE,
    STATISTICS,
    DISTRIBUTION,
    SPREADSHEET,
    FUNCTION_TABLE,
    EQUATION,
    INEQUALITY,
    COMPLEX,
    BASE_N,
    MATRIX,
    VECTOR,
    RATIO,

    SETTINGS,
    SETTINGS_INPUT_OUTPUT,
    SETTINGS_INPUT_OUTPUT_OPTIONS,
    SETTINGS_ANGLE_OPTIONS,
    SETTINGS_DISPLAY,
    SETTINGS_FORMAT,
    SETTINGS_FIX_DIGITS,
    SETTINGS_SCI_DIGITS,
    RESET_CONFIRM,
    CATALOG,
    CATALOG_FUNCTIONS,
    CATALOG_NUMERIC,
    CATALOG_ANGLE,
    CATALOG_TRIG,
    CATALOG_ENGINEERING,
    CATALOG_CONSTANTS,
    CATALOG_CONSTANT_ITEMS,
    CATALOG_CONVERSIONS,
    CATALOG_CONVERSION_ITEMS,
    CATALOG_PROBABILITY,
    CATALOG_COMPLEX,
    CATALOG_RELATIONS,
    TOOLS,
    TOOLS_CONVERSION,
    VARIABLES,
    FUNCTIONS,
    FORMAT;

    /** Returns whether this is one of the ten/twelve application landing screens. */
    public boolean isApplication() {
        return switch (this) {
            case CALCULATE, STATISTICS, DISTRIBUTION, SPREADSHEET,
                    FUNCTION_TABLE, EQUATION, INEQUALITY, COMPLEX, BASE_N,
                    MATRIX, VECTOR, RATIO -> true;
            default -> false;
        };
    }

    /** Returns whether this screen is a temporary list/popup layer. */
    public boolean isPopupMenu() {
        return switch (this) {
            case CALCULATOR_ID, SETTINGS, SETTINGS_INPUT_OUTPUT, SETTINGS_DISPLAY,
                    SETTINGS_INPUT_OUTPUT_OPTIONS, SETTINGS_ANGLE_OPTIONS,
                    SETTINGS_FORMAT, SETTINGS_FIX_DIGITS, SETTINGS_SCI_DIGITS,
                    RESET_CONFIRM, CATALOG, CATALOG_FUNCTIONS,
                    CATALOG_NUMERIC, CATALOG_ANGLE, CATALOG_TRIG,
                    CATALOG_ENGINEERING, CATALOG_CONSTANTS,
                    CATALOG_CONSTANT_ITEMS, CATALOG_CONVERSIONS,
                    CATALOG_CONVERSION_ITEMS,
                    CATALOG_PROBABILITY, CATALOG_COMPLEX, TOOLS, CATALOG_RELATIONS,
                    TOOLS_CONVERSION, VARIABLES, FUNCTIONS, FORMAT -> true;
            default -> false;
        };
    }

    public static CnCwScreen forApplication(ApplicationMode mode) {
        if (mode == null) return HOME;
        return switch (mode) {
            case CALCULATE -> CALCULATE;
            case STATISTICS -> STATISTICS;
            case DISTRIBUTION -> DISTRIBUTION;
            case SPREADSHEET -> SPREADSHEET;
            case FUNCTION_TABLE -> FUNCTION_TABLE;
            case EQUATION -> EQUATION;
            case INEQUALITY -> INEQUALITY;
            case COMPLEX -> COMPLEX;
            case BASE_N -> BASE_N;
            case MATRIX -> MATRIX;
            case VECTOR -> VECTOR;
            case RATIO -> RATIO;
        };
    }

    public ApplicationMode applicationMode() {
        return switch (this) {
            case CALCULATE -> ApplicationMode.CALCULATE;
            case STATISTICS -> ApplicationMode.STATISTICS;
            case DISTRIBUTION -> ApplicationMode.DISTRIBUTION;
            case SPREADSHEET -> ApplicationMode.SPREADSHEET;
            case FUNCTION_TABLE -> ApplicationMode.FUNCTION_TABLE;
            case EQUATION -> ApplicationMode.EQUATION;
            case INEQUALITY -> ApplicationMode.INEQUALITY;
            case COMPLEX -> ApplicationMode.COMPLEX;
            case BASE_N -> ApplicationMode.BASE_N;
            case MATRIX -> ApplicationMode.MATRIX;
            case VECTOR -> ApplicationMode.VECTOR;
            case RATIO -> ApplicationMode.RATIO;
            default -> null;
        };
    }
}
