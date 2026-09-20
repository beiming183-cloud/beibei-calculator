package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.MatrixValue;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.VectorValue;
import com.codex.fx991.core.mode.ApplicationMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent-in-session MatA..MatD / VctA..VctD memory owned by the core machine.
 * Values are immutable, so evaluation snapshots can copy slot references safely.
 */
public final class CnCwLinearAlgebraMemory {
    private final Map<String, MatrixValue> matrices = new HashMap<>();
    private final Map<String, VectorValue> vectors = new HashMap<>();
    private MatrixValue matrixAns;
    private VectorValue vectorAns;

    public void copyFrom(CnCwLinearAlgebraMemory source) {
        matrices.clear();
        matrices.putAll(source.matrices);
        vectors.clear();
        vectors.putAll(source.vectors);
        matrixAns = source.matrixAns;
        vectorAns = source.vectorAns;
    }

    public void clear() {
        matrices.clear();
        vectors.clear();
        clearAnswers();
    }

    public void clearAnswers() {
        matrixAns = null;
        vectorAns = null;
    }

    public CnCwModeEngine.ModeResult answer(ApplicationMode mode) {
        if (mode == ApplicationMode.MATRIX) {
            if (matrixAns == null) throw new IllegalArgumentException("MatAns 未定义");
            return matrixResult("MatAns", matrixAns);
        }
        if (mode == ApplicationMode.VECTOR) {
            if (vectorAns == null) throw new IllegalArgumentException("VctAns 未定义");
            return vectorResult("VctAns", vectorAns);
        }
        throw new IllegalArgumentException("No linear algebra answer for " + mode);
    }

    public boolean handles(ApplicationMode mode, String commandId) {
        if (mode == ApplicationMode.MATRIX) {
            return commandId.equals("define") || commandId.startsWith("mat-")
                    || commandId.startsWith("matrix-");
        }
        if (mode == ApplicationMode.VECTOR) {
            return commandId.equals("define") || commandId.startsWith("vct-")
                    || commandId.startsWith("vector-");
        }
        return false;
    }

    public CnCwModeEngine.ModeResult evaluate(ApplicationMode mode,
                                               String commandId,
                                               CnCwWorkflowSession.Snapshot input,
                                               ScalarExpressionEngine.EvaluationContext context) {
        if (input == null) throw new IllegalArgumentException("Structured input required");
        if (mode == ApplicationMode.MATRIX) return matrix(commandId, input, context);
        if (mode == ApplicationMode.VECTOR) return vector(commandId, input, context);
        throw new IllegalArgumentException("Unsupported linear algebra mode");
    }

    private CnCwModeEngine.ModeResult matrix(String command,
                                             CnCwWorkflowSession.Snapshot input,
                                             ScalarExpressionEngine.EvaluationContext context) {
        String definition = matrixDefinitionSlot(command);
        if (definition != null) {
            MatrixValue value = matrixFromGrid(input, context);
            matrices.put(definition, value);
            return matrixResult("Mat" + definition + " 已保存", value);
        }
        return switch (command) {
            case "matrix-det" -> {
                String slot = input.cell(0, 0);
                MatrixValue value = requireMatrix(slot);
                double determinant = value.determinant();
                yield CnCwModeEngine.ModeResult.keyValue("det(Mat" + slot + ")",
                        "det=" + format(determinant), determinant,
                        new CnCwModeEngine.ResultItem("det", format(determinant)));
            }
            case "matrix-inverse" -> rememberMatrixResult(
                    "Mat" + input.cell(0, 0) + "⁻¹", requireMatrix(input.cell(0, 0)).inverse());
            case "matrix-transpose" -> rememberMatrixResult(
                    "Trn(Mat" + input.cell(0, 0) + ")", requireMatrix(input.cell(0, 0)).transpose());
            case "matrix-square" -> {
                MatrixValue value = requireMatrix(input.cell(0, 0));
                yield rememberMatrixResult("Mat" + input.cell(0, 0) + "²", value.multiply(value));
            }
            case "matrix-cube" -> {
                MatrixValue value = requireMatrix(input.cell(0, 0));
                yield rememberMatrixResult("Mat" + input.cell(0, 0) + "³",
                        value.multiply(value).multiply(value));
            }
            case "matrix-identity" -> {
                double raw = ScalarExpressionEngine.evaluate(input.cell(0, 0), context);
                if (raw != Math.rint(raw) || raw < 1 || raw > 4) {
                    throw new IllegalArgumentException("Identity size must be 1..4");
                }
                yield rememberMatrixResult("Identity(" + (int) raw + ")",
                        MatrixValue.identity((int) raw));
            }
            case "matrix-abs" -> rememberMatrixResult(
                    "Abs(Mat" + input.cell(0, 0) + ")", requireMatrix(input.cell(0, 0)).elementAbs());
            case "matrix-add" -> rememberMatrixResult(matrixBinaryTitle(input, "+"),
                    requireMatrix(input.cell(0, 0)).add(requireMatrix(input.cell(0, 1))));
            case "matrix-subtract" -> rememberMatrixResult(matrixBinaryTitle(input, "−"),
                    requireMatrix(input.cell(0, 0)).subtract(requireMatrix(input.cell(0, 1))));
            case "matrix-multiply" -> rememberMatrixResult(matrixBinaryTitle(input, "×"),
                    requireMatrix(input.cell(0, 0)).multiply(requireMatrix(input.cell(0, 1))));
            default -> throw new IllegalArgumentException("Unknown stored matrix command");
        };
    }

    private CnCwModeEngine.ModeResult vector(String command,
                                             CnCwWorkflowSession.Snapshot input,
                                             ScalarExpressionEngine.EvaluationContext context) {
        String definition = vectorDefinitionSlot(command);
        if (definition != null) {
            VectorValue value = vectorFromGrid(input, context);
            vectors.put(definition, value);
            return vectorResult("Vct" + definition + " 已保存", value);
        }
        String leftSlot = input.cell(0, 0);
        VectorValue left = requireVector(leftSlot);
        return switch (command) {
            case "vector-magnitude" -> CnCwModeEngine.ModeResult.keyValue(
                    "|Vct" + leftSlot + "|", "|v|=" + format(left.magnitude()), left.magnitude(),
                    new CnCwModeEngine.ResultItem("|v|", format(left.magnitude())));
            case "vector-unit" -> rememberVectorResult("Unit(Vct" + leftSlot + ")", left.unit());
            case "vector-add" -> rememberVectorResult(vectorBinaryTitle(input, "+"),
                    left.add(requireVector(input.cell(0, 1))));
            case "vector-subtract" -> rememberVectorResult(vectorBinaryTitle(input, "−"),
                    left.subtract(requireVector(input.cell(0, 1))));
            case "vector-dot" -> {
                double value = left.dot(requireVector(input.cell(0, 1)));
                yield CnCwModeEngine.ModeResult.keyValue(vectorBinaryTitle(input, "·"),
                        "dot=" + format(value), value,
                        new CnCwModeEngine.ResultItem("dot", format(value)));
            }
            case "vector-cross" -> rememberVectorResult(vectorBinaryTitle(input, "×"),
                    left.cross(requireVector(input.cell(0, 1))));
            case "vector-angle" -> {
                double degrees = Math.toDegrees(left.angleRadians(requireVector(input.cell(0, 1))));
                yield CnCwModeEngine.ModeResult.keyValue("Angle(Vct" + leftSlot
                                + ",Vct" + input.cell(0, 1) + ")",
                        "θ=" + format(degrees) + "°", degrees,
                        new CnCwModeEngine.ResultItem("θ", format(degrees) + "°"));
            }
            default -> throw new IllegalArgumentException("Unknown stored vector command");
        };
    }

    private MatrixValue matrixFromGrid(CnCwWorkflowSession.Snapshot input,
                                       ScalarExpressionEngine.EvaluationContext context) {
        double[][] values = new double[input.rows()][input.columns()];
        for (int row = 0; row < input.rows(); row++) {
            for (int column = 0; column < input.columns(); column++) {
                values[row][column] = ScalarExpressionEngine.evaluate(input.cell(row, column), context);
            }
        }
        return new MatrixValue(values);
    }

    private VectorValue vectorFromGrid(CnCwWorkflowSession.Snapshot input,
                                       ScalarExpressionEngine.EvaluationContext context) {
        if (input.rows() != 1 || (input.columns() != 2 && input.columns() != 3)) {
            throw new IllegalArgumentException("Vector definition must be one 2D/3D vector");
        }
        double[] values = new double[input.columns()];
        for (int column = 0; column < input.columns(); column++) {
            values[column] = ScalarExpressionEngine.evaluate(input.cell(0, column), context);
        }
        return new VectorValue(values);
    }

    private MatrixValue requireMatrix(String slot) {
        MatrixValue value = "Ans".equals(slot) ? matrixAns : matrices.get(slot);
        if (value == null) throw new IllegalArgumentException(
                "Ans".equals(slot) ? "MatAns 未定义" : "Mat" + slot + " 未定义");
        return value;
    }

    private VectorValue requireVector(String slot) {
        VectorValue value = "Ans".equals(slot) ? vectorAns : vectors.get(slot);
        if (value == null) throw new IllegalArgumentException(
                "Ans".equals(slot) ? "VctAns 未定义" : "Vct" + slot + " 未定义");
        return value;
    }

    private String matrixDefinitionSlot(String command) {
        return switch (command) {
            case "define", "mat-a" -> "A";
            case "mat-b" -> "B";
            case "mat-c" -> "C";
            case "mat-d" -> "D";
            default -> null;
        };
    }

    private String vectorDefinitionSlot(String command) {
        return switch (command) {
            case "define", "vct-a" -> "A";
            case "vct-b" -> "B";
            case "vct-c" -> "C";
            case "vct-d" -> "D";
            default -> null;
        };
    }

    private CnCwModeEngine.ModeResult rememberMatrixResult(String title, MatrixValue value) {
        matrixAns = value;
        return matrixResult(title, value);
    }

    private CnCwModeEngine.ModeResult rememberVectorResult(String title, VectorValue value) {
        vectorAns = value;
        return vectorResult(title, value);
    }

    private CnCwModeEngine.ModeResult matrixResult(String title, MatrixValue value) {
        List<String> cells = new ArrayList<>();
        for (int row = 0; row < value.rows(); row++) {
            for (int column = 0; column < value.columns(); column++) {
                cells.add(format(value.get(row, column)));
            }
        }
        List<CnCwModeEngine.ResultItem> items = new ArrayList<>();
        Double primary = value.get(0, 0);
        if (value.rows() == value.columns()) {
            double determinant = value.determinant();
            items.add(new CnCwModeEngine.ResultItem("det", format(determinant)));
            primary = determinant;
        }
        String display = value.rows() + "×" + value.columns() + "  [1,1]="
                + format(value.get(0, 0));
        return CnCwModeEngine.ModeResult.grid(CnCwModeEngine.ResultLayout.MATRIX,
                title, display, primary, value.rows(), value.columns(), cells, items);
    }

    private CnCwModeEngine.ModeResult vectorResult(String title, VectorValue value) {
        List<String> cells = new ArrayList<>();
        for (double component : value.toArray()) cells.add(format(component));
        List<CnCwModeEngine.ResultItem> items = new ArrayList<>();
        items.add(new CnCwModeEngine.ResultItem("|v|", format(value.magnitude())));
        return CnCwModeEngine.ModeResult.grid(CnCwModeEngine.ResultLayout.VECTOR,
                title, "|v|=" + format(value.magnitude()), value.magnitude(),
                1, value.dimension(), cells, items);
    }

    private String matrixBinaryTitle(CnCwWorkflowSession.Snapshot input, String operator) {
        return "Mat" + input.cell(0, 0) + operator + "Mat" + input.cell(0, 1);
    }

    private String vectorBinaryTitle(CnCwWorkflowSession.Snapshot input, String operator) {
        return "Vct" + input.cell(0, 0) + operator + "Vct" + input.cell(0, 1);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Non-finite matrix/vector result");
        return CnCwModeEngine.format(value);
    }
}
