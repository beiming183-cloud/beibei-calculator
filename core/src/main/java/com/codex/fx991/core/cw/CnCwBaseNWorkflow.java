package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.BaseNEngine;

/** Core-owned structured Base-N operations built on the existing signed 32-bit engine. */
public final class CnCwBaseNWorkflow {
    private CnCwBaseNWorkflow() { }

    public static boolean handles(String commandId) {
        return commandId != null && commandId.startsWith("base-");
    }

    public static CnCwModeEngine.ModeResult evaluate(String commandId,
                                                     CnCwWorkflowSession.Snapshot input) {
        if (!handles(commandId) || input == null) {
            throw new IllegalArgumentException("Base-N workflow");
        }
        if (commandId.equals("base-convert")) {
            BaseNEngine.Base sourceBase = base(input.cell(0, 0));
            BaseNEngine.Base targetBase = base(input.cell(0, 1));
            int value = BaseNEngine.parse(input.cell(0, 2), sourceBase);
            return result("进制转换", targetBase, value);
        }

        BaseNEngine.Base base = base(input.cell(0, 0));
        int left = BaseNEngine.parse(input.cell(0, 1), base);
        int value = switch (commandId) {
            case "base-negate" -> BaseNEngine.negate(left);
            case "base-not" -> BaseNEngine.not(left);
            case "base-add" -> BaseNEngine.add(left, right(input, base));
            case "base-subtract" -> BaseNEngine.subtract(left, right(input, base));
            case "base-multiply" -> BaseNEngine.multiply(left, right(input, base));
            case "base-divide" -> BaseNEngine.divide(left, right(input, base));
            case "base-and" -> BaseNEngine.and(left, right(input, base));
            case "base-or" -> BaseNEngine.or(left, right(input, base));
            case "base-xor" -> BaseNEngine.xor(left, right(input, base));
            case "base-xnor" -> BaseNEngine.xnor(left, right(input, base));
            default -> throw new IllegalArgumentException("Unknown Base-N command");
        };
        return result(title(commandId), base, value);
    }

    private static int right(CnCwWorkflowSession.Snapshot input, BaseNEngine.Base base) {
        return BaseNEngine.parse(input.cell(0, 2), base);
    }

    private static CnCwModeEngine.ModeResult result(String title, BaseNEngine.Base base, int value) {
        String formatted = BaseNEngine.format(value, base);
        String label = label(base);
        return CnCwModeEngine.ModeResult.keyValue(title,
                label + "=" + formatted, (double) value,
                new CnCwModeEngine.ResultItem(label, formatted),
                new CnCwModeEngine.ResultItem("DEC", Integer.toString(value)));
    }

    private static BaseNEngine.Base base(String code) {
        return switch (code) {
            case "16" -> BaseNEngine.Base.HEXADECIMAL;
            case "2" -> BaseNEngine.Base.BINARY;
            case "8" -> BaseNEngine.Base.OCTAL;
            default -> BaseNEngine.Base.DECIMAL;
        };
    }

    private static String label(BaseNEngine.Base base) {
        return switch (base) {
            case DECIMAL -> "DEC";
            case HEXADECIMAL -> "HEX";
            case BINARY -> "BIN";
            case OCTAL -> "OCT";
        };
    }

    private static String title(String commandId) {
        return switch (commandId) {
            case "base-negate" -> "取负";
            case "base-not" -> "NOT";
            case "base-add" -> "加法";
            case "base-subtract" -> "减法";
            case "base-multiply" -> "乘法";
            case "base-divide" -> "除法";
            case "base-and" -> "AND";
            case "base-or" -> "OR";
            case "base-xor" -> "XOR";
            case "base-xnor" -> "XNOR";
            default -> "Base-N";
        };
    }
}
