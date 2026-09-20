package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.BaseNEngine;
import com.codex.fx991.core.mode.ApplicationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure syntax validation for structured workflow inputs.
 *
 * <p>Validation deliberately compiles expressions without evaluating them.
 * This avoids rejecting valid inputs such as 1/x merely because a default
 * evaluation context would currently give x = 0.</p>
 */
public final class CnCwWorkflowValidation {
    private CnCwWorkflowValidation() { }

    public enum Status {
        EMPTY,
        VALID,
        INVALID_EXPRESSION
    }

    public static final class CellState {
        private final int row;
        private final int column;
        private final Status status;
        private final String message;

        private CellState(int row, int column, Status status, String message) {
            this.row = row;
            this.column = column;
            this.status = status;
            this.message = message == null ? "" : message;
        }

        public int row() { return row; }
        public int column() { return column; }
        public Status status() { return status; }
        public String message() { return message; }
        public boolean valid() { return status == Status.VALID; }
    }

    public static final class Report {
        private final List<CellState> cells;
        private final boolean ready;
        private final int firstProblemRow;
        private final int firstProblemColumn;

        private Report(List<CellState> cells, boolean ready,
                       int firstProblemRow, int firstProblemColumn) {
            this.cells = com.codex.fx991.core.Compat.copyList(cells);
            this.ready = ready;
            this.firstProblemRow = firstProblemRow;
            this.firstProblemColumn = firstProblemColumn;
        }

        public List<CellState> cells() { return cells; }
        public boolean ready() { return ready; }
        public boolean hasProblem() { return firstProblemRow >= 0; }
        public int firstProblemRow() { return firstProblemRow; }
        public int firstProblemColumn() { return firstProblemColumn; }

        public CellState cell(int row, int column, int columns) {
            if (row < 0 || column < 0 || columns < 1) {
                throw new IndexOutOfBoundsException(row + "," + column);
            }
            int index = row * columns + column;
            if (index < 0 || index >= cells.size()) {
                throw new IndexOutOfBoundsException(row + "," + column);
            }
            return cells.get(index);
        }
    }

    public static Report validate(CnCwWorkflowSession session) {
        if (session == null) throw new IllegalArgumentException("session");
        return validate(session.spec(), session.rows(), session.columns(), session.cells());
    }

    public static Report validate(CnCwWorkflowSession.Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot");
        return validate(snapshot.spec(), snapshot.rows(), snapshot.columns(), snapshot.cells());
    }

    public static Status validateExpression(String source) {
        if (com.codex.fx991.core.Compat.isBlank(source)) return Status.EMPTY;
        try {
            ScalarExpressionEngine.compile(source.trim());
            return Status.VALID;
        } catch (RuntimeException error) {
            return Status.INVALID_EXPRESSION;
        }
    }

    private static Report validate(CnCwWorkflowSpec.WorkflowSpec spec,
                                   int rows, int columns, List<String> cells) {
        List<CellState> states = new ArrayList<>();
        boolean ready = true;
        int firstRow = -1;
        int firstColumn = -1;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                String source = cells.get(row * columns + column);
                Status status = validateCell(spec, rows, columns, row, column, source, cells);
                String message = switch (status) {
                    case EMPTY -> "必填";
                    case VALID -> "";
                    case INVALID_EXPRESSION -> "表达式格式错误";
                };
                states.add(new CellState(row, column, status, message));
                if (status != Status.VALID) {
                    ready = false;
                    if (firstRow < 0) {
                        firstRow = row;
                        firstColumn = column;
                    }
                }
            }
        }
        return new Report(states, ready, firstRow, firstColumn);
    }

    private static Status validateCell(CnCwWorkflowSpec.WorkflowSpec spec,
                                       int rows, int columns, int row, int column,
                                       String source, List<String> cells) {
        if (spec != null && spec.mode() == ApplicationMode.BASE_N
                && CnCwBaseNWorkflow.handles(spec.commandId())) {
            if (com.codex.fx991.core.Compat.isBlank(source)) return Status.EMPTY;
            try {
                boolean baseChoice = column == 0
                        || (spec.commandId().equals("base-convert") && column == 1);
                if (baseChoice) {
                    inputBase(source);
                } else {
                    // HEX E is a digit, not a decimal exponent or a scalar variable.
                    BaseNEngine.parse(source, inputBase(cells.get(0)));
                }
                return Status.VALID;
            } catch (RuntimeException error) {
                return Status.INVALID_EXPRESSION;
            }
        }
        if (isLegacyOneVariableAggregate(spec, rows, columns, row, column, source)) {
            List<String> parts = splitTopLevel(source);
            if (parts.size() <= 1) return validateExpression(source);
            for (String part : parts) {
                if (validateExpression(part) != Status.VALID) return Status.INVALID_EXPRESSION;
            }
            return Status.VALID;
        }
        return validateExpression(source);
    }

    private static BaseNEngine.Base inputBase(String source) {
        return switch (source) {
            case "2" -> BaseNEngine.Base.BINARY;
            case "8" -> BaseNEngine.Base.OCTAL;
            case "10" -> BaseNEngine.Base.DECIMAL;
            case "16" -> BaseNEngine.Base.HEXADECIMAL;
            default -> throw new IllegalArgumentException("Unsupported base");
        };
    }

    private static boolean isLegacyOneVariableAggregate(CnCwWorkflowSpec.WorkflowSpec spec,
                                                        int rows, int columns,
                                                        int row, int column,
                                                        String source) {
        return spec != null
                && spec.mode() == com.codex.fx991.core.mode.ApplicationMode.STATISTICS
                && "one".equals(spec.commandId())
                && rows == 1 && columns == 1 && row == 0 && column == 0
                && source != null && source.indexOf(',') >= 0;
    }

    /** Splits only commas outside parentheses, preserving function arguments. */
    private static List<String> splitTopLevel(String source) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')' && depth > 0) depth--;
            else if (value == ',' && depth == 0) {
                parts.add(source.substring(start, index).trim());
                start = index + 1;
            }
        }
        parts.add(source.substring(start).trim());
        return parts;
    }
}
