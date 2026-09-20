import com.codex.fx991.core.cw.*;
import com.codex.fx991.core.mode.CnCwModel;
import com.codex.fx991.core.math.NumericAnalysis;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StateProbe {
    static CnCwMachine app(int index) {
        CnCwMachine m = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        for (int n=0; n<index; n++) m.dispatch(CnCwKey.RIGHT);
        m.dispatch(CnCwKey.OK);
        return m;
    }
    static void verify(CnCwMachine m) {
        m.dispatch(CnCwKey.TOOLS);
        m.dispatch(CnCwKey.DOWN);
        m.dispatch(CnCwKey.DOWN);
        m.dispatch(CnCwKey.OK);
    }
    static void show(String label, CnCwMachine m) {
        System.out.println(label+" => expr="+m.state().expression()+" result="+m.state().result()
            +" phase="+m.state().calculationState().phase()+" verify="+m.state().verificationMode()
            +" ans="+m.state().ans()+" status="+m.state().status());
    }
    public static void main(String[] args) throws Exception {
        CnCwMachine complex=app(5);
        verify(complex);
        complex.pasteExpression("i^2=-1");
        complex.dispatch(CnCwKey.EXE);
        show("Complex verify i^2=-1 (expected True)", complex);
        complex.dispatch(CnCwKey.AC);
        complex.pasteExpression("i^2");
        complex.dispatch(CnCwKey.EXE);
        show("Complex verify without relation", complex);
        CnCwMachine basic=app(0);
        basic.pasteExpression("7"); basic.dispatch(CnCwKey.EXE); basic.dispatch(CnCwKey.AC);
        verify(basic);
        basic.pasteExpression("1=1"); basic.dispatch(CnCwKey.EXE);
        show("Calculate verification overwrites Ans?", basic);
        CnCwMachine history=app(0);
        history.pasteExpression("1/2"); history.dispatch(CnCwKey.EXE);
        history.dispatch(CnCwKey.AC);
        history.pasteExpression("1/3"); history.dispatch(CnCwKey.EXE);
        history.dispatch(CnCwKey.UP); history.dispatch(CnCwKey.UP);
        show("Recall earlier 1/2", history);
        history.dispatch(CnCwKey.FORMAT);
        while (!history.state().menuItems().get(history.state().selectedIndex()).id().equals("improper")) {
            history.dispatch(CnCwKey.DOWN);
        }
        history.dispatch(CnCwKey.OK);
        show("Earlier 1/2 FORMAT improper (expected 1/2)", history);
        for(String input:new String[]{"log(100)","0^0.5","ln(0)"}) {
            CnCwMachine c=app(5); c.pasteExpression(input); c.dispatch(CnCwKey.EXE); show("Complex user-entry "+input,c);
        }
        for (int i=0;i<10;i++) {
            CnCwMachine m=app(i); m.dispatch(CnCwKey.TOOLS);
            System.out.println("Tools app="+i+" verify="+m.state().menuItems().stream().anyMatch(c->c.id().equals("verify")));
        }
        // A finite numerical operation, interrupted before entering its loop.
        AtomicInteger calls=new AtomicInteger();
        Thread.currentThread().interrupt();
        double v=NumericAnalysis.sum(x->{calls.incrementAndGet(); return x;},1,10000);
        System.out.println("Already-interrupted sum: calls="+calls+" result="+v+" stillInterrupted="+Thread.interrupted());
        // Bound the experiment with latches; never launch an unbounded heavy computation.
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger afterCancel=new AtomicInteger();
        Future<?> first=worker.submit(()->NumericAnalysis.sum(x->{
            if(x==1) { started.countDown(); boolean ready=false; while(!ready) {
                try { ready=release.await(2,TimeUnit.SECONDS); } catch(InterruptedException ignored) {}
            }}
            afterCancel.incrementAndGet(); return x;
        },1,10000));
        started.await(2,TimeUnit.SECONDS);
        first.cancel(true);
        Future<Integer> second=worker.submit(()->2);
        System.out.println("Canceled old job, new result available="+second.isDone());
        release.countDown();
        System.out.println("Next result="+second.get(3,TimeUnit.SECONDS)+" old job continued iterations="+afterCancel.get());
        worker.shutdownNow();
    }
}
