import com.codex.fx991.core.cw.*;
import com.codex.fx991.core.math.*;
import com.codex.fx991.core.mode.*;

public final class SpecProbe {
  static CnCwMachine fresh() { return new CnCwMachine(CnCwModel.FX_991_CN_CW); }
  static void key(CnCwMachine m, CnCwKey k) { m.dispatch(k); }
  static void open(CnCwMachine m, int app, int cmd) {
    key(m,CnCwKey.HOME);
    for(int i=0;i<app;i++) key(m,CnCwKey.RIGHT);
    key(m,CnCwKey.OK);
    for(int i=0;i<cmd;i++) key(m,CnCwKey.DOWN);
    key(m,CnCwKey.OK);
  }
  static void print(String label,CnCwMachine m) {
    System.out.println(label+" result="+m.state().result()+" status="+m.state().status()
      +" shown="+m.state().resultShown()+" error="+m.state().calculationState().isError()
      +" ans="+m.state().ans());
  }
  public static void main(String[] args) {
    CnCwMachine hex = fresh();
    open(hex,6,4);
    key(hex,CnCwKey.RIGHT); key(hex,CnCwKey.OK); key(hex,CnCwKey.OK);
    key(hex,CnCwKey.DIGIT_1); key(hex,CnCwKey.VAR_E); key(hex,CnCwKey.EXE);
    print("HEX 1E -> DEC expected 30",hex);
    System.out.println("HEX input cells="+hex.state().workflowInput().cells());

    CnCwMachine tiny = fresh();
    open(tiny,7,0); tiny.pasteExpression("0.000000000001"); key(tiny,CnCwKey.EXE);
    print("MatA 1e-12",tiny);
    System.out.println("MatA cells="+tiny.state().applicationResult().cells());
    open(tiny,7,7); key(tiny,CnCwKey.EXE);
    System.out.println("Trn MatA cells="+tiny.state().applicationResult().cells());

    CnCwMachine verify = fresh(); key(verify,CnCwKey.OK); key(verify,CnCwKey.TOOLS);
    key(verify,CnCwKey.DOWN); key(verify,CnCwKey.DOWN); key(verify,CnCwKey.OK);
    System.out.println("verification before="+verify.state().verificationMode());
    key(verify,CnCwKey.HOME); key(verify,CnCwKey.HOME); key(verify,CnCwKey.RIGHT); key(verify,CnCwKey.OK);
    System.out.println("verification after HOME HOME Statistics="+verify.state().verificationMode());
    open(verify,0,0); verify.pasteExpression("2+3"); key(verify,CnCwKey.EXE);
    print("return Calculate 2+3", verify);

    CnCwMachine mat = fresh(); open(mat,7,0); mat.pasteExpression("5"); key(mat,CnCwKey.EXE);
    open(mat,7,11); key(mat,CnCwKey.EXE);
    key(mat,CnCwKey.HOME); key(mat,CnCwKey.HOME); key(mat,CnCwKey.OK);
    key(mat,CnCwKey.HOME); key(mat,CnCwKey.HOME);
    for(int i=0;i<7;i++) key(mat,CnCwKey.RIGHT); key(mat,CnCwKey.OK);
    for(int i=0;i<15;i++) key(mat,CnCwKey.DOWN); key(mat,CnCwKey.OK);
    print("MatAns after switching via double HOME",mat);

    CnCwMachine freq = fresh(); open(freq,1,9);
    freq.pasteExpression("10"); key(freq,CnCwKey.OK); freq.pasteExpression("-1"); key(freq,CnCwKey.EXE);
    print("invalid negative freq",freq);
    key(freq,CnCwKey.OK);
    System.out.println("after error OK expression="+freq.state().expression()
      +" cells="+freq.state().workflowInput().cells());
    freq.selectWorkflowCell(0,0);
    System.out.println("after selecting x cells="+freq.state().workflowInput().cells());

    CnCwMachine verify2 = fresh(); key(verify2,CnCwKey.OK); key(verify2,CnCwKey.TOOLS);
    key(verify2,CnCwKey.DOWN); key(verify2,CnCwKey.DOWN); key(verify2,CnCwKey.OK);
    key(verify2,CnCwKey.HOME); key(verify2,CnCwKey.HOME); key(verify2,CnCwKey.RIGHT); key(verify2,CnCwKey.OK);
    key(verify2,CnCwKey.HOME); key(verify2,CnCwKey.HOME); key(verify2,CnCwKey.OK);
    verify2.pasteExpression("2+3"); key(verify2,CnCwKey.EXE);
    print("double HOME switch twice then Calculate 2+3",verify2);
  }
}
