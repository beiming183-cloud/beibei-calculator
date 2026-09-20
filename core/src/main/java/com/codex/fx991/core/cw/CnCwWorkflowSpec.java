package com.codex.fx991.core.cw;

import com.codex.fx991.core.mode.ApplicationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Core-owned input contract for structured application workflows.
 *
 * <p>The legacy comma-separated bridge remains valid, but Android can now ask
 * the core what shape and fields a workflow expects instead of parsing prompt
 * strings.  Later Stage 5 steps attach editable state to these immutable specs.</p>
 */
public final class CnCwWorkflowSpec {
    private CnCwWorkflowSpec() { }

    public enum InputLayout {
        FIXED_FIELDS,
        SERIES,
        PAIRED_SERIES,
        COEFFICIENTS,
        GRID,
        VECTOR_SET
    }

    public enum FieldKind {
        EXPRESSION,
        INTEGER,
        DIMENSION,
        CHOICE
    }

    /** Serialized evaluator value paired with a human-readable field label. */
    public static final class ChoiceOption {
        private final String value;
        private final String label;

        public ChoiceOption(String value, String label) {
            if (com.codex.fx991.core.Compat.isBlank(value)) throw new IllegalArgumentException("value");
            if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
            this.value = value;
            this.label = label;
        }

        public String value() { return value; }
        public String label() { return label; }
    }

    public static final class FieldSpec {
        private final String id;
        private final String label;
        private final FieldKind kind;
        private final boolean required;
        private final List<ChoiceOption> choices;

        public FieldSpec(String id, String label, FieldKind kind, boolean required) {
            this(id, label, kind, required, com.codex.fx991.core.Compat.list());
        }

        public FieldSpec(String id, String label, FieldKind kind, boolean required,
                         List<ChoiceOption> choices) {
            if (com.codex.fx991.core.Compat.isBlank(id)) throw new IllegalArgumentException("id");
            if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
            if (kind == null) throw new IllegalArgumentException("kind");
            if (choices == null) throw new IllegalArgumentException("choices");
            if (kind == FieldKind.CHOICE && choices.isEmpty()) {
                throw new IllegalArgumentException("choice options");
            }
            if (kind != FieldKind.CHOICE && !choices.isEmpty()) {
                throw new IllegalArgumentException("choices only valid for CHOICE");
            }
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.required = required;
            this.choices = com.codex.fx991.core.Compat.copyList(choices);
        }

        public String id() { return id; }
        public String label() { return label; }
        public FieldKind kind() { return kind; }
        public boolean required() { return required; }
        public List<ChoiceOption> choices() { return choices; }
        public String defaultValue() {
            return kind == FieldKind.CHOICE ? choices.get(0).value() : "";
        }
        public String displayValue(String raw) {
            if (kind != FieldKind.CHOICE) return raw == null ? "" : raw;
            String value = com.codex.fx991.core.Compat.isBlank(raw) ? defaultValue() : raw;
            for (ChoiceOption choice : choices) {
                if (choice.value().equals(value)) return choice.label();
            }
            return value;
        }
    }

    public static final class WorkflowSpec {
        private final ApplicationMode mode;
        private final String commandId;
        private final String title;
        private final InputLayout layout;
        private final List<FieldSpec> fields;
        private final int minRows;
        private final int maxRows;
        private final int minColumns;
        private final int maxColumns;

        private WorkflowSpec(ApplicationMode mode, String commandId, String title,
                             InputLayout layout, List<FieldSpec> fields,
                             int minRows, int maxRows, int minColumns, int maxColumns) {
            if (mode == null) throw new IllegalArgumentException("mode");
            if (com.codex.fx991.core.Compat.isBlank(commandId)) throw new IllegalArgumentException("commandId");
            if (com.codex.fx991.core.Compat.isBlank(title)) throw new IllegalArgumentException("title");
            if (layout == null) throw new IllegalArgumentException("layout");
            if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("fields");
            if (minRows < 1 || maxRows < minRows) throw new IllegalArgumentException("rows");
            if (minColumns < 1 || maxColumns < minColumns) throw new IllegalArgumentException("columns");
            this.mode = mode;
            this.commandId = commandId;
            this.title = title;
            this.layout = layout;
            this.fields = com.codex.fx991.core.Compat.copyList(fields);
            this.minRows = minRows;
            this.maxRows = maxRows;
            this.minColumns = minColumns;
            this.maxColumns = maxColumns;
        }

        public ApplicationMode mode() { return mode; }
        public String commandId() { return commandId; }
        public String title() { return title; }
        public InputLayout layout() { return layout; }
        public List<FieldSpec> fields() { return fields; }
        public int minRows() { return minRows; }
        public int maxRows() { return maxRows; }
        public int minColumns() { return minColumns; }
        public int maxColumns() { return maxColumns; }
        public boolean hasVariableRows() { return minRows != maxRows; }
        public boolean hasVariableColumns() { return minColumns != maxColumns; }
    }

    public static WorkflowSpec forCommand(ApplicationMode mode, String commandId) {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (com.codex.fx991.core.Compat.isBlank(commandId)) throw new IllegalArgumentException("commandId");
        return switch (mode) {
            case STATISTICS -> statistics(commandId);
            case FUNCTION_TABLE -> functionTable(commandId);
            case EQUATION -> equation(commandId);
            case INEQUALITY -> inequality(commandId);
            case BASE_N -> baseN(commandId);
            case MATRIX -> matrix(commandId);
            case VECTOR -> vector(commandId);
            case RATIO -> ratio(commandId);
            default -> null;
        };
    }

    private static WorkflowSpec statistics(String commandId) {
        if (commandId.equals("one")) {
            return spec(ApplicationMode.STATISTICS, commandId, "一元统计",
                    InputLayout.SERIES, fields(field("x", "x", FieldKind.EXPRESSION)),
                    1, 999, 1, 1);
        }
        if (commandId.equals("one-freq")) {
            return spec(ApplicationMode.STATISTICS, commandId, "一元统计（频数）",
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("frequency", "频数", FieldKind.EXPRESSION)),
                    1, 999, 2, 2);
        }
        if (commandId.equals("two-freq")) {
            return spec(ApplicationMode.STATISTICS, commandId, "双变量统计（频数）",
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("y", "y", FieldKind.EXPRESSION),
                            field("frequency", "频数", FieldKind.EXPRESSION)),
                    2, 999, 3, 3);
        }
        boolean frequency = commandId.endsWith("-freq");
        String baseCommand = frequency
                ? commandId.substring(0, commandId.length() - "-freq".length()) : commandId;
        boolean regression = baseCommand.equals("regression") || baseCommand.startsWith("reg-");
        if (commandId.equals("two") || regression) {
            int minRows = baseCommand.equals("reg-quadratic") ? 3 : 2;
            List<FieldSpec> fields = new ArrayList<>();
            fields.add(field("x", "x", FieldKind.EXPRESSION));
            fields.add(field("y", "y", FieldKind.EXPRESSION));
            if (frequency) fields.add(field("frequency", "频数", FieldKind.EXPRESSION));
            return spec(ApplicationMode.STATISTICS, commandId,
                    commandId.equals("two") ? "双变量统计"
                            : regressionTitle(baseCommand) + (frequency ? "（频数）" : ""),
                    InputLayout.PAIRED_SERIES, fields,
                    minRows, 999, fields.size(), fields.size());
        }
        return null;
    }

    private static String regressionTitle(String commandId) {
        return switch (commandId) {
            case "reg-quadratic" -> "二次回归";
            case "reg-logarithmic" -> "对数回归";
            case "reg-e-exponential" -> "e 指数回归";
            case "reg-ab-exponential" -> "ab^x 回归";
            case "reg-power" -> "幂回归";
            case "reg-inverse" -> "逆数回归";
            default -> "线性回归";
        };
    }

    private static WorkflowSpec functionTable(String commandId) {
        if (commandId.equals("single") || commandId.equals("f")) {
            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x)",
                    InputLayout.FIXED_FIELDS,
                    fields(field("f", "f(x)", FieldKind.EXPRESSION),
                            field("start", "开始", FieldKind.EXPRESSION),
                            field("end", "结束", FieldKind.EXPRESSION),
                            field("step", "步长", FieldKind.EXPRESSION)),
                    1, 1, 4, 4);
        }
        if (commandId.equals("fg")) {
            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x), g(x)",
                    InputLayout.FIXED_FIELDS,
                    fields(field("f", "f(x)", FieldKind.EXPRESSION),
                            field("g", "g(x)", FieldKind.EXPRESSION),
                            field("start", "开始", FieldKind.EXPRESSION),
                            field("end", "结束", FieldKind.EXPRESSION),
                            field("step", "步长", FieldKind.EXPRESSION)),
                    1, 1, 5, 5);
        }
        return null;
    }

    private static WorkflowSpec equation(String commandId) {
        if (commandId.equals("polynomial")) {
            return spec(ApplicationMode.EQUATION, commandId, "多项式方程",
                    InputLayout.COEFFICIENTS,
                    fields(field("coefficient", "系数", FieldKind.EXPRESSION)),
                    3, 5, 1, 1);
        }
        if (commandId.equals("simultaneous")) {
            return spec(ApplicationMode.EQUATION, commandId, "联立方程",
                    InputLayout.COEFFICIENTS,
                    fields(field("dimension", "元数", FieldKind.DIMENSION),
                            field("coefficient", "系数", FieldKind.EXPRESSION),
                            field("constant", "常数", FieldKind.EXPRESSION)),
                    2, 4, 3, 5);
        }
        if (commandId.equals("solve")) {
            return spec(ApplicationMode.EQUATION, commandId, "SOLVE",
                    InputLayout.FIXED_FIELDS,
                    fields(field("expression", "f(x)", FieldKind.EXPRESSION),
                            field("initial", "初值", FieldKind.EXPRESSION)),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static WorkflowSpec inequality(String commandId) {
        int degree = switch (commandId) {
            case "quadratic" -> 2;
            case "cubic" -> 3;
            case "quartic" -> 4;
            default -> 0;
        };
        if (degree == 0) return null;
        List<FieldSpec> values = new ArrayList<>();
        values.add(choiceField("relation", "关系",
                choice("1", ">"), choice("2", "<"),
                choice("3", "≥"), choice("4", "≤")));
        for (int power = degree; power >= 0; power--) {
            String label = power == 0 ? "常数" : power == 1 ? "x" : "x^" + power;
            values.add(field("c" + power, label, FieldKind.EXPRESSION));
        }
        String title = degree == 2 ? "二次不等式" : degree == 3 ? "三次不等式" : "四次不等式";
        int columns = degree + 2;
        return spec(ApplicationMode.INEQUALITY, commandId, title,
                InputLayout.FIXED_FIELDS, values, 1, 1, columns, columns);
    }

    private static WorkflowSpec baseN(String commandId) {
        if (commandId.equals("base-convert")) {
            return spec(ApplicationMode.BASE_N, commandId, "进制转换",
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("sourceBase", "输入进制"),
                            baseChoice("targetBase", "输出进制"),
                            field("value", "数值", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        if (commandId.equals("base-negate") || commandId.equals("base-not")) {
            return spec(ApplicationMode.BASE_N, commandId, baseNTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("base", "进制"),
                            field("value", "数值", FieldKind.EXPRESSION)),
                    1, 1, 2, 2);
        }
        if (commandId.equals("base-add") || commandId.equals("base-subtract")
                || commandId.equals("base-multiply") || commandId.equals("base-divide")
                || commandId.equals("base-and") || commandId.equals("base-or")
                || commandId.equals("base-xor") || commandId.equals("base-xnor")) {
            return spec(ApplicationMode.BASE_N, commandId, baseNTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("base", "进制"),
                            field("left", "左值", FieldKind.EXPRESSION),
                            field("right", "右值", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        return null;
    }

    private static FieldSpec baseChoice(String id, String label) {
        return choiceField(id, label,
                choice("10", "DEC"), choice("16", "HEX"),
                choice("2", "BIN"), choice("8", "OCT"));
    }

    private static String baseNTitle(String commandId) {
        return switch (commandId) {
            case "base-add" -> "加法";
            case "base-subtract" -> "减法";
            case "base-multiply" -> "乘法";
            case "base-divide" -> "除法";
            case "base-negate" -> "取负";
            case "base-not" -> "NOT";
            case "base-and" -> "AND";
            case "base-or" -> "OR";
            case "base-xor" -> "XOR";
            case "base-xnor" -> "XNOR";
            default -> "Base-N";
        };
    }

    private static WorkflowSpec matrix(String commandId) {
        if (commandId.equals("calculate")) {
            return spec(ApplicationMode.MATRIX, commandId, "直接矩阵",
                    InputLayout.GRID,
                    fields(field("cell", "元素", FieldKind.EXPRESSION)),
                    1, 4, 1, 4);
        }
        String definition = switch (commandId) {
            case "define" -> "MatA";
            case "mat-b" -> "MatB";
            case "mat-c" -> "MatC";
            case "mat-d" -> "MatD";
            default -> null;
        };
        if (definition != null) {
            return spec(ApplicationMode.MATRIX, commandId, definition,
                    InputLayout.GRID,
                    fields(field("cell", "元素", FieldKind.EXPRESSION)),
                    1, 4, 1, 4);
        }
        if (commandId.equals("matrix-det") || commandId.equals("matrix-inverse")
                || commandId.equals("matrix-transpose") || commandId.equals("matrix-square")
                || commandId.equals("matrix-cube") || commandId.equals("matrix-abs")) {
            return spec(ApplicationMode.MATRIX, commandId, matrixOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("matrix", "矩阵")),
                    1, 1, 1, 1);
        }
        if (commandId.equals("matrix-identity")) {
            return spec(ApplicationMode.MATRIX, commandId, "单位矩阵",
                    InputLayout.FIXED_FIELDS,
                    fields(field("dimension", "维数", FieldKind.EXPRESSION)),
                    1, 1, 1, 1);
        }
        if (commandId.equals("matrix-add") || commandId.equals("matrix-subtract")
                || commandId.equals("matrix-multiply")) {
            return spec(ApplicationMode.MATRIX, commandId, matrixOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("left", "左矩阵"),
                            linearAlgebraSlot("right", "右矩阵")),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static String matrixOperationTitle(String commandId) {
        return switch (commandId) {
            case "matrix-det" -> "行列式";
            case "matrix-inverse" -> "逆矩阵";
            case "matrix-transpose" -> "矩阵转置";
            case "matrix-square" -> "矩阵平方";
            case "matrix-cube" -> "矩阵立方";
            case "matrix-identity" -> "单位矩阵";
            case "matrix-abs" -> "元素绝对值";
            case "matrix-add" -> "矩阵加法";
            case "matrix-subtract" -> "矩阵减法";
            case "matrix-multiply" -> "矩阵乘法";
            default -> "矩阵";
        };
    }

    private static WorkflowSpec vector(String commandId) {
        if (commandId.equals("calculate")) {
            return spec(ApplicationMode.VECTOR, commandId, "直接向量",
                    InputLayout.VECTOR_SET,
                    fields(field("component", "分量", FieldKind.EXPRESSION)),
                    1, 2, 2, 3);
        }
        String definition = switch (commandId) {
            case "define" -> "VctA";
            case "vct-b" -> "VctB";
            case "vct-c" -> "VctC";
            case "vct-d" -> "VctD";
            default -> null;
        };
        if (definition != null) {
            return spec(ApplicationMode.VECTOR, commandId, definition,
                    InputLayout.VECTOR_SET,
                    fields(field("component", "分量", FieldKind.EXPRESSION)),
                    1, 1, 2, 3);
        }
        if (commandId.equals("vector-magnitude") || commandId.equals("vector-unit")) {
            return spec(ApplicationMode.VECTOR, commandId, vectorOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("vector", "向量")),
                    1, 1, 1, 1);
        }
        if (commandId.equals("vector-add") || commandId.equals("vector-subtract")
                || commandId.equals("vector-dot") || commandId.equals("vector-cross")
                || commandId.equals("vector-angle")) {
            return spec(ApplicationMode.VECTOR, commandId, vectorOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("left", "左向量"),
                            linearAlgebraSlot("right", "右向量")),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static String vectorOperationTitle(String commandId) {
        return switch (commandId) {
            case "vector-magnitude" -> "向量模";
            case "vector-unit" -> "单位向量";
            case "vector-add" -> "向量加法";
            case "vector-subtract" -> "向量减法";
            case "vector-dot" -> "向量点积";
            case "vector-cross" -> "向量叉积";
            case "vector-angle" -> "向量夹角";
            default -> "向量";
        };
    }

    private static FieldSpec linearAlgebraSlot(String id, String label) {
        return choiceField(id, label,
                choice("A", "A"), choice("B", "B"), choice("C", "C"), choice("D", "D"),
                choice("Ans", "Ans"));
    }

    private static WorkflowSpec ratio(String commandId) {
        if (commandId.equals("a:b=x:d")) {
            return spec(ApplicationMode.RATIO, commandId, "A:B=X:D",
                    InputLayout.FIXED_FIELDS,
                    fields(field("a", "A", FieldKind.EXPRESSION),
                            field("b", "B", FieldKind.EXPRESSION),
                            field("d", "D", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        if (commandId.equals("a:b=c:x")) {
            return spec(ApplicationMode.RATIO, commandId, "A:B=C:X",
                    InputLayout.FIXED_FIELDS,
                    fields(field("a", "A", FieldKind.EXPRESSION),
                            field("b", "B", FieldKind.EXPRESSION),
                            field("c", "C", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        return null;
    }

    private static WorkflowSpec spec(ApplicationMode mode, String commandId, String title,
                                     InputLayout layout, List<FieldSpec> fields,
                                     int minRows, int maxRows, int minColumns, int maxColumns) {
        return new WorkflowSpec(mode, commandId, title, layout, fields,
                minRows, maxRows, minColumns, maxColumns);
    }

    private static FieldSpec field(String id, String label, FieldKind kind) {
        return new FieldSpec(id, label, kind, true);
    }

    private static FieldSpec choiceField(String id, String label, ChoiceOption... options) {
        List<ChoiceOption> values = new ArrayList<>();
        for (ChoiceOption option : options) values.add(option);
        return new FieldSpec(id, label, FieldKind.CHOICE, true, values);
    }

    private static ChoiceOption choice(String value, String label) {
        return new ChoiceOption(value, label);
    }

    private static List<FieldSpec> fields(FieldSpec... values) {
        List<FieldSpec> fields = new ArrayList<>();
        for (FieldSpec value : values) fields.add(value);
        return fields;
    }
}
