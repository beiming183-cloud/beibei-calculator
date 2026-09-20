package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable core-owned editor state for one structured application workflow.
 *
 * <p>Every Stage 5 structured editor uses the same row-major state. Evaluation
 * still delegates to CnCwModeEngine, so input UX can evolve without duplicating
 * any numerical algorithm.</p>
 */
public final class CnCwWorkflowSession {
    /** Immutable renderer/evaluation snapshot; never exposes the live cells list. */
    public static final class Snapshot {
        private final CnCwWorkflowSpec.WorkflowSpec spec;
        private final int rows;
        private final int columns;
        private final List<String> cells;
        private final int selectedRow;
        private final int selectedColumn;
        private final boolean complete;
        private final List<CnCwWorkflowAction> actions;

        private Snapshot(CnCwWorkflowSpec.WorkflowSpec spec, int rows, int columns,
                         List<String> cells, int selectedRow, int selectedColumn,
                         boolean complete, List<CnCwWorkflowAction> actions) {
            this.spec = spec;
            this.rows = rows;
            this.columns = columns;
            this.cells = com.codex.fx991.core.Compat.copyList(cells);
            this.selectedRow = selectedRow;
            this.selectedColumn = selectedColumn;
            this.complete = complete;
            this.actions = com.codex.fx991.core.Compat.copyList(actions);
        }

        public CnCwWorkflowSpec.WorkflowSpec spec() { return spec; }
        public int rows() { return rows; }
        public int columns() { return columns; }
        public List<String> cells() { return cells; }
        public int selectedRow() { return selectedRow; }
        public int selectedColumn() { return selectedColumn; }
        public boolean complete() { return complete; }
        public List<CnCwWorkflowAction> actions() { return actions; }
        public String cell(int row, int column) {
            if (row < 0 || row >= rows || column < 0 || column >= columns) {
                throw new IndexOutOfBoundsException(row + "," + column);
            }
            return cells.get(row * columns + column);
        }
        public CnCwWorkflowSpec.FieldSpec field(int row, int column) {
            if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS
                    || row != 0 || column < 0 || column >= spec.fields().size()) return null;
            return spec.fields().get(column);
        }
        public boolean isChoiceCell(int row, int column) {
            CnCwWorkflowSpec.FieldSpec field = field(row, column);
            return field != null && field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE;
        }
        public String displayCell(int row, int column) {
            CnCwWorkflowSpec.FieldSpec field = field(row, column);
            String raw = cell(row, column);
            return field == null ? raw : field.displayValue(raw);
        }
    }

    private final CnCwWorkflowSpec.WorkflowSpec spec;
    private int rows;
    private int columns;
    private final List<String> cells = new ArrayList<>();
    private int selectedRow;
    private int selectedColumn;

    private CnCwWorkflowSession(CnCwWorkflowSpec.WorkflowSpec spec,
                                int rows, int columns) {
        if (spec == null) throw new IllegalArgumentException("spec");
        this.spec = spec;
        resize(rows, columns);
        initializeChoiceDefaults();
    }

    public static CnCwWorkflowSession create(CnCwWorkflowSpec.WorkflowSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec");
        int rows;
        int columns;
        switch (spec.layout()) {
            case SERIES -> {
                rows = spec.minRows();
                columns = 1;
            }
            case PAIRED_SERIES -> {
                rows = spec.minRows();
                columns = spec.fields().size();
            }
            case FIXED_FIELDS -> {
                rows = 1;
                columns = spec.fields().size();
            }
            case COEFFICIENTS -> {
                rows = spec.minRows();
                columns = spec.minColumns();
            }
            case GRID, VECTOR_SET -> {
                rows = spec.minRows();
                columns = spec.minColumns();
            }
            default -> throw new IllegalArgumentException("Unsupported layout");
        }
        return new CnCwWorkflowSession(spec, rows, columns);
    }

    public CnCwWorkflowSession copy() {
        CnCwWorkflowSession copy = new CnCwWorkflowSession(spec, rows, columns);
        copy.cells.clear();
        copy.cells.addAll(cells);
        copy.selectedRow = selectedRow;
        copy.selectedColumn = selectedColumn;
        return copy;
    }

    public Snapshot snapshot() {
        return new Snapshot(spec, rows, columns, cells,
                selectedRow, selectedColumn, isComplete(), actions());
    }

    public CnCwWorkflowSpec.WorkflowSpec spec() { return spec; }
    public int rows() { return rows; }
    public int columns() { return columns; }
    public int selectedRow() { return selectedRow; }
    public int selectedColumn() { return selectedColumn; }

    public String cell(int row, int column) {
        return cells.get(index(row, column));
    }

    public void setCell(int row, int column, String value) {
        cells.set(index(row, column), value == null ? "" : value.trim());
    }

    public String selectedCell() {
        return cell(selectedRow, selectedColumn);
    }

    public void setSelectedCell(String value) {
        setCell(selectedRow, selectedColumn, value);
    }

    public CnCwWorkflowSpec.FieldSpec selectedField() {
        return fieldAt(selectedRow, selectedColumn);
    }

    public boolean selectedIsChoice() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        return field != null && field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE;
    }

    public String selectedDisplayCell() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        return field == null ? selectedCell() : field.displayValue(selectedCell());
    }

    public boolean cycleSelectedChoice(int delta) {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        if (field == null || field.kind() != CnCwWorkflowSpec.FieldKind.CHOICE) return false;
        List<CnCwWorkflowSpec.ChoiceOption> choices = field.choices();
        String current = selectedCell();
        int index = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).value().equals(current)) {
                index = i;
                break;
            }
        }
        int next = Math.floorMod(index + delta, choices.size());
        setSelectedCell(choices.get(next).value());
        return next != index;
    }

    public boolean resetSelectedChoice() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        if (field == null || field.kind() != CnCwWorkflowSpec.FieldKind.CHOICE) return false;
        setSelectedCell(field.defaultValue());
        return true;
    }

    public boolean selectCell(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) return false;
        boolean changed = row != selectedRow || column != selectedColumn;
        selectedRow = row;
        selectedColumn = column;
        return changed;
    }

    public boolean move(int rowDelta, int columnDelta) {
        return selectCell(clamp(selectedRow + rowDelta, 0, rows - 1),
                clamp(selectedColumn + columnDelta, 0, columns - 1));
    }

    public boolean selectFirstBlank() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (com.codex.fx991.core.Compat.isBlank(cell(row, column))) {
                    selectCell(row, column);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean appendRow() {
        if (isSimultaneous()) return false;
        if (rows >= spec.maxRows()) return false;
        int oldRows = rows;
        resize(rows + 1, columns);
        selectedRow = oldRows;
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    public boolean removeSelectedRow() {
        if (isSimultaneous()) return false;
        if (rows <= spec.minRows()) return false;
        int remove = selectedRow;
        for (int column = columns - 1; column >= 0; column--) {
            cells.remove(remove * columns + column);
        }
        rows--;
        selectedRow = Math.min(selectedRow, rows - 1);
        return true;
    }

    public boolean resizeGrid(int newRows, int newColumns) {
        if (newRows < spec.minRows() || newRows > spec.maxRows()
                || newColumns < spec.minColumns() || newColumns > spec.maxColumns()) {
            return false;
        }
        if (isSimultaneous()) {
            if (newColumns != newRows + 1) return false;
            return resizeSimultaneous(newRows);
        }
        if (isPolynomial()) {
            if (newColumns != 1) return false;
            return resizePolynomial(newRows);
        }
        resize(newRows, newColumns);
        selectedRow = Math.min(selectedRow, rows - 1);
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    /** Changes a 2–4 variable simultaneous system to n rows × (n+1) columns. */
    public boolean setEquationDimension(int dimension) {
        if (!isSimultaneous()) return false;
        return resizeGrid(dimension, dimension + 1);
    }

    /**
     * Current workflow capabilities. Renderers may expose these directly, but
     * should not infer them again from layout names or keyboard shortcuts.
     */
    public List<CnCwWorkflowAction> actions() {
        List<CnCwWorkflowAction> values = new ArrayList<>();
        switch (spec.layout()) {
            case SERIES, PAIRED_SERIES -> {
                values.add(action(CnCwWorkflowAction.Type.ADD_ROW, "新增数据",
                        rows < spec.maxRows()));
                values.add(action(CnCwWorkflowAction.Type.REMOVE_ROW, "删除当前行",
                        rows > spec.minRows()));
            }
            case COEFFICIENTS -> {
                String decrease = isSimultaneous() ? "减少元数" : "降低阶数";
                String increase = isSimultaneous() ? "增加元数" : "提高阶数";
                values.add(action(CnCwWorkflowAction.Type.DECREASE_ROWS, decrease,
                        rows > spec.minRows()));
                values.add(action(CnCwWorkflowAction.Type.INCREASE_ROWS, increase,
                        rows < spec.maxRows()));
            }
            case GRID -> {
                addDimensionActions(values, "行", "列");
            }
            case VECTOR_SET -> {
                addDimensionActions(values, "向量", "维度");
            }
            case FIXED_FIELDS -> { }
        }
        values.add(action(CnCwWorkflowAction.Type.EXECUTE, "计算",
                CnCwWorkflowValidation.validate(this).ready()));
        values.add(action(CnCwWorkflowAction.Type.BACK, "返回", true));
        return com.codex.fx991.core.Compat.copyList(values);
    }

    /** Applies one session-owned mutation from the same protocol published to UI. */
    public boolean applyAction(CnCwWorkflowAction.Type type) {
        if (type == null || !actionEnabled(type)) return false;
        return switch (type) {
            case ADD_ROW -> appendRow();
            case REMOVE_ROW -> removeSelectedRow();
            case DECREASE_ROWS -> resizeRows(rows - 1);
            case INCREASE_ROWS -> resizeRows(rows + 1);
            case DECREASE_COLUMNS -> resizeGrid(rows, columns - 1);
            case INCREASE_COLUMNS -> resizeGrid(rows, columns + 1);
            case EXECUTE, BACK -> false;
        };
    }

    public boolean isComplete() {
        for (String cell : cells) {
            if (com.codex.fx991.core.Compat.isBlank(cell)) return false;
        }
        return true;
    }

    /**
     * Compatibility serialization for the existing evaluator. Statistics,
     * polynomial, SOLVE and vectors are row-major. Matrix shape is prefixed as
     * rows,columns; simultaneous equations are prefixed by their dimension.
     */
    public String legacySource() {
        if (!isComplete()) throw new IllegalStateException("Workflow input incomplete");
        List<String> values = new ArrayList<>();
        if (spec.layout() == CnCwWorkflowSpec.InputLayout.GRID) {
            values.add(Integer.toString(rows));
            values.add(Integer.toString(columns));
        } else if (isSimultaneous()) {
            values.add(Integer.toString(rows));
        }
        values.addAll(cells);
        return com.codex.fx991.core.Compat.join(",", values);
    }

    public CnCwModeEngine.ModeResult evaluate(ScalarExpressionEngine.EvaluationContext context) {
        return CnCwModeEngine.evaluate(spec.mode(), spec.commandId(), legacySource(), context);
    }

    public List<String> cells() {
        return com.codex.fx991.core.Compat.copyList(cells);
    }

    private void addDimensionActions(List<CnCwWorkflowAction> values,
                                     String rowNoun, String columnNoun) {
        values.add(action(CnCwWorkflowAction.Type.DECREASE_ROWS, "减少" + rowNoun,
                rows > spec.minRows()));
        values.add(action(CnCwWorkflowAction.Type.INCREASE_ROWS, "增加" + rowNoun,
                rows < spec.maxRows()));
        values.add(action(CnCwWorkflowAction.Type.DECREASE_COLUMNS, "减少" + columnNoun,
                columns > spec.minColumns()));
        values.add(action(CnCwWorkflowAction.Type.INCREASE_COLUMNS, "增加" + columnNoun,
                columns < spec.maxColumns()));
    }

    private CnCwWorkflowAction action(CnCwWorkflowAction.Type type,
                                      String label, boolean enabled) {
        return new CnCwWorkflowAction(type, label, enabled);
    }

    private boolean actionEnabled(CnCwWorkflowAction.Type type) {
        for (CnCwWorkflowAction action : actions()) {
            if (action.type() == type) return action.enabled();
        }
        return false;
    }

    /** Keeps a_k attached to the same power when the polynomial degree changes. */
    private boolean resizePolynomial(int newRows) {
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldSelectedRow = selectedRow;
        int oldToNewShift = newRows - oldRows;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            int oldRow = row - oldToNewShift;
            cells.add(oldRow >= 0 && oldRow < oldRows ? previous.get(oldRow) : "");
        }
        rows = newRows;
        columns = 1;
        selectedRow = clamp(oldSelectedRow + oldToNewShift, 0, rows - 1);
        selectedColumn = 0;
        return true;
    }

    /** Keeps each variable coefficient and the augmented RHS column in its semantic slot. */
    private boolean resizeSimultaneous(int newRows) {
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldColumns = columns;
        int oldSelectedRow = selectedRow;
        int oldSelectedColumn = selectedColumn;
        int newColumns = newRows + 1;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            for (int column = 0; column < newColumns; column++) {
                String value = "";
                if (row < oldRows) {
                    if (column < Math.min(oldRows, newRows)) {
                        value = previous.get(row * oldColumns + column);
                    } else if (column == newRows) {
                        value = previous.get(row * oldColumns + oldColumns - 1);
                    }
                }
                cells.add(value);
            }
        }
        rows = newRows;
        columns = newColumns;
        selectedRow = clamp(oldSelectedRow, 0, rows - 1);
        selectedColumn = oldSelectedColumn == oldColumns - 1
                ? columns - 1 : clamp(oldSelectedColumn, 0, rows - 1);
        return true;
    }

    private boolean resizeRows(int newRows) {
        if (isSimultaneous()) return setEquationDimension(newRows);
        return resizeGrid(newRows, columns);
    }

    private CnCwWorkflowSpec.FieldSpec fieldAt(int row, int column) {
        if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS
                || row != 0 || column < 0 || column >= spec.fields().size()) return null;
        return spec.fields().get(column);
    }

    private void initializeChoiceDefaults() {
        if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS) return;
        for (int column = 0; column < Math.min(columns, spec.fields().size()); column++) {
            CnCwWorkflowSpec.FieldSpec field = spec.fields().get(column);
            if (field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE
                    && com.codex.fx991.core.Compat.isBlank(cell(0, column))) {
                setCell(0, column, field.defaultValue());
            }
        }
    }

    private boolean isPolynomial() {
        return spec.mode() == ApplicationMode.EQUATION
                && "polynomial".equals(spec.commandId());
    }

    private boolean isSimultaneous() {
        return spec.mode() == ApplicationMode.EQUATION
                && "simultaneous".equals(spec.commandId());
    }

    private void resize(int newRows, int newColumns) {
        if (newRows < 1 || newColumns < 1) throw new IllegalArgumentException("shape");
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldColumns = columns;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            for (int column = 0; column < newColumns; column++) {
                String value = row < oldRows && column < oldColumns
                        ? previous.get(row * oldColumns + column) : "";
                cells.add(value);
            }
        }
        rows = newRows;
        columns = newColumns;
    }

    private int index(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IndexOutOfBoundsException(row + "," + column);
        }
        return row * columns + column;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
