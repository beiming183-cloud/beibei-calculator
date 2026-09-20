import com.codex.fx991.core.math.*;
import com.codex.fx991.core.cw.*;
import com.codex.fx991.core.mode.*;
import java.util.*;
public class PolynomialDisplayProbe {
    public static void main(String[] args) {
        for (String source : new String[]{"1,0,0,-8", "1,-6,11,-6", "1,0,-2,-1", "1,0,-5,0,4", "1,-10,35,-50,24", "1,0,0,-2", "1,0,0,0,-16"}) {
            try {
                double[] coefficients = Arrays.stream(source.split(",")).mapToDouble(Double::parseDouble).toArray();
                System.out.println(source+" roots: "+PolynomialEngine.roots(coefficients));
                System.out.println("mode: "+CnCwModeEngine.evaluate(ApplicationMode.EQUATION,"polynomial",source,ScalarExpressionEngine.EvaluationContext.standard()).display());
            } catch (Exception error) { System.out.println(source+": "+error); }
        }
    }
}
