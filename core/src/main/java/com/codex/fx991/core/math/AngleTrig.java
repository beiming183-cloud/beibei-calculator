package com.codex.fx991.core.math;

import com.codex.fx991.core.AngleUnit;

/** Exact axis values only for exact integer quadrants; never snaps nearby angles. */
public final class AngleTrig {
    private AngleTrig() { }

    public static double sin(double angle, AngleUnit unit) {
        int quadrant = quadrant(angle, quarterTurn(unit));
        return quadrant < 0 ? Math.sin(toRadians(angle, unit)) : sineAtQuadrant(quadrant);
    }

    public static double cos(double angle, AngleUnit unit) {
        int quadrant = quadrant(angle, quarterTurn(unit));
        return quadrant < 0 ? Math.cos(toRadians(angle, unit)) : cosineAtQuadrant(quadrant);
    }

    public static double sinRadians(double angle) { return sin(angle, AngleUnit.RAD); }
    public static double cosRadians(double angle) { return cos(angle, AngleUnit.RAD); }

    private static int quadrant(double angle, double quarterTurn) {
        if (!Double.isFinite(angle) || angle % quarterTurn != 0.0) return -1;
        int result = (int) ((angle / quarterTurn) % 4.0);
        return result < 0 ? result + 4 : result;
    }

    private static double sineAtQuadrant(int quadrant) {
        return switch (quadrant) { case 1 -> 1.0; case 3 -> -1.0; default -> 0.0; };
    }

    private static double cosineAtQuadrant(int quadrant) {
        return switch (quadrant) { case 0 -> 1.0; case 2 -> -1.0; default -> 0.0; };
    }

    private static double quarterTurn(AngleUnit unit) {
        return switch (unit) { case DEG -> 90.0; case GRAD -> 100.0; case RAD -> Math.PI / 2.0; };
    }

    private static double toRadians(double angle, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toRadians(angle);
            case RAD -> angle;
            case GRAD -> angle * Math.PI / 200.0;
        };
    }
}
