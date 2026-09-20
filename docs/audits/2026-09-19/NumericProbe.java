import com.codex.fx991.core.AngleUnit;
import com.codex.fx991.core.math.*;
import com.codex.fx991.core.cw.*;
import com.codex.fx991.core.mode.*;
import java.util.*;

public class NumericProbe {
    static void run(String name, java.util.function.Supplier<?> fn) {
        try { System.out.println(name + " => " + fn.get()); }
        catch (Throwable error) { System.out.println(name + " => " + error); }
    }
    public static void main(String[] args) {
        run("x^2+1>0 [expected all reals]", () -> PolynomialEngine.solveInequality(PolynomialEngine.Relation.GREATER,1,0,1));
        run("-x^2-1<0 [expected all reals]", () -> PolynomialEngine.solveInequality(PolynomialEngine.Relation.LESS,-1,0,-1));
        run("(x-1)^4 roots [expected 1 four times]", () -> PolynomialEngine.roots(1,-4,6,-4,1));
        run("(x-1)^4<=0 [expected {1}]", () -> PolynomialEngine.solveInequality(PolynomialEngine.Relation.LESS_OR_EQUAL,1,-4,6,-4,1));
        run("(x-1)^3 roots [expected 1 three times]", () -> PolynomialEngine.roots(1,-3,3,-1));
        run("(x-1)^3>0 [expected (1,+inf)]", () -> PolynomialEngine.solveInequality(PolynomialEngine.Relation.GREATER,1,-3,3,-1));
        run("small leading coefficient [expected +/-1]", () -> PolynomialEngine.roots(1e-16,0,-1e-16));
        run("small nonzero quadratic roots [expected +/-1e-8]", () -> PolynomialEngine.roots(1,0,-1e-16));
        run("tiny quadratic roots [expected +/-1e-13]", () -> PolynomialEngine.roots(1,0,-1e-26));
        run("stats [expected variance .25]", () -> StatisticsEngine.oneVariable(new double[]{100000000,100000001}));
        run("stats regression [expected slope 1, intercept -100000000]", () -> StatisticsEngine.regression(StatisticsEngine.RegressionType.LINEAR,new double[]{100000000,100000001},new double[]{0,1}));
        for (String expr : new String[]{"log(100)","ln(100)","0^0.5","0^0","sqrt(1+1e-10*i)","ln(0)","exp(1000)","6/2(1+2)","tan(90)"}) {
            run("complex " + expr, () -> ComplexExpressionEngine.evaluate(expr,Map.of(),ComplexValue.ZERO,AngleUnit.DEG));
        }
        for (String expr : new String[]{"asinh(-100000000)","asinh(-10000)","6/2(1+2)","sin(180)","1/sin(180)","diff(x^2,100000000)","diff(sin(x),100000000)"}) {
            run("scalar " + expr, () -> ScalarExpressionEngine.evaluate(expr,ScalarExpressionEngine.EvaluationContext.standard()));
        }
        run("derivative x^3 at 1e12 [expected 3e24]", () -> NumericAnalysis.derivative(x -> x*x*x,1e12,1e-10));
        for (String expr : new String[]{"log(100)", "0^0.5", "ln(0)", "exp(1000)", "0.000000000000001i", "6/2(1+2)"}) {
            run("machine COMPLEX " + expr, () -> machine(expr, true));
        }
        run("machine CALCULATE log(100)", () -> machine("log(100)", false));
        run("machine CALCULATE sqrt(-1)+log(100)", () -> machine("sqrt(-1)+log(100)", false));
        run("mode inequality x^2+1>0", () -> CnCwModeEngine.evaluate(ApplicationMode.INEQUALITY, "polynomial", "1,1,0,1", ScalarExpressionEngine.EvaluationContext.standard()).display());
        run("mode polynomial (x-1)^4", () -> CnCwModeEngine.evaluate(ApplicationMode.EQUATION, "polynomial", "1,-4,6,-4,1", ScalarExpressionEngine.EvaluationContext.standard()).display());
    }
    static String machine(String expr, boolean complex) {
        var machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        if (complex) for (int i=0;i<5;i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        machine.pasteExpression(expr);
        machine.dispatch(CnCwKey.EXE);
        var state=machine.state();
        return "display="+state.result()+", phase="+state.calculationState().phase()+", scalar="+state.calculationState().scalarValue();
    }
}
