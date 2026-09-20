package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwCalculationState;
import com.codex.fx991.core.math.*;
import com.codex.fx991.core.mode.CnCwModel;
import java.util.List;
import java.util.Map;

/** Numerical counterexamples found during the Stage 6 independent audit. */
public final class CnCwNumericalSafetySuite {
    private int checks;
    private int failures;

    public static void main(String[] args) {
        CnCwNumericalSafetySuite suite = new CnCwNumericalSafetySuite();
        suite.run();
        if (suite.failures != 0) throw new AssertionError(suite.failures + " numerical safety failures");
        System.out.println("PASS " + suite.checks + " numerical safety checks");
    }

    private void run() {
        test("quadratic noisy fit agrees with independent exact coefficients", () -> {
            for (double origin : new double[]{0, 1e8}) {
                var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                        new double[]{origin-2, origin-1, origin, origin+1, origin+2}, new double[]{4, 1, 1, 1, 4});
                near(6.0/7, fit.a(), 1e-12);
                near(17.0/35, fit.estimateY(origin), 1e-12);
                near(137.0/35, fit.estimateY(origin+2), 1e-12);
            }
        });
        test("quadratic independent x/y scales", () -> {
            for (double sx : new double[]{1e-90, 1e-9, 1, 1e9, 1e90}) {
                for (double sy : new double[]{1e-90, 1, 1e90}) {
                    var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                            new double[]{-sx, 0, sx}, new double[]{sy, 0, sy});
                    near(0.25*sy, fit.estimateY(0.5*sx), sy*1e-12);
                    double expected = (sy/sx)/sx;
                    near(expected, fit.a(), Math.abs(expected)*1e-12);
                }
            }
        });
        test("quadratic zero-frequency outlier has no influence", () -> {
            var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{-1, 0, 1, 1e99}, new double[]{1, 0, 1, -1e99}, new double[]{1, 1, 1, 0});
            near(1, fit.a(), 1e-12); near(0.25, fit.estimateY(0.5), 1e-12);
        });
        test("quadratic frequency rescaling and row permutation preserve predictions", () -> {
            var first = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{-2, -1, 0, 1, 2}, new double[]{4, 1, 1, 1, 4}, new double[]{1, 2, 3, 4, 5});
            var second = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{2, 1, 0, -1, -2}, new double[]{4, 1, 1, 1, 4},
                    new double[]{5e90, 4e90, 3e90, 2e90, 1e90});
            for (double x : new double[]{-2, -0.5, 0, 1.5, 2}) near(first.estimateY(x), second.estimateY(x), 1e-12);
        });
        test("quadratic constant response and inverse boundaries", () -> {
            var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{-1, 0, 1}, new double[]{7, 7, 7});
            near(7, fit.estimateY(2), 0);
            require(fit.estimateX(8).length == 0);
            rejects(() -> fit.estimateX(7));
            var line = new StatisticsEngine.RegressionResult(StatisticsEngine.RegressionType.QUADRATIC, 0, 2, 1, Double.NaN);
            near(3, line.estimateX(7)[0], 0);
        });
        test("quadratic near-rank-deficient design refuses an unreliable fit", () -> rejects(() ->
                StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                        new double[]{0, 1e-16, 1}, new double[]{0, 1, 2})));
        test("quadratic fit cooperates with cancellation", () -> {
            Thread.currentThread().interrupt();
            try {
                StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                        new double[]{-1, 0, 1}, new double[]{1, 0, 1});
                throw new AssertionError("cancellation was ignored");
            } catch (java.util.concurrent.CancellationException expected) {
                // Must not become a successful fit or an ordinary math error.
            } finally { Thread.interrupted(); }
        });
        test("quadratic fit with large x offset retains local predictions", () -> {
            var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{99999999, 100000000, 100000001}, new double[]{1, 0, 1});
            near(1, fit.a(), 1e-10); near(-200000000, fit.b(), 1e-2);
            near(0, fit.estimateY(100000000), 1e-10);
            near(1, fit.estimateY(100000001), 1e-10);
            double[] inverse = fit.estimateX(1);
            java.util.Arrays.sort(inverse);
            near(99999999, inverse[0], 1e-6); near(100000001, inverse[1], 1e-6);
        });
        test("quadratic fit on a small x scale", () -> {
            var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{-1e-9, 0, 1e-9}, new double[]{1, 0, 1});
            near(1e18, fit.a(), 1e5); near(0.25, fit.estimateY(5e-10), 1e-12);
        });
        test("quadratic fit preserves small y values", () -> {
            var fit = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{-1, 0, 1}, new double[]{1e-20, 0, 1e-20});
            near(1e-20, fit.a(), 1e-32); near(2.5e-21, fit.estimateY(0.5), 1e-32);
        });
        test("quadratic fit requires three distinct active x values", () -> rejects(() ->
                StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                        new double[]{0, 0, 1, 2}, new double[]{1, 1, 2, 5}, new double[]{1, 1, 1, 0})));
        test("quadratic weighted fit agrees with expanded samples", () -> {
            var weighted = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{1e8-1, 1e8, 1e8+1, 1e8+2}, new double[]{2, 0, 1, 5},
                    new double[]{2, 1, 3, 1});
            var expanded = StatisticsEngine.regression(StatisticsEngine.RegressionType.QUADRATIC,
                    new double[]{1e8-1, 1e8-1, 1e8, 1e8+1, 1e8+1, 1e8+1, 1e8+2},
                    new double[]{2, 2, 0, 1, 1, 1, 5});
            for (double x : new double[]{1e8-1, 1e8, 1e8+0.5, 1e8+2})
                near(expanded.estimateY(x), weighted.estimateY(x), 1e-10);
        });
        test("common polynomial coefficient scaling preserves separated roots", () -> {
            for (double scale : new double[]{1e-90, 1e-30, 1, 1e30, 1e90}) {
                var roots = PolynomialEngine.roots(scale, -6*scale, 11*scale, -6*scale);
                for (int i = 0; i < 3; i++) {
                    near(i+1, roots.get(i).real(), 1e-8); near(0, roots.get(i).imaginary(), 0);
                }
            }
        });
        test("real cubic roots have no invented imaginary component", () -> {
            var roots = PolynomialEngine.roots(1, -6, 11, -6);
            for (int i = 0; i < roots.size(); i++) {
                near(i + 1, roots.get(i).real(), 1e-12);
                near(0, roots.get(i).imaginary(), 0);
            }
        });
        test("irrational real roots are classified in the engine", () -> {
            var roots = PolynomialEngine.roots(1, 0, -2, -1);
            for (var root : roots) near(0, root.imaginary(), 0);
            near((-1 - Math.sqrt(5)) / -2, roots.get(2).real(), 1e-12);
        });
        test("quartic real-root result is clean without formatter truncation", () -> {
            var result = com.codex.fx991.core.cw.CnCwModeEngine.evaluate(
                    com.codex.fx991.core.mode.ApplicationMode.EQUATION, "polynomial",
                    "1,-10,35,-50,24", ScalarExpressionEngine.EvaluationContext.standard());
            for (var item : result.items()) require(!item.value().contains("i"));
        });
        test("small actual complex roots remain complex", () -> {
            var roots = PolynomialEngine.roots(1, 0, 1e-26);
            near(-1e-13, roots.get(0).imaginary(), 1e-28);
            near(1e-13, roots.get(1).imaginary(), 1e-28);
            var mixed = PolynomialEngine.roots(1, -2, 1e-12, -2e-12);
            long complexCount = mixed.stream().filter(r -> r.imaginary() != 0.0).count();
            require(complexCount == 2);
        });
        test("positive polynomial without real roots", () -> require(
                PolynomialEngine.solveInequality(PolynomialEngine.Relation.GREATER, 1, 0, 1).isAllReals()));
        test("negative polynomial without real roots", () -> require(
                PolynomialEngine.solveInequality(PolynomialEngine.Relation.LESS, -1, 0, -1).isAllReals()));
        test("opposite no-root relation remains empty", () -> require(
                PolynomialEngine.solveInequality(PolynomialEngine.Relation.LESS_OR_EQUAL, 1, 0, 1).isNoSolution()));
        test("quartic repeated roots", () -> allRoots(PolynomialEngine.roots(1, -4, 6, -4, 1), 1, 4));
        test("cubic repeated roots", () -> allRoots(PolynomialEngine.roots(1, -3, 3, -1), 1, 3));
        test("quartic isolated inequality root", () -> {
            var intervals = PolynomialEngine.solveInequality(
                    PolynomialEngine.Relation.LESS_OR_EQUAL, 1, -4, 6, -4, 1).intervals();
            require(intervals.size() == 1);
            near(1, intervals.get(0).lower(), 1e-12);
            near(1, intervals.get(0).upper(), 1e-12);
            require(intervals.get(0).includeLower() && intervals.get(0).includeUpper());
        });
        test("cubic repeated inequality boundary", () -> {
            var intervals = PolynomialEngine.solveInequality(
                    PolynomialEngine.Relation.GREATER, 1, -3, 3, -1).intervals();
            require(intervals.size() == 1);
            near(1, intervals.get(0).lower(), 1e-12);
            require(!intervals.get(0).includeLower());
            require(intervals.get(0).upper() == Double.POSITIVE_INFINITY);
        });
        test("quartic two repeated real roots", () -> {
            var roots = PolynomialEngine.roots(1, -6, 13, -12, 4);
            near(1, roots.get(0).real(), 1e-12);
            near(1, roots.get(1).real(), 1e-12);
            near(2, roots.get(2).real(), 1e-12);
            near(2, roots.get(3).real(), 1e-12);
            for (var root : roots) near(0, root.imaginary(), 0);
        });
        test("cubic mixed repeated and simple roots", () -> {
            var roots = PolynomialEngine.roots(1, 0, -3, 2);
            near(-2, roots.get(0).real(), 1e-12);
            near(1, roots.get(1).real(), 1e-12);
            near(1, roots.get(2).real(), 1e-12);
            for (var root : roots) near(0, root.imaginary(), 0);
        });
        test("quartic repeated real root with complex pair", () -> {
            var roots = PolynomialEngine.roots(1, -3, 4, -3, 1);
            int repeated = 0;
            for (var root : roots) if (Math.abs(root.real() - 1) < 1e-12) {
                near(0, root.imaginary(), 0);
                repeated++;
            }
            require(repeated == 2);
        });
        test("decimal repeated cubic", () -> allRoots(PolynomialEngine.roots(1, -.3, .03, -.001), .1, 3));
        test("rational repeated quartic", () -> allRoots(PolynomialEngine.roots(81, -108, 54, -12, 1), 1.0/3.0, 4));
        test("large ordinary cubic root converges", () -> {
            var roots = PolynomialEngine.roots(1, 0, 0, -1e12);
            var real = roots.stream().min(java.util.Comparator.comparingDouble(r -> Math.abs(r.imaginary()))).orElseThrow();
            near(10000, real.real(), 1e-8);
            near(0, real.imaginary(), 1e-8);
        });
        test("exact zero root with nearby simple roots", () -> {
            var roots = PolynomialEngine.roots(1, -2.000001, 1.000001, 0);
            near(0, roots.get(0).abs(), 0);
            near(1, roots.get(1).real(), 1e-9);
            near(1.000001, roots.get(2).real(), 1e-9);
        });
        test("canceled polynomial work stops immediately", () -> {
            Thread.currentThread().interrupt();
            try {
                try {
                    PolynomialEngine.roots(1, -4, 6, -4, 1);
                    throw new AssertionError("canceled polynomial returned a result");
                } catch (java.util.concurrent.CancellationException expected) { }
            } finally { Thread.interrupted(); }
        });
        test("tiny nonzero leading coefficient", () -> {
            var roots = PolynomialEngine.roots(1e-16, 0, -1e-16);
            near(-1, roots.get(0).real(), 1e-12);
            near(1, roots.get(1).real(), 1e-12);
        });
        test("tiny nonzero quadratic roots", () -> {
            var roots = PolynomialEngine.roots(1, 0, -1e-26);
            near(-1e-13, roots.get(0).real(), 1e-26);
            near(1e-13, roots.get(1).real(), 1e-26);
        });
        test("small genuine imaginary roots are not inequality boundaries", () -> require(
                PolynomialEngine.solveInequality(PolynomialEngine.Relation.GREATER, 1, 0, 1e-20).isAllReals()));
        test("nearby distinct real inequality boundaries", () -> {
            var intervals = PolynomialEngine.solveInequality(PolynomialEngine.Relation.LESS, 1, 0, -1e-26).intervals();
            require(intervals.size() == 1);
            near(-1e-13, intervals.get(0).lower(), 1e-26);
            near(1e-13, intervals.get(0).upper(), 1e-26);
        });
        test("scaled inequality retains inclusive endpoints", () -> {
            var intervals = PolynomialEngine.solveInequality(
                    PolynomialEngine.Relation.GREATER_OR_EQUAL, 1e20, 0, -2e20).intervals();
            require(intervals.size() == 2);
            require(intervals.get(0).includeUpper());
            require(intervals.get(1).includeLower());
        });
        test("complex common logarithm", () -> near(2, complex("log(100)").real(), 1e-14));
        test("complex natural logarithm unchanged", () -> near(Math.log(100), complex("ln(100)").real(), 1e-14));
        test("ordinary mixed real complex common logarithm", () -> {
            var machine = machine(false, "sqrt(-1)+log(100)");
            near(2, machine.state().calculationState().complexValue().real(), 1e-14);
        });
        test("zero positive nonintegral power", () -> near(0, complex("0^0.5").abs(), 0));
        test("zero-to-zero is a domain error", () -> rejects(() -> complex("0^0")));
        test("log zero is a domain error", () -> rejects(() -> complex("ln(0)")));
        test("complex overflow is an error", () -> rejects(() -> complex("exp(1000)")));
        test("complex domain error cannot become successful Ans", () -> {
            var machine = machine(true, "ln(0)");
            require(machine.state().calculationState().phase() == CnCwCalculationState.Phase.ERROR);
            machine.dispatch(CnCwKey.AC);
            machine.pasteExpression("2+3");
            machine.dispatch(CnCwKey.EXE);
            near(5, machine.state().calculationState().scalarValue(), 0);
        });
        test("complex square root retains small component", () -> {
            var root = complex("sqrt(1+1e-10*i)");
            near(1, root.real(), 1e-14);
            near(5e-11, root.imaginary(), 1e-24);
        });
        test("population and sample variance with offset", () -> {
            var result = StatisticsEngine.oneVariable(new double[]{100000000, 100000001});
            near(.25, result.populationVariance(), 1e-15);
            near(.5, result.sampleVariance(), 1e-15);
            near(.5, result.populationStdDev(), 1e-15);
        });
        test("weighted variance with offset", () -> {
            var result = StatisticsEngine.oneVariable(new double[]{100000000, 100000001}, new double[]{1, 3});
            near(.1875, result.populationVariance(), 1e-15);
        });
        test("two-variable variance with offset", () -> {
            var result = StatisticsEngine.twoVariable(new double[]{100000000, 100000001}, new double[]{200000000, 200000002});
            near(.25, result.populationVarianceX(), 1e-15);
            near(1, result.populationVarianceY(), 1e-15);
        });
        test("linear regression with offset", () -> {
            var result = StatisticsEngine.regression(StatisticsEngine.RegressionType.LINEAR,
                    new double[]{100000000, 100000001}, new double[]{0, 1});
            near(1, result.a(), 1e-15);
            near(-100000000, result.b(), 1e-7);
            near(1, result.r(), 1e-15);
        });
        test("small nonsingular regression", () -> {
            var result = StatisticsEngine.regression(StatisticsEngine.RegressionType.LINEAR,
                    new double[]{0, 1e-10}, new double[]{0, 1e-10});
            near(1, result.a(), 1e-15);
        });
        test("complex implicit multiplication follows scalar precedence", () -> near(1, complex("6/2(1+2)").real(), 0));
        test("scalar implicit multiplication unchanged", () -> near(1, ScalarExpressionEngine.evaluate("6/2(1+2)", null), 0));
        String[] commonExpressions = {
                "6/2(1+2)", "8/2(2)", "6/2(3)+1", "6/-2(3)",
                "6/2*(1+2)", "(6/2)(1+2)", "6/(2(1+2))", "6/(-2)(1+2)",
                "12/2pi", "12/2sin(30)", "8/2(2)^2", "8/2(2)/2",
                "2^3^2", "-2^2", "2^-2", "6÷2(1+2)", "6÷2×(1+2)"
        };
        for (String expression : commonExpressions) {
            test("scalar/complex grammar parity: " + expression, () -> {
                double expected = ScalarExpressionEngine.evaluate(expression, null);
                ComplexValue actual = complex(expression);
                near(expected, actual.real(), 1e-12 * Math.max(1, Math.abs(expected)));
                near(0, actual.imaginary(), 0);
            });
        }
        String[] complexExpressions = {"1/2i", "i/2i", "1/2i^2", "8/2(1+i)", "8/2*(1+i)"};
        double[][] complexExpected = {{0, -0.5}, {0.5, 0}, {-0.5, 0}, {2, -2}, {4, 4}};
        for (int index = 0; index < complexExpressions.length; index++) {
            final int sample = index;
            test("complex implicit denominator: " + complexExpressions[sample], () -> {
                ComplexValue actual = complex(complexExpressions[sample]);
                near(complexExpected[sample][0], actual.real(), 1e-12);
                near(complexExpected[sample][1], actual.imaginary(), 1e-12);
            });
        }
        test("bounded generated explicit/implicit product parity", () -> {
            for (int a = -3; a <= 3; a++) {
                if (a == 0) continue;
                for (int b = 1; b <= 4; b++) {
                    for (int c = -2; c <= 2; c++) {
                        if (c == 0) continue;
                        for (String join : new String[]{"", "*"}) {
                            String expression = a + "/(" + b + ")" + join + "(" + c + ")";
                            double expected = join.isEmpty() ? (double) a / (b * c) : (double) a / b * c;
                            near(expected, ScalarExpressionEngine.evaluate(expression, null), 1e-12);
                            near(expected, complex(expression).real(), 1e-12);
                            near(0, complex(expression).imaginary(), 0);
                        }
                    }
                }
            }
        });
        test("implicit denominator includes complex variables and Ans", () -> {
            var variables = Map.of("A", new ComplexValue(3, 0));
            for (String expression : new String[]{"12/2A", "12/2Ans"}) {
                var actual = ComplexExpressionEngine.evaluate(expression, variables,
                        new ComplexValue(3, 0), AngleUnit.DEG);
                near(2, actual.real(), 0); near(0, actual.imaginary(), 0);
            }
        });
        test("parenthesized polar value is an implicit denominator factor", () -> {
            var actual = complex("1/2(1∠90)");
            near(0, actual.real(), 0); near(-0.5, actual.imaginary(), 0);
        });
        test("implicit zero denominator is an error, not zero", () -> rejects(() -> complex("1/2(0)")));
        test("asinh negative large argument", () -> near(-(Math.log(1e8) + Math.log(2)),
                ScalarExpressionEngine.evaluate("asinh(-100000000)", null), 1e-13));
        test("asinh large in-range argument", () -> near(Math.log(1e99) + Math.log(2),
                ScalarExpressionEngine.evaluate("asinh(1e99)", null), 1e-12));
        test("asinh tiny positive argument", () -> near(1e-20,
                ScalarExpressionEngine.evaluate("asinh(1e-20)", null), 1e-34));
        test("asinh tiny negative argument", () -> near(-1e-20,
                ScalarExpressionEngine.evaluate("asinh(-1e-20)", null), 1e-34));
        test("asinh zero", () -> near(0, ScalarExpressionEngine.evaluate("asinh(0)", null), 0));
        test("asinh moderate argument and odd symmetry", () -> {
            double expected = Math.log(1.2 + Math.sqrt(2.44));
            near(expected, ScalarExpressionEngine.evaluate("asinh(1.2)", null), 1e-15);
            near(-expected, ScalarExpressionEngine.evaluate("asinh(-1.2)", null), 1e-15);
        });
        test("nonrepeated quartic residuals", () -> {
            for (var root : PolynomialEngine.roots(1, 0, 0, 0, 1)) near(0, root.pow(4).add(ComplexValue.ONE).abs(), 1e-10);
        });
        test("complex equality verification", () -> require(verify("i^2=-1")));
        test("half power agrees with principal square root", () -> require(verify("(-1)^0.5=i")));
        test("negative half power agrees with reciprocal square root", () -> require(verify("(-1)^(-0.5)=-i")));
        test("polar positive imaginary axis", () -> require(verify("1∠90=i")));
        test("polar negative real axis", () -> require(verify("1∠180=-1")));
        test("polar retains original degree quadrant before conversion", () -> require(verify("1∠90000090=i")));
        test("radian and gradian exact quadrants", () -> {
            require(ComplexExpressionEngine.verify("cos(pi/2)=0", Map.of(), ComplexValue.ZERO, AngleUnit.RAD));
            require(ComplexExpressionEngine.verify("sin(200)=0", Map.of(), ComplexValue.ZERO, AngleUnit.GRAD));
            require(verify("1∠(-90)=-i"));
        });
        test("complex sine exact degree axis", () -> require(verify("sin(180)=0")));
        test("complex cosine exact degree axis", () -> require(verify("cos(90)=0")));
        test("complex exponential exact imaginary axis", () -> require(verify("exp(i*pi)=-1")));
        test("complex three-half power exact imaginary axis", () -> require(verify("(-1)^1.5=-i")));
        test("scalar degree sine and cosine axes", () -> {
            near(0, ScalarExpressionEngine.evaluate("sin(180)", null), 0);
            near(0, ScalarExpressionEngine.evaluate("cos(90)", null), 0);
            rejects(() -> ScalarExpressionEngine.evaluate("1/sin(180)", null));
        });
        test("scalar large integer degree quadrant", () -> near(0,
                ScalarExpressionEngine.evaluate("cos(90000090)", null), 0));
        test("near-axis polar component is retained", () -> {
            double angle = Math.nextDown(Math.PI / 2.0);
            near(Math.cos(angle), ComplexValue.polar(1, angle).real(), 0);
            require(ComplexValue.polar(1, angle).real() != 0);
        });
        test("near-axis exponential component is retained", () -> {
            double angle = Math.nextDown(Math.PI);
            near(Math.sin(angle), new ComplexValue(0, angle).exp().imaginary(), 0);
            require(new ComplexValue(0, angle).exp().imaginary() != 0);
        });
        test("near-axis trigonometric input is retained", () -> {
            String sine = "sin(" + Double.toString(Math.nextUp(180.0)) + ")";
            String cosine = "cos(" + Double.toString(Math.nextDown(90.0)) + ")";
            require(complex(sine).real() != 0);
            require(complex(cosine).real() != 0);
            require(ScalarExpressionEngine.evaluate(sine, null) != 0);
            require(ScalarExpressionEngine.evaluate(cosine, null) != 0);
        });
        test("explicit tiny imaginary coefficient survives", () -> near(1e-15, complex("1e-15*i").imaginary(), 0));
        test("complex inequality verification", () -> require(verify("1+i!=1-i")));
        test("tiny imaginary value is not zero", () -> require(!verify("1e-15*i=0")));
        test("tiny imaginary component is not discarded", () -> require(!verify("1+1e-15*i=1")));
        test("ordering real values in complex mode", () -> require(verify("1+1<=3")));
        test("ordering complex values is rejected", () -> rejects(() -> verify("1e-15*i<1")));
        test("missing verification relation is rejected", () -> rejects(() -> verify("1+i")));
        test("multiple verification relations are rejected", () -> rejects(() -> verify("1=1=1")));
    }

    private static CnCwMachine machine(boolean complex, String source) {
        var machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        if (complex) for (int i = 0; i < 5; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        machine.pasteExpression(source);
        machine.dispatch(CnCwKey.EXE);
        return machine;
    }
    private static ComplexValue complex(String source) {
        return ComplexExpressionEngine.evaluate(source, Map.of(), ComplexValue.ZERO, AngleUnit.DEG);
    }
    private static boolean verify(String source) {
        return ComplexExpressionEngine.verify(source, Map.of(), ComplexValue.ZERO, AngleUnit.DEG);
    }
    private void test(String name, Runnable test) {
        checks++;
        try { test.run(); }
        catch (Throwable error) { failures++; System.err.println("FAIL " + name + ": " + error); }
    }
    private static void allRoots(List<ComplexValue> roots, double expected, int count) {
        require(roots.size() == count);
        for (var root : roots) { near(expected, root.real(), 1e-12); near(0, root.imaginary(), 0); }
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void near(double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance)
            throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (ArithmeticException | IllegalArgumentException expected) { return; }
        throw new AssertionError("expected calculation error");
    }
}
