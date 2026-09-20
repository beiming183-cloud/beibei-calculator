package com.codex.fx991smooth;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.view.MotionEvent;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwCursorPath;
import com.codex.fx991.core.cw.CnCwUiState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Actual View input behavior on host API substitutes; no rendering/device claims. */
public final class CalculatorViewHostSuite {
    private static int failed;

    public static void main(String[] args) throws Exception {
        run("OK is evaluated off the main thread", () -> executionIsAsync(CnCwKey.OK));
        run("ENTER is evaluated off the main thread", () -> executionIsAsync(CnCwKey.ENTER));
        run("repeated execute shares the in-flight calculation", CalculatorViewHostSuite::repeatedExecute);
        run("completed worker awaiting display is not restarted", CalculatorViewHostSuite::postedExecute);
        run("switching application cancels old calculation", CalculatorViewHostSuite::switchCancelsCalculation);
        run("error OK dismisses inline before fast editing", CalculatorViewHostSuite::errorOkStaysInline);
        run("error text is not scientific notation", CalculatorViewHostSuite::errorIsNotScientific);
        run("structured result does not draw serialized input or cursor", CalculatorViewHostSuite::structuredResultDrawing);
        run("structured result touch does not edit hidden input", CalculatorViewHostSuite::structuredResultTouch);
        run("home card tap opens selected application", CalculatorViewHostSuite::homeCardTap);
        run("result copy and return preserve workflow cells", CalculatorViewHostSuite::resultCopyAndReturn);
        run("home swipe cancel gap and focus loss do not activate", CalculatorViewHostSuite::homeCanceledTaps);
        run("all visible home slots use current viewport", CalculatorViewHostSuite::homeViewportTaps);
        run("function table navigation survives read-only result screen", CalculatorViewHostSuite::tableResultNavigation);
        run("paste invalidates a queued result", CalculatorViewHostSuite::pasteInvalidates);
        run("paste rejects an already posted old result", CalculatorViewHostSuite::postedResultInvalidates);
        run("touch cursor invalidates a queued result", CalculatorViewHostSuite::cursorInvalidates);
        run("touch selection invalidates a queued result", CalculatorViewHostSuite::selectionInvalidates);
        run("workflow cell selection invalidates a queued result", CalculatorViewHostSuite::cellInvalidates);
        run("workflow OK advances synchronously before next input", CalculatorViewHostSuite::workflowOkStaysInline);
        run("crossed display/key release stops repeat", CalculatorViewHostSuite::crossedRelease);
        run("ordinary held key repeats only until release", CalculatorViewHostSuite::ordinaryRepeat);
        run("focus loss stops repeat", CalculatorViewHostSuite::focusStopsRepeat);
        run("focus loss cancels a queued calculation", CalculatorViewHostSuite::focusCancelsEvaluation);
        run("detach stops repeat", CalculatorViewHostSuite::detachStopsRepeat);
        run("reattached view can evaluate again", CalculatorViewHostSuite::reattachEvaluates);
        if (failed > 0) throw new AssertionError(failed + " View regressions failed");
    }

    private static CalculatorView view() {
        Handler.reset();
        android.app.AlertDialog.items = null;
        CalculatorView view = new CalculatorView(new Context());
        view.layout(0, 0, 420, 933);
        return view;
    }

    private static void ratioResult(CalculatorView view) throws Exception {
        key(view, CnCwKey.HOME);
        for(int i=0;i<9;i++)key(view,CnCwKey.RIGHT);
        key(view,CnCwKey.OK); key(view,CnCwKey.OK);
        paste(view,"101"); key(view,CnCwKey.RIGHT);
        paste(view,"202"); key(view,CnCwKey.RIGHT);
        paste(view,"303"); key(view,CnCwKey.EXE); settle(view);
        check(state(view).hasStructuredApplicationResult(),"ratio result missing");
    }

    private static void structuredResultDrawing() throws Exception {
        CalculatorView view=view();
        try {
            ratioResult(view);
            android.graphics.Canvas canvas=new android.graphics.Canvas();
            Method draw=CalculatorView.class.getDeclaredMethod("drawApplicationScreen",android.graphics.Canvas.class,RectF.class);
            draw.setAccessible(true);
            draw.invoke(view,canvas,new PhysicalKeyLayout(1).displayBounds(420,933));
            check(!String.join("",canvas.texts).contains("101"),"serialized input is drawn: "+canvas.texts);
            check(canvas.lines==0,"editor cursor line leaked into key/value result");
            check(canvas.texts.stream().anyMatch(text -> text.contains("151.5")),"result value was hidden");
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void structuredResultTouch() throws Exception {
        CalculatorView view=view();
        try {
            ratioResult(view);
            String original=state(view).expression();
            touch(view,MotionEvent.ACTION_DOWN,80,70);
            touch(view,MotionEvent.ACTION_UP,80,70);
            check(state(view).resultShown(),"tap exited result and edited invisible input");
            touch(view,MotionEvent.ACTION_DOWN,80,70); Handler.advanceBy(360);
            check(!state(view).hasSelection(),"long press selected hidden source");
            check(android.app.AlertDialog.items != null
                    && java.util.Arrays.asList(android.app.AlertDialog.items).contains("复制计算结果"),"result copy menu missing");
            check(original.equals(state(view).expression()),"result gesture changed original inputs");
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void touch(CalculatorView view,int action,float x,float y) {
        view.onTouchEvent(event(action,0,new int[]{0},new float[]{x},new float[]{y}));
    }

    private static void homeCardTap() throws Exception {
        CalculatorView view=view();
        try {
            key(view,CnCwKey.HOME);
            touch(view,MotionEvent.ACTION_DOWN,210,85);
            touch(view,MotionEvent.ACTION_UP,210,85);
            check(state(view).application()==com.codex.fx991.core.mode.ApplicationMode.STATISTICS
                    && state(view).applicationLanding(),"statistics card tap did not open statistics");
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void resultCopyAndReturn() throws Exception {
        CalculatorView view=view();
        try {
            ratioResult(view);
            touch(view,MotionEvent.ACTION_DOWN,80,70);Handler.advanceBy(360);
            var actions=java.util.Arrays.asList(android.app.AlertDialog.items);
            check(!actions.contains("复制计算过程"),"serialized workflow process is exposed");
            android.app.AlertDialog.listener.onClick(null,actions.indexOf("复制计算结果"));
            ClipboardManager clipboard=(ClipboardManager)view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            String copied=clipboard.getPrimaryClip().getItemAt(0).coerceToText(view.getContext()).toString();
            check(copied.contains("151.5")&&!copied.contains("101")&&!copied.contains("│"),"wrong result clipboard: "+copied);
            touch(view,MotionEvent.ACTION_UP,80,70);
            key(view,CnCwKey.BACK);
            check(!state(view).resultShown()&&state(view).hasWorkflowInput(),"BACK did not restore form");
            check(state(view).workflowInput().cells().containsAll(java.util.List.of("101","202","303")),"form cells lost");
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void tableResultNavigation() throws Exception {
        CalculatorView view=view();
        try {
            key(view,CnCwKey.HOME);key(view,CnCwKey.RIGHT);key(view,CnCwKey.RIGHT);
            key(view,CnCwKey.OK);key(view,CnCwKey.DOWN);key(view,CnCwKey.OK);
            paste(view,"x");key(view,CnCwKey.OK);
            paste(view,"0");key(view,CnCwKey.OK);
            paste(view,"20");key(view,CnCwKey.OK);paste(view,"1");
            key(view,CnCwKey.EXE);settle(view);
            check(state(view).hasStructuredApplicationResult(),"table result missing");
            key(view,CnCwKey.DOWN);check((int)field(view,"tableFirstRow")==1,"table down");
            key(view,CnCwKey.PAGE_DOWN);check((int)field(view,"tableFirstRow")==5,"table page down");
            key(view,CnCwKey.PAGE_UP);check((int)field(view,"tableFirstRow")==1,"table page up");
            touch(view,MotionEvent.ACTION_DOWN,80,70);touch(view,MotionEvent.ACTION_UP,80,70);
            check(state(view).resultShown(),"table tap activated hidden editor");
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void homeCanceledTaps() throws Exception {
        CalculatorView view=view();
        try {
            for(int kind=0;kind<4;kind++) {
                key(view,CnCwKey.HOME);
                touch(view,MotionEvent.ACTION_DOWN,210,85);
                check(state(view).screen()==com.codex.fx991.core.cw.CnCwScreen.HOME,"card opened before release");
                if(kind==0) {touch(view,MotionEvent.ACTION_MOVE,240,85);touch(view,MotionEvent.ACTION_MOVE,210,85);}
                if(kind==1)touch(view,MotionEvent.ACTION_CANCEL,210,85);
                if(kind==2)view.onWindowFocusChanged(false);
                touch(view,MotionEvent.ACTION_UP,kind==3?10:210,85);
                check(state(view).screen()==com.codex.fx991.core.cw.CnCwScreen.HOME,"canceled card activated: "+kind);
            }
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void homeViewportTaps() throws Exception {
        CalculatorView view=view();
        try {
            RectF lcd=new PhysicalKeyLayout(1).displayBounds(420,933);
            Method bounds=CalculatorView.class.getDeclaredMethod("homeCardBounds",RectF.class,int.class,RectF.class);
            bounds.setAccessible(true);
            for(int page=0;page<2;page++)for(int slot=0;slot<6;slot++) {
                key(view,CnCwKey.HOME);
                if(page==1)key(view,CnCwKey.PAGE_DOWN);
                if(slot>=state(view).homeVisibleItems().size())continue;
                String expected=state(view).homeVisibleItems().get(slot).id();
                RectF card=new RectF();bounds.invoke(view,lcd,slot,card);
                touch(view,MotionEvent.ACTION_DOWN,card.centerX(),card.centerY());
                touch(view,MotionEvent.ACTION_UP,card.centerX(),card.centerY());
                check(state(view).application()!=null&&state(view).application().name().equals(expected),"wrong home item "+expected);
            }
        } finally {view.onDetachedFromWindow();Handler.reset();}
    }

    private static void errorIsNotScientific() throws Exception {
        CalculatorView view = view();
        try {
            Method draw = CalculatorView.class.getDeclaredMethod("drawNaturalScientificResult",
                    android.graphics.Canvas.class, String.class, RectF.class,
                    float.class, float.class, float.class);
            draw.setAccessible(true);
            for (String value : new String[] { "Math ERROR", "Syntax ERROR", "TIMEOUT",
                    "1Eoops", "NaNE3", "1E2i", "1+2E3", "InfinityE2" }) {
                check(Boolean.FALSE.equals(draw.invoke(view, null, value,
                        new RectF(0, 0, 400, 300), 0f, 300f, 380f)), value);
            }
        } finally {
            view.onDetachedFromWindow();
            Handler.reset();
        }
    }

    private static Object field(CalculatorView view, String name) throws Exception {
        Field field = CalculatorView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }

    private static void key(CalculatorView view, CnCwKey key) throws Exception {
        Method method = CalculatorView.class.getDeclaredMethod("dispatchKey", CnCwKey.class);
        method.setAccessible(true);
        method.invoke(view, key);
    }

    private static CnCwUiState state(CalculatorView view) throws Exception {
        return (CnCwUiState) field(view, "state");
    }

    private static void paste(CalculatorView view, String text) throws Exception {
        ClipboardManager clipboard = (ClipboardManager) view.getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("test", text));
        Method method = CalculatorView.class.getDeclaredMethod("pasteClipboardText");
        method.setAccessible(true);
        method.invoke(view);
    }

    private static ExecutorService executor(CalculatorView view) throws Exception {
        return (ExecutorService) field(view, "evaluationExecutor");
    }

    private static CountDownLatch block(CalculatorView view) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        executor(view).submit(() -> {
            started.countDown();
            try { release.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        check(started.await(2, TimeUnit.SECONDS), "worker did not start");
        return release;
    }

    private static void settle(CalculatorView view) throws Exception {
        executor(view).submit(() -> {}).get(2, TimeUnit.SECONDS);
        Handler.advanceBy(0);
    }

    private static void executionIsAsync(CnCwKey execute) throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "1+1");
            key(view, execute);
            check(!state(view).resultShown(), "evaluation ran synchronously on the caller");
            check(field(view, "pendingEvaluation") != null, "no queued task");
            release.countDown();
            settle(view);
            check("2".equals(state(view).result()), "worker result missing");
        } finally {
            release.countDown();
            view.onDetachedFromWindow();
            Handler.reset();
        }
    }

    private static void repeatedExecute() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "1+1"); key(view, CnCwKey.EXE);
            Object original = field(view, "pendingEvaluation");
            for (int i = 0; i < 50; i++) key(view, CnCwKey.EXE);
            check(field(view, "pendingEvaluation") == original, "duplicate EXE restarted identical calculation");
            paste(view, "+1"); key(view, CnCwKey.EXE);
            check(field(view, "pendingEvaluation") != original, "edited expression must start new calculation");
            release.countDown(); settle(view);
            check("3".equals(state(view).result()), "latest calculation lost");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void postedExecute() throws Exception {
        CalculatorView view = view();
        try {
            paste(view, "2+2"); key(view, CnCwKey.EXE);
            executor(view).submit(() -> {}).get(2, TimeUnit.SECONDS);
            Object original = field(view, "pendingEvaluation");
            key(view, CnCwKey.EXE);
            check(field(view, "pendingEvaluation") == original, "ready result was discarded and recalculated");
            settle(view); check("4".equals(state(view).result()), "ready result missing");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void switchCancelsCalculation() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "1+1"); key(view, CnCwKey.EXE);
            java.util.concurrent.Future<?> original = (java.util.concurrent.Future<?>) field(view, "pendingEvaluation");
            key(view, CnCwKey.HOME); key(view, CnCwKey.RIGHT); key(view, CnCwKey.OK);
            check(original.isCancelled() && field(view, "pendingEvaluation") == null, "old mode calculation survived");
            release.countDown(); settle(view);
            check(state(view).application() == com.codex.fx991.core.mode.ApplicationMode.STATISTICS
                    && state(view).applicationLanding(), "old result replaced statistics landing");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void pasteInvalidates() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "1");
            key(view, CnCwKey.EXE);
            paste(view, "+2");
            check("1+2".equals(state(view).expression()), "paste not accepted");
            release.countDown();
            settle(view);
            check("1+2".equals(state(view).expression()) && !state(view).resultShown(),
                    "old worker replaced pasted expression with " + state(view).expression());
        } finally {
            release.countDown();
            view.onDetachedFromWindow();
            Handler.reset();
        }
    }

    private static void errorOkStaysInline() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = null;
        try {
            paste(view, "1/0"); key(view, CnCwKey.EXE); settle(view);
            check(state(view).calculationState().isError(), "expected calculation error");
            release = block(view);
            key(view, CnCwKey.OK);
            check(!state(view).calculationState().isError(), "error dismissal was incorrectly queued");
            check(field(view, "pendingEvaluation") == null, "dismissal started an evaluation job");
            key(view, CnCwKey.DIGIT_2);
            check(!state(view).calculationState().isError(), "fast editing remains on error overlay");
        } finally {
            if (release != null) release.countDown();
            view.onDetachedFromWindow(); Handler.reset();
        }
    }

    private static void cursorInvalidates() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "123");
            key(view, CnCwKey.EXE);
            Method move = CalculatorView.class.getDeclaredMethod("moveCursorAtomically",
                    CnCwCursorPath.class, boolean.class);
            move.setAccessible(true);
            move.invoke(view, CnCwCursorPath.rootBoundary(0), false);
            release.countDown();
            settle(view);
            check(state(view).cursor() == 0 && !state(view).resultShown(),
                    "old result replaced touched cursor");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void postedResultInvalidates() throws Exception {
        CalculatorView view = view();
        try {
            paste(view, "1"); key(view, CnCwKey.EXE);
            // Worker has finished; its UI callback is pending, so cancelling the
            // Future alone cannot protect the newer text. Revision must change.
            executor(view).submit(() -> {}).get(2, TimeUnit.SECONDS);
            paste(view, "+2");
            Handler.advanceBy(0);
            check("1+2".equals(state(view).expression()) && !state(view).resultShown(),
                    "already posted callback replaced newer text");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void selectionInvalidates() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "123");
            key(view, CnCwKey.EXE);
            RectF lcd = new PhysicalKeyLayout(1).displayBounds(420, 933);
            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0, new int[]{4},
                    new float[]{lcd.left + 12}, new float[]{lcd.top + 35}));
            Handler.advanceBy(360);
            check(state(view).hasSelection(), "long press did not create selection");
            release.countDown();
            settle(view);
            check(state(view).hasSelection() && !state(view).resultShown(),
                    "old result replaced touch selection");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void cellInvalidates() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            key(view, CnCwKey.HOME);
            for (int i = 0; i < 9; i++) key(view, CnCwKey.RIGHT);
            key(view, CnCwKey.OK);
            key(view, CnCwKey.OK);
            check(state(view).hasWorkflowInput(), "ratio workflow not opened");
            paste(view, "1"); key(view, CnCwKey.RIGHT);
            paste(view, "2"); key(view, CnCwKey.RIGHT);
            paste(view, "3"); key(view, CnCwKey.EXE);
            RectF lcd = new PhysicalKeyLayout(1).displayBounds(420, 933);
            Method select = CalculatorView.class.getDeclaredMethod("selectWorkflowCellAt",
                    float.class, float.class);
            select.setAccessible(true);
            select.invoke(view, lcd.left + 45, lcd.top + lcd.height() * .22f + 24);
            release.countDown(); settle(view);
            check(state(view).workflowInput().selectedColumn() == 0 && !state(view).resultShown(),
                    "old result replaced workflow focus");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void workflowOkStaysInline() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            key(view, CnCwKey.HOME);
            for (int i = 0; i < 9; i++) key(view, CnCwKey.RIGHT);
            key(view, CnCwKey.OK); key(view, CnCwKey.OK);
            paste(view, "1"); key(view, CnCwKey.OK); key(view, CnCwKey.DIGIT_2);
            check(state(view).workflowInput().selectedColumn() == 1,
                    "OK cell navigation was deferred and lost to the following digit");
            check("2".equals(state(view).expression()), "next digit was appended to the old cell");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static RectF digit() {
        return new PhysicalKeyLayout(1).arrange(420, 933).stream()
                .filter(hit -> hit.definition.key == CnCwKey.DIGIT_1)
                .findFirst().orElseThrow().touchBounds;
    }

    private static MotionEvent event(int action, int index, int[] ids, float[] xs, float[] ys) {
        return new MotionEvent(action, index, ids, xs, ys);
    }

    private static void downDigit(CalculatorView view) {
        RectF bounds = digit();
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0, new int[]{9},
                new float[]{bounds.centerX()}, new float[]{bounds.centerY()}));
    }

    private static void crossedRelease() throws Exception {
        CalculatorView view = view();
        try {
            RectF bounds = digit();
            RectF lcd = new PhysicalKeyLayout(1).displayBounds(420, 933);
            float x = lcd.centerX(), y = lcd.centerY();
            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0, new int[]{4},
                    new float[]{x}, new float[]{y}));
            view.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 1, new int[]{4, 9},
                    new float[]{x, bounds.centerX()}, new float[]{y, bounds.centerY()}));
            view.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0, new int[]{4, 9},
                    new float[]{x, bounds.centerX()}, new float[]{y, bounds.centerY()}));
            view.onTouchEvent(event(MotionEvent.ACTION_UP, 0, new int[]{9},
                    new float[]{bounds.centerX()}, new float[]{bounds.centerY()}));
            String before = state(view).expression();
            Handler.advanceBy(1000);
            check(before.equals(state(view).expression()),
                    "key kept repeating after both fingers released: " + state(view).expression());
            check(!((CnCwTouchRouter) field(view, "touchRouter")).isPressed(CnCwKey.DIGIT_1),
                    "key remains pressed");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void focusStopsRepeat() throws Exception {
        CalculatorView view = view();
        try {
            downDigit(view);
            view.onWindowFocusChanged(false);
            String before = state(view).expression();
            Handler.advanceBy(1000);
            check(before.equals(state(view).expression()), "repeat continued without window focus");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void ordinaryRepeat() throws Exception {
        CalculatorView view = view();
        try {
            downDigit(view);
            Handler.advanceBy(500);
            check(state(view).expression().length() > 1, "held key did not repeat");
            RectF bounds = digit();
            view.onTouchEvent(event(MotionEvent.ACTION_UP, 0, new int[]{9},
                    new float[]{bounds.centerX()}, new float[]{bounds.centerY()}));
            String before = state(view).expression();
            Handler.advanceBy(1000);
            check(before.equals(state(view).expression()), "repeat survived release");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void focusCancelsEvaluation() throws Exception {
        CalculatorView view = view();
        CountDownLatch release = block(view);
        try {
            paste(view, "1+1"); key(view, CnCwKey.EXE);
            view.onWindowFocusChanged(false);
            check(field(view, "pendingEvaluation") == null, "background task not cancelled");
            release.countDown(); settle(view);
            check(!state(view).resultShown() && !(boolean) field(view, "evaluating"),
                    "inactive window published a result");
        } finally { release.countDown(); view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void reattachEvaluates() throws Exception {
        CalculatorView view = view();
        try {
            view.onDetachedFromWindow();
            view.onAttachedToWindow();
            paste(view, "2"); key(view, CnCwKey.EXE); settle(view);
            check("2".equals(state(view).result()), "reattached executor did not evaluate");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void detachStopsRepeat() throws Exception {
        CalculatorView view = view();
        try {
            downDigit(view);
            view.onDetachedFromWindow();
            String before = state(view).expression();
            Handler.advanceBy(1000);
            check(before.equals(state(view).expression()), "repeat continued after detach");
        } finally { view.onDetachedFromWindow(); Handler.reset(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface Test { void run() throws Exception; }

    private static void run(String name, Test test) {
        try { test.run(); System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }
}
