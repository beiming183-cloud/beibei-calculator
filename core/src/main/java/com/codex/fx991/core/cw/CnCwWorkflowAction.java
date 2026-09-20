package com.codex.fx991.core.cw;

import java.util.Objects;

/**
 * Core-owned action descriptor for one structured application workflow.
 *
 * <p>Android may render these actions as buttons, chips, menu rows, or key
 * legends, but it must not infer workflow capabilities from layout names.</p>
 */
public final class CnCwWorkflowAction {
    public enum Type {
        ADD_ROW,
        REMOVE_ROW,
        DECREASE_ROWS,
        INCREASE_ROWS,
        DECREASE_COLUMNS,
        INCREASE_COLUMNS,
        EXECUTE,
        BACK
    }

    private final Type type;
    private final String label;
    private final boolean enabled;

    public CnCwWorkflowAction(Type type, String label, boolean enabled) {
        this.type = Objects.requireNonNull(type, "type");
        if (com.codex.fx991.core.Compat.isBlank(label)) {
            throw new IllegalArgumentException("label");
        }
        this.label = label;
        this.enabled = enabled;
    }

    public Type type() { return type; }
    public String label() { return label; }
    public boolean enabled() { return enabled; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CnCwWorkflowAction other)) return false;
        return type == other.type && enabled == other.enabled && label.equals(other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, label, enabled);
    }

    @Override
    public String toString() {
        return type + "(" + label + ", " + (enabled ? "enabled" : "disabled") + ")";
    }
}
