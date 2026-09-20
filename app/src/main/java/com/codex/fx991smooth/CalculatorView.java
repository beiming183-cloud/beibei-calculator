package com.codex.fx991smooth;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwCursorPath;
import com.codex.fx991.core.cw.CnCwExpressionNode;
import com.codex.fx991.core.cw.CnCwSemanticSpan;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwScreen;
import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.mode.CnCwModel;
import com.codex.fx991.core.ui.Cw991LayoutMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.math.BigDecimal;

/**
 * Retained single-canvas Android adapter for the clean-room CN CW state machine.
 *
 * <p>Touch-down commits ordinary keys immediately; EXE uses an isolated
 * background snapshot for heavy evaluation. Pointer-up only releases the
 * pressed visual, so two overlapping keys are committed in down order and can
 * never be duplicated by crossed releases.</p>
 */
public final class CalculatorView extends View {
    private static final java.util.regex.Pattern SCI_MANTISSA =
            java.util.regex.Pattern.compile("[+\\-−]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private static final java.util.regex.Pattern SCI_EXPONENT =
            java.util.regex.Pattern.compile("[+\\-−]?[0-9]+");
    private final android.os.Handler gestureHandler = new android.os.Handler();
    private Runnable displayLongPress;
    private Runnable keyRepeat;
    private CnCwKey repeatingKey;
    private int repeatingPointerId = -1;
    private boolean displayPressed;
    private int displayPointerId = -1;
    private boolean displaySelectionMode;
    private boolean displayLongPressTriggered;
    private float displayDownX;
    private float displayDownY;
    private int lastDragCursor = -1;
    private CnCwCursorPath lastDragSemantic;
    /** -1 = left handle, 0 = choose from drag direction, +1 = right handle. */
    private int selectionDragEdge;
    private int homePointerId = -1;
    private int pressedHomeIndex = -1;
    private int pressedHomeViewport;
    private float homeDownX, homeDownY;
    private boolean selectionTapCandidate;
    private static final int BODY_EDGE = Color.rgb(48, 55, 52);
    /* A warm neutral shell keeps the calculator from looking washed out while
       the cool LCD and ochre function layer remain immediately scannable. */
    private static final int BODY = Color.rgb(242, 243, 237);
    private static final int BODY_SHADOW = Color.rgb(169, 176, 170);
    private static final int LCD_FRAME = Color.rgb(81, 105, 95);
    private static final int LCD = Color.rgb(220, 234, 222);
    private static final int LCD_MID = Color.rgb(190, 211, 198);
    private static final int LCD_DARK = Color.rgb(24, 58, 45);
    private static final int LCD_INK = Color.rgb(13, 38, 28);
    private static final int KEY_FUNCTION = Color.rgb(246, 247, 241);
    private static final int KEY_NUMBER = Color.rgb(255, 255, 250);
    private static final int KEY_OPERATOR = Color.rgb(232, 236, 230);
    private static final int KEY_CONTROL = Color.rgb(226, 231, 226);
    private static final int KEY_NAV = Color.rgb(186, 199, 190);
    private static final int KEY_OK = Color.rgb(165, 184, 173);
    private static final int KEY_SHIFT = Color.rgb(250, 250, 246);
    private static final int KEY_ACTION = Color.rgb(205, 216, 209);
    private static final int KEY_EXECUTE = Color.rgb(163, 185, 173);
    private static final int KEY_BORDER = Color.rgb(163, 173, 166);
    private static final int KEY_HIGHLIGHT = Color.rgb(255, 255, 252);
    private static final int KEY_SHADOW = Color.argb(92, 42, 49, 45);
    private static final int INK_LIGHT = Color.rgb(255, 255, 249);
    private static final int INK_DARK = Color.rgb(18, 30, 24);
    private static final int SHIFT_INK = Color.rgb(137, 102, 13);
    private static final Typeface FACE_NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface FACE_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface FACE_BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final Path iconPath = new Path();
    private final RectF scratch = new RectF();
    private final Rect textBounds = new Rect();
    private final List<KeyHit> hitMap = new ArrayList<>(64);
    /** Geometry/legends are owned by the physical 991 layout, not the painter. */
    private final PhysicalKeyLayout physicalLayout;
    private final CnCwTouchRouter touchRouter = new CnCwTouchRouter();
    private ExecutorService evaluationExecutor = createEvaluationExecutor();
    private CnCwMachine machine;
    private CnCwUiState state;
    private Future<?> pendingEvaluation;
    private CnCwKey pendingEvaluationKey;
    private long inputRevision;
    private boolean evaluating;
    /** View-local viewport for core-owned TABLE results; never changes math state. */
    private CnCwModeEngine.ModeResult tableResult;
    private int tableFirstRow;

    private static ExecutorService createEvaluationExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "cn-cw-evaluator");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CalculatorView(Context context) {
        super(context);
        physicalLayout = new PhysicalKeyLayout(getResources().getDisplayMetrics().density);
        machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        // Launch directly into a blank Calculate work area.  HOME remains
        // available as a deliberate key action, but opening the app must not
        // make a routine calculation pay for a menu transition first.
        state = machine.dispatch(CnCwKey.OK);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setKeepScreenOn(false);
        setContentDescription(BuildConfig.MODEL_LABEL
                + " independent scientific calculator, HOME screen");
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        rebuildHitMap(width, height);
    }

    private void rebuildHitMap(int width, int height) {
        hitMap.clear();
        for (PhysicalKeyLayout.Hit hit : physicalLayout.arrange(width, height)) {
            PhysicalKeyLayout.Definition definition = hit.definition;
            hitMap.add(new KeyHit(new KeySpec(definition.key, definition.primary,
                    definition.secondary, adaptKind(definition.kind), definition.circular),
                    new RectF(hit.touchBounds), new RectF(hit.visualBounds), hit.visual));
        }
    }

    private static KeyKind adaptKind(PhysicalKeyLayout.Kind kind) {
        return switch (kind) {
            case NUMBER -> KeyKind.NUMBER;
            case OPERATOR -> KeyKind.OPERATOR;
            case FUNCTION -> KeyKind.FUNCTION;
            case CONTROL, CONTEXT -> KeyKind.STRIP;
            case NAV -> KeyKind.NAV;
            case OK -> KeyKind.OK;
            case SHIFT -> KeyKind.SHIFT;
            case ACTION -> KeyKind.ACTION;
            case EXECUTE -> KeyKind.EQUALS;
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BODY_EDGE);
        paint.setColor(BODY_SHADOW);
        canvas.drawRoundRect(dp(3), dp(5), getWidth() - dp(3), getHeight() + dp(24),
                dp(18), dp(18), paint);
        paint.setColor(BODY);
        canvas.drawRoundRect(dp(4), dp(3), getWidth() - dp(4), getHeight() + dp(18),
                dp(17), dp(17), paint);
        drawDisplay(canvas);
        drawNavigationPlate(canvas);
        drawPageRocker(canvas);
        for (KeyHit hit : hitMap) drawKey(canvas, hit);
    }

    private void drawHeader(Canvas canvas) {
        RectF lcd = displayBounds(getWidth());
        float modelBaseline = Math.max(dp(24), lcd.top - dp(45));
        float subtitleBaseline = modelBaseline + dp(9);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(FACE_BOLD);
        paint.setTextSize(sp(11.5f));
        paint.setColor(Color.rgb(68, 74, 70));
        canvas.drawText("CN-991", dp(18), modelBaseline, paint);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(4.6f));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(99, 105, 101));
        canvas.drawText("SCIENTIFIC CALCULATOR", dp(18), subtitleBaseline, paint);
        float panelWidth = lcd.width() * 0.37f;
        float panelHeight = panelWidth * 0.43f;
        float panelRight = lcd.right - dp(6);
        float panelTop = lcd.top - panelHeight - dp(12);
        scratch.set(panelRight - panelWidth, panelTop, panelRight, panelTop + panelHeight);
        paint.setColor(Color.rgb(91, 96, 87));
        canvas.drawRoundRect(scratch, dp(7), dp(7), paint);
        paint.setColor(Color.rgb(137, 143, 130));
        for (float x = scratch.left + dp(6); x < scratch.right; x += dp(6)) {
            canvas.drawLine(x, scratch.top + dp(3), x + dp(8), scratch.bottom - dp(3), paint);
        }
    }

    private RectF displayBounds(float width) {
        return physicalLayout.displayBounds(width, getHeight());
    }

    private void drawDisplay(Canvas canvas) {
        RectF lcd = displayBounds(getWidth());
        paint.setColor(LCD_FRAME);
        canvas.drawRoundRect(lcd.left - dp(3), lcd.top - dp(3), lcd.right + dp(3),
                lcd.bottom + dp(3), dp(5), dp(5), paint);
        paint.setColor(LCD);
        canvas.drawRoundRect(lcd, dp(2), dp(2), paint);
        canvas.save();
        canvas.clipRect(lcd);
        if (!state.poweredOn()) {
            canvas.restore();
            return;
        }
        if (state.screen() == CnCwScreen.HOME) drawHomeScreen(canvas, lcd);
        else if (state.screen().isPopupMenu()) drawMenuScreen(canvas, lcd);
        else drawApplicationScreen(canvas, lcd);
        canvas.restore();
    }

    private void drawStatusBar(Canvas canvas, RectF lcd, String title) {
        // The physical LCD reserves only a thin indicator band; keeping it
        // short leaves room for the larger natural-display expression below.
        float barHeight = lcd.height() * 0.075f;
        paint.setColor(LCD_MID);
        canvas.drawRect(lcd.left, lcd.top, lcd.right, lcd.top + barHeight, paint);
        if (!title.isEmpty()) {
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextSize(sp(12f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(ellipsize(title, lcd.width() * 0.52f),
                    lcd.left + dp(5), centeredBaseline(lcd.top, lcd.top + barHeight), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(FACE_NORMAL);
        String indicators = (evaluating ? "…  " : "")
                + (state.shiftArmed() ? "S  " : "")
                + (state.verificationMode() ? "✓  " : "")
                + (state.engineeringMode() ? "E  " : "")
                + (state.overwriteMode() ? "O  " : "")
                + (state.statementSequenceActive() ? "▮▮  " : "")
                + (state.historyPrevious() ? "▲" : "")
                + (state.historyNext() ? "▼  " : state.historyPrevious() ? "  " : "")
                + state.settings().angleUnit().name().charAt(0)
                + "  " + compactDisplayMode();
        canvas.drawText(indicators, lcd.right - dp(5),
                centeredBaseline(lcd.top, lcd.top + barHeight), paint);
    }

    private void drawHomeScreen(Canvas canvas, RectF lcd) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(LCD_MID);
        canvas.drawRect(lcd.left, lcd.top, lcd.right, lcd.top + lcd.height() * 0.09f, paint);
        paint.setColor(LCD_DARK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(sp(10f));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("应用", lcd.left + dp(7),
                centeredBaseline(lcd.top, lcd.top + lcd.height() * 0.09f), paint);
        List<CnCwCommand> allItems = state.homeItems();
        List<CnCwCommand> items = state.homeVisibleItems();
        int start = state.homeViewportStart();
        float radius = dp(7f);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(sp(9f));
        paint.setColor(LCD_DARK);
        canvas.drawText((start / 6 + 1) + " / " + ((allItems.size() + 5) / 6),
                lcd.right - dp(7), centeredBaseline(lcd.top, lcd.top + lcd.height() * 0.09f), paint);
        for (int visibleIndex = 0; visibleIndex < items.size(); visibleIndex++) {
            int index = start + visibleIndex;
            homeCardBounds(lcd, visibleIndex, scratch);
            boolean selected = index == state.selectedIndex();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(selected ? 31 : 8, 22, 55, 42));
            canvas.drawRoundRect(scratch, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(selected ? 1.2f : 0.6f));
            paint.setColor(Color.argb(selected ? 180 : 40, 22, 55, 42));
            canvas.drawRoundRect(scratch, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);

            drawApplicationGlyph(canvas, items.get(visibleIndex).id(), scratch, LCD_INK);
            drawFittedCentered(canvas, items.get(visibleIndex).label(), scratch.centerX(),
                    scratch.centerY() + dp(9), scratch.centerY() + dp(25),
                    sp(12f), scratch.width() * 0.88f, sp(10.5f), LCD_INK, FACE_NORMAL);
        }
    }

    private void homeCardBounds(RectF lcd, int slot, RectF out) {
        float outer = dp(6f), columnGap = dp(6f), rowGap = dp(6f);
        float contentTop = lcd.top + lcd.height() * 0.115f;
        int columns = state.homeVisibleItems().size() <= 4 ? 2 : 3;
        float width = (lcd.width() - outer * 2 - columnGap * (columns - 1)) / columns;
        float height = (lcd.bottom - contentTop - outer * 2 - rowGap) / 2;
        float left = lcd.left + outer + (slot % columns) * (width + columnGap);
        float top = contentTop + outer + (slot / columns) * (height + rowGap);
        out.set(left, top, left + width, top + height);
    }

    private int homeItemAt(float x, float y) {
        if (state.screen() != CnCwScreen.HOME) return -1;
        RectF lcd = displayBounds(getWidth());
        for (int slot = 0; slot < state.homeVisibleItems().size(); slot++) {
            homeCardBounds(lcd, slot, scratch);
            if (scratch.contains(x, y)) return state.homeViewportStart() + slot;
        }
        return -1;
    }

    private boolean isStructuredResultScreen() {
        return state.screen().isApplication() && !state.applicationLanding()
                && state.resultShown() && state.hasStructuredApplicationResult();
    }

    private void drawApplicationGlyph(Canvas canvas, String id, RectF cell, int color) {
        float cx = cell.centerX();
        float cy = cell.centerY() - dp(10);
        float radius = dp(11);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.35f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        if ("CALCULATE".equals(id)) {
            canvas.drawRoundRect(cx-radius*1.2f,cy-radius*1.2f,cx+radius*1.2f,cy+radius*1.2f,dp(3),dp(3),paint);
            canvas.drawLine(cx-radius*.65f,cy-radius*.55f,cx+radius*.65f,cy-radius*.55f,paint);
            canvas.drawLine(cx-radius*.65f,cy+radius*.45f,cx-radius*.05f,cy+radius*.45f,paint);
            canvas.drawLine(cx-radius*.35f,cy+radius*.15f,cx-radius*.35f,cy+radius*.75f,paint);
            canvas.drawLine(cx+radius*.35f,cy+radius*.3f,cx+radius*.75f,cy+radius*.3f,paint);
            canvas.drawLine(cx+radius*.35f,cy+radius*.65f,cx+radius*.75f,cy+radius*.65f,paint);
        } else if ("STATISTICS".equals(id) || "DISTRIBUTION".equals(id)) {
            canvas.drawLine(cx - radius * 1.5f, cy + radius, cx + radius * 1.5f, cy + radius, paint);
            canvas.drawRect(cx - radius * 1.2f, cy, cx - radius * 0.6f, cy + radius, paint);
            canvas.drawRect(cx - radius * 0.3f, cy - radius * 0.7f,
                    cx + radius * 0.3f, cy + radius, paint);
            canvas.drawRect(cx + radius * 0.6f, cy - radius * 0.2f,
                    cx + radius * 1.2f, cy + radius, paint);
        } else if ("SPREADSHEET".equals(id) || "FUNCTION_TABLE".equals(id)) {
            canvas.drawRect(cx - radius * 1.4f, cy - radius, cx + radius * 1.4f, cy + radius, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
            canvas.drawLine(cx - radius * 1.4f, cy, cx + radius * 1.4f, cy, paint);
        } else if ("MATRIX".equals(id)) {
            for (int side = -1; side <= 1; side += 2) {
                float edge = cx + side * radius * 1.3f;
                canvas.drawLine(edge, cy-radius, edge, cy+radius, paint);
                canvas.drawLine(edge, cy-radius, edge-side*radius*.35f, cy-radius, paint);
                canvas.drawLine(edge, cy+radius, edge-side*radius*.35f, cy+radius, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            for(int row=-1;row<=1;row+=2) for(int col=-1;col<=1;col+=2)
                canvas.drawCircle(cx+col*radius*.45f,cy+row*radius*.5f,dp(1.5f),paint);
        } else if ("EQUATION".equals(id) || "INEQUALITY".equals(id)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(18f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("EQUATION".equals(id) ? "x = y" : "x < y", cx,
                    centeredBaseline(cy - radius, cy + radius), paint);
        } else if ("VECTOR".equals(id) || "COMPLEX".equals(id)) {
            canvas.drawLine(cx-radius,cy+radius,cx+radius*1.2f,cy+radius,paint);
            canvas.drawLine(cx-radius,cy+radius,cx-radius,cy-radius*1.2f,paint);
            canvas.drawLine(cx-radius,cy+radius,cx+radius*.8f,cy-radius*.8f,paint);
            if ("VECTOR".equals(id)) {
                canvas.drawLine(cx+radius*.8f,cy-radius*.8f,cx+radius*.1f,cy-radius*.8f,paint);
                canvas.drawLine(cx+radius*.8f,cy-radius*.8f,cx+radius*.8f,cy-radius*.1f,paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx+radius*.8f,cy-radius*.8f,dp(2),paint);
            }
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp("BASE_N".equals(id) ? 15f : 20f));
            paint.setTextAlign(Paint.Align.CENTER);
            String symbol = switch(id) {
                case "BASE_N" -> "01";
                case "RATIO" -> "a:b";
                default -> "ƒ";
            };
            canvas.drawText(symbol, cx,
                    centeredBaseline(cy - radius, cy + radius), paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMenuScreen(Canvas canvas, RectF lcd) {
        drawStatusBar(canvas, lcd, state.status());
        List<CnCwCommand> items = state.menuItems();
        if (items.isEmpty()) return;
        float top = lcd.top + lcd.height() * 0.145f;
        float bottom = lcd.bottom - dp(2);
        int visible = Math.min(5, items.size());
        int start = Math.max(0, Math.min(state.selectedIndex() - visible + 1,
                items.size() - visible));
        float rowHeight = (bottom - top) / visible;
        for (int visibleRow = 0; visibleRow < visible; visibleRow++) {
            int index = start + visibleRow;
            float rowTop = top + visibleRow * rowHeight;
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRect(lcd.left + dp(2), rowTop,
                        lcd.right - dp(2), rowTop + rowHeight, paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(18f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(items.get(index).label(), lcd.left + dp(6),
                    centeredBaseline(rowTop, rowTop + rowHeight), paint);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(12f));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(ellipsize(items.get(index).description(), lcd.width() * 0.47f),
                    lcd.right - dp(7), centeredBaseline(rowTop, rowTop + rowHeight), paint);
        }
        if (items.size() > visible) drawScrollBar(canvas, lcd, start, visible, items.size(), top, bottom);
    }

    private void drawApplicationScreen(Canvas canvas, RectF lcd) {
        String title = state.application() == null ? "计算" : state.application().chineseName();
        drawStatusBar(canvas, lcd, title);
        if (state.spreadsheetGrid()) {
            drawSpreadsheetGrid(canvas, lcd);
            return;
        }
        if (state.applicationLanding()) {
            drawModeLanding(canvas, lcd);
            return;
        }
        if (state.hasWorkflowInput() && !state.resultShown()) {
            drawWorkflowInput(canvas, lcd, state.workflowInput());
            return;
        }
        float contentTop = lcd.top + lcd.height() * 0.145f;
        float contentBottom = lcd.bottom - dp(3);
        float available = lcd.width() - dp(12);
        CnCwModeEngine.ModeResult structuredResult = state.resultShown()
                && state.hasStructuredApplicationResult() ? state.applicationResult() : null;
        boolean showExpandedAnsProcess = structuredResult == null
                && state.resultShown()
                && state.expression().contains("Ans")
                && !machine.calculationProcessDisplay().isBlank();
        if (structuredResult == null) {
            if (showExpandedAnsProcess) {
                drawInspectionProcess(canvas, machine.calculationProcessDisplay(), lcd,
                        contentTop, contentBottom, available);
            } else {
                drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,
                        contentBottom, available);
            }
            if (state.hasSelection()) {
                drawSelectionHandles(canvas, lcd, contentTop, contentBottom);
            }
        }
        if (structuredResult != null && structuredResult.layout() != CnCwModeEngine.ResultLayout.TEXT) {
            drawStructuredApplicationResult(canvas, structuredResult, lcd,
                    contentTop, contentBottom, available);
        } else if (state.resultShown() && !state.result().isEmpty()) {
            String[] lines = decimalDisplayResult(state.result()).split("\\n", -1);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (lines.length == 1) {
                if (!drawNaturalScientificResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)
                        && !drawNaturalFractionResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)) {
                    drawFittedResultText(canvas, lines[0], lcd, contentTop,
                            contentBottom, available);
                }
            } else {
                paint.setTextSize(sp(16f));
                int visibleLines = Math.min(2, lines.length);
                for (int index = 0; index < visibleLines; index++) {
                    canvas.drawText(ellipsize(lines[index], available), lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * (0.66f + index * 0.24f), paint);
                }
            }
        } else {
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(14f));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(ellipsize(state.status(), available * 0.8f), lcd.right - dp(6),
                    contentBottom - dp(1), paint);
        }
    }

    /** Draws a Stage 5 core-owned structured input editor. */
    private void drawWorkflowInput(Canvas canvas, RectF lcd,
                                   CnCwWorkflowSession.Snapshot input) {
        RectF grid = workflowGridBounds(lcd);
        int visibleRows = Math.min(5, input.rows());
        int startRow = Math.max(0, Math.min(input.selectedRow() - visibleRows / 2,
                input.rows() - visibleRows));
        float rowHeader = dp(24f);
        float headerHeight = dp(18f);
        float cellWidth = (grid.width() - rowHeader) / input.columns();
        float cellHeight = (grid.height() - headerHeight) / visibleRows;

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(11f));
        canvas.drawText(input.spec().title(), grid.left, grid.top - dp(6f), paint);

        for (int column = 0; column < input.columns(); column++) {
            float left = grid.left + rowHeader + column * cellWidth;
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(9.5f));
            canvas.drawText(workflowColumnLabel(input, column), left + cellWidth * 0.5f,
                    centeredBaseline(grid.top, grid.top + headerHeight), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.65f));
        paint.setColor(Color.argb(115, 24, 58, 45));
        for (int vr = 0; vr < visibleRows; vr++) {
            int row = startRow + vr;
            float top = grid.top + headerHeight + vr * cellHeight;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(8.5f));
            canvas.drawText(workflowRowLabel(input, row), grid.left + rowHeader * 0.45f,
                    centeredBaseline(top, top + cellHeight), paint);
            for (int column = 0; column < input.columns(); column++) {
                float left = grid.left + rowHeader + column * cellWidth;
                boolean selected = row == input.selectedRow() && column == input.selectedColumn();
                scratch.set(left, top, left + cellWidth, top + cellHeight);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(selected ? Color.argb(56, 24, 58, 45)
                        : Color.argb(12, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(selected ? 1.1f : 0.55f));
                paint.setColor(Color.argb(selected ? 190 : 85, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(LCD_INK);
                paint.setTypeface(selected ? FACE_MEDIUM : FACE_NORMAL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(sp(10.5f));
                String value = input.isChoiceCell(row, column)
                        ? input.displayCell(row, column)
                        : selected ? cleanClipboardText(state.displayText())
                        : input.cell(row, column);
                canvas.drawText(ellipsize(value, cellWidth - dp(5f)),
                        scratch.centerX(), centeredBaseline(top, top + cellHeight), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        drawWorkflowActionBar(canvas, lcd, input);
    }

    private void drawWorkflowActionBar(Canvas canvas, RectF lcd,
                                       CnCwWorkflowSession.Snapshot input) {
        List<CnCwWorkflowAction> actions = input.actions();
        for (int index = 0; index < actions.size(); index++) {
            CnCwWorkflowAction action = actions.get(index);
            RectF bounds = workflowActionBounds(lcd, actions.size(), index);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(action.enabled() ? Color.argb(42, 24, 58, 45)
                    : Color.argb(14, 24, 58, 45));
            canvas.drawRoundRect(bounds, dp(2f), dp(2f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(0.65f));
            paint.setColor(action.enabled() ? Color.argb(145, 24, 58, 45)
                    : Color.argb(50, 24, 58, 45));
            canvas.drawRoundRect(bounds, dp(2f), dp(2f), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(action.enabled() ? LCD_INK : Color.argb(105, 24, 58, 45));
            paint.setTypeface(action.type() == CnCwWorkflowAction.Type.EXECUTE
                    ? FACE_BOLD : FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(actions.size() >= 5 ? 7.6f : 8.2f));
            canvas.drawText(ellipsize(action.label(), bounds.width() - dp(4f)),
                    bounds.centerX(), centeredBaseline(bounds.top, bounds.bottom), paint);
        }
    }

    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(50f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }

    private RectF workflowActionBarBounds(RectF lcd) {
        return new RectF(lcd.left + dp(6f), lcd.bottom - dp(45f),
                lcd.right - dp(6f), lcd.bottom - dp(3f));
    }

    private RectF workflowActionBounds(RectF lcd, int count, int index) {
        RectF bar = workflowActionBarBounds(lcd);
        int columns = Math.max(1, count);
        float gap = dp(1.5f);
        float width = (bar.width() - gap * (columns - 1)) / columns;
        float left = bar.left + index * (width + gap);
        return new RectF(left, bar.top, left + width, bar.bottom);
    }

    private String workflowColumnLabel(CnCwWorkflowSession.Snapshot input, int column) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        if ((layout == CnCwWorkflowSpec.InputLayout.SERIES
                || layout == CnCwWorkflowSpec.InputLayout.PAIRED_SERIES
                || layout == CnCwWorkflowSpec.InputLayout.FIXED_FIELDS)
                && column < input.spec().fields().size()) {
            return input.spec().fields().get(column).label();
        }
        if (layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            return column == 0 ? "x" : column == 1 ? "y" : "z";
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && "simultaneous".equals(input.spec().commandId())) {
            return column == input.columns() - 1 ? "b" : "x" + (column + 1);
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS) return "系数";
        return Integer.toString(column + 1);
    }

    private String workflowRowLabel(CnCwWorkflowSession.Snapshot input, int row) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        if (layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) return "v" + (row + 1);
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && "polynomial".equals(input.spec().commandId())) {
            return "a" + (input.rows() - 1 - row);
        }
        return Integer.toString(row + 1);
    }

    private boolean workflowShapeAdjustable(CnCwWorkflowSession.Snapshot input) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        return layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET
                || layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS;
    }

    private boolean selectWorkflowCellAt(float x, float y) {
        if (!state.hasWorkflowInput() || state.resultShown()) return false;
        CnCwWorkflowSession.Snapshot input = state.workflowInput();
        RectF lcd = displayBounds(getWidth());
        RectF grid = workflowGridBounds(lcd);
        int visibleRows = Math.min(5, input.rows());
        int startRow = Math.max(0, Math.min(input.selectedRow() - visibleRows / 2,
                input.rows() - visibleRows));
        float rowHeader = dp(24f);
        float headerHeight = dp(18f);
        if (x < grid.left + rowHeader || x > grid.right
                || y < grid.top + headerHeight || y > grid.bottom) return false;
        float cellWidth = (grid.width() - rowHeader) / input.columns();
        float cellHeight = (grid.height() - headerHeight) / visibleRows;
        int column = Math.min(input.columns() - 1,
                Math.max(0, (int) ((x - grid.left - rowHeader) / cellWidth)));
        int visibleRow = Math.min(visibleRows - 1,
                Math.max(0, (int) ((y - grid.top - headerHeight) / cellHeight)));
        int row = startRow + visibleRow;
        invalidateEvaluation();
        state = machine.selectWorkflowCell(row, column);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
        return true;
    }

    private boolean selectWorkflowActionAt(float x, float y) {
        if (!state.hasWorkflowInput() || state.resultShown()) return false;
        CnCwWorkflowSession.Snapshot input = state.workflowInput();
        List<CnCwWorkflowAction> actions = input.actions();
        RectF lcd = displayBounds(getWidth());
        for (int index = 0; index < actions.size(); index++) {
            RectF bounds = workflowActionBounds(lcd, actions.size(), index);
            if (!bounds.contains(x, y)) continue;
            CnCwWorkflowAction action = actions.get(index);
            if (!action.enabled()) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return true;
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (action.type() == CnCwWorkflowAction.Type.EXECUTE) {
                dispatchKey(CnCwKey.EXE);
            } else if (action.type() == CnCwWorkflowAction.Type.BACK) {
                dispatchKey(CnCwKey.BACK);
            } else {
                invalidateEvaluation();
                state = machine.performWorkflowAction(action.type());
                postInvalidateOnAnimation();
            }
            return true;
        }
        return false;
    }

    /** Renders core-owned application results without parsing display strings. */
    private void drawStructuredApplicationResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                                 RectF lcd, float contentTop,
                                                 float contentBottom, float available) {
        if (result == null || result.layout() == CnCwModeEngine.ResultLayout.TEXT) return;
        switch (result.layout()) {
            case KEY_VALUE -> drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            case MATRIX, VECTOR -> drawGridResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TABLE -> drawTableResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TEXT -> { }
        }
    }

    private void drawKeyValueResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                    RectF lcd, float contentTop, float contentBottom,
                                    float available) {
        float resultTop = contentTop + dp(12f);
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.48f),
                lcd.left + dp(6), resultTop, paint);

        List<CnCwModeEngine.ResultItem> items = result.items();
        int count = Math.min(4, items.size());
        if (count == 0) return;
        int columns = count == 1 ? 1 : 2;
        int rows = (count + columns - 1) / columns;
        float top = resultTop + dp(4f);
        float bottom = contentBottom - dp(1f);
        float cellWidth = available / columns;
        float rowHeight = Math.max(dp(14f), (bottom - top) / Math.max(1, rows));
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            float left = lcd.left + dp(6) + column * cellWidth;
            float centerY = top + row * rowHeight + rowHeight * 0.52f;
            CnCwModeEngine.ResultItem item = items.get(index);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(9.5f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(item.label() + " =", left, centerY - dp(3f), paint);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(13.5f));
            canvas.drawText(ellipsize(item.value(), cellWidth - dp(8f)),
                    left, centerY + dp(8f), paint);
        }
    }

    private int tableVisibleRows(CnCwModeEngine.ModeResult result) {
        return Math.max(1, Math.min(4, result == null ? 1 : result.rows()));
    }

    private void drawTableResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                 RectF lcd, float contentTop, float contentBottom,
                                 float available) {
        int rows = result.rows();
        int columns = result.columns();
        if (rows <= 0 || columns <= 0 || result.cells().size() != rows * columns) {
            drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            return;
        }
        if (tableResult != result) {
            tableResult = result;
            tableFirstRow = 0;
        }
        int visibleRows = tableVisibleRows(result);
        int maxStart = Math.max(0, rows - visibleRows);
        tableFirstRow = Math.max(0, Math.min(tableFirstRow, maxStart));

        float resultTop = contentTop + (contentBottom - contentTop) * 0.43f;
        float titleBottom = resultTop + dp(14f);
        float headerHeight = dp(14f);
        float gridTop = titleBottom;
        float gridBottom = contentBottom - dp(11f);
        float gridLeft = lcd.left + dp(7f);
        float gridRight = lcd.right - dp(rows > visibleRows ? 7f : 5f);
        float cellWidth = (gridRight - gridLeft) / columns;
        float rowHeight = Math.max(dp(10f),
                (gridBottom - gridTop - headerHeight) / visibleRows);

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.58f),
                gridLeft, resultTop + dp(10f), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(8.5f));
        String range = (tableFirstRow + 1) + "–"
                + Math.min(rows, tableFirstRow + visibleRows) + "/" + rows;
        canvas.drawText(range, gridRight, resultTop + dp(10f), paint);

        for (int column = 0; column < columns; column++) {
            float left = gridLeft + column * cellWidth;
            paint.setColor(Color.argb(38, 24, 58, 45));
            canvas.drawRect(left, gridTop, left + cellWidth, gridTop + headerHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(9f));
            String heading = column < result.items().size()
                    ? result.items().get(column).label() : Integer.toString(column + 1);
            canvas.drawText(ellipsize(heading, cellWidth - dp(3f)),
                    left + cellWidth * 0.5f,
                    centeredBaseline(gridTop, gridTop + headerHeight), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.55f));
        paint.setColor(Color.argb(95, 24, 58, 45));
        for (int vr = 0; vr < visibleRows; vr++) {
            int row = tableFirstRow + vr;
            if (row >= rows) break;
            float top = gridTop + headerHeight + vr * rowHeight;
            for (int column = 0; column < columns; column++) {
                float left = gridLeft + column * cellWidth;
                scratch.set(left, top, left + cellWidth, top + rowHeight);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(vr % 2 == 0
                        ? Color.argb(12, 24, 58, 45) : Color.TRANSPARENT);
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.argb(72, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(LCD_INK);
                paint.setTypeface(FACE_MEDIUM);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(sp(columns >= 3 ? 9.2f : 10f));
                String value = result.cells().get(row * columns + column);
                canvas.drawText(ellipsize(value, cellWidth - dp(4f)),
                        scratch.centerX(), centeredBaseline(top, top + rowHeight), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        if (rows > visibleRows) {
            drawScrollBar(canvas, lcd, tableFirstRow, visibleRows, rows,
                    gridTop + headerHeight, gridTop + headerHeight + visibleRows * rowHeight);
        }
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(7.8f));
        canvas.drawText("↑↓逐行  Page↑↓翻页", gridLeft, contentBottom - dp(1f), paint);
    }

    private void drawGridResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                RectF lcd, float contentTop, float contentBottom,
                                float available) {
        int rows = result.rows();
        int columns = result.columns();
        if (rows <= 0 || columns <= 0 || result.cells().size() != rows * columns) {
            drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            return;
        }
        float resultTop = contentTop + dp(12f);
        float gridBottom = result.items().isEmpty()
                ? contentBottom - dp(2f) : contentBottom - dp(20f);
        float gridLeft = lcd.left + dp(14f);
        float gridRight = lcd.right - dp(14f);
        float gridTop = resultTop + dp(5f);
        float cellWidth = (gridRight - gridLeft) / columns;
        float cellHeight = Math.max(dp(10f), (gridBottom - gridTop) / rows);

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.45f),
                lcd.left + dp(6), resultTop, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.8f));
        float bracket = dp(4f);
        canvas.drawLine(gridLeft - bracket, gridTop, gridLeft, gridTop, paint);
        canvas.drawLine(gridLeft - bracket, gridTop, gridLeft - bracket, gridBottom, paint);
        canvas.drawLine(gridLeft - bracket, gridBottom, gridLeft, gridBottom, paint);
        canvas.drawLine(gridRight, gridTop, gridRight + bracket, gridTop, paint);
        canvas.drawLine(gridRight + bracket, gridTop, gridRight + bracket, gridBottom, paint);
        canvas.drawLine(gridRight, gridBottom, gridRight + bracket, gridBottom, paint);
        paint.setStyle(Paint.Style.FILL);

        float textSize = columns >= 4 || rows >= 4 ? 9.5f : 11.5f;
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(sp(textSize));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                String value = result.cells().get(row * columns + column);
                float cx = gridLeft + (column + 0.5f) * cellWidth;
                float cy = gridTop + (row + 0.5f) * cellHeight;
                canvas.drawText(ellipsize(value, cellWidth - dp(2f)), cx,
                        centeredBaseline(cy - cellHeight * 0.42f, cy + cellHeight * 0.42f), paint);
            }
        }

        if (!result.items().isEmpty()) {
            CnCwModeEngine.ResultItem first = result.items().get(0);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(11f));
            String summary = first.label() + "=" + first.value();
            if (result.items().size() > 1) {
                CnCwModeEngine.ResultItem second = result.items().get(1);
                summary += "   " + second.label() + "=" + second.value();
            }
            canvas.drawText(ellipsize(summary, available), lcd.right - dp(6),
                    contentBottom - dp(1), paint);
        }
    }

    /** Draws the core-owned expression tree without falling back to caret notation. */
    private void drawNaturalExpression(Canvas canvas, CnCwExpressionNode expression,
                                       RectF lcd, float contentTop, float contentBottom,
                                       float available) {
        float baseSize = sp(25f);
        NaturalMetrics metrics = measureNatural(expression, baseSize);
        float viewportLeft = lcd.left + dp(6);
        float cursorOffset = naturalCursorOffset(expression, baseSize);
        float expressionX = viewportLeft;
        if (metrics.width > available) {
            float focus = cursorOffset < 0 ? metrics.width : cursorOffset;
            expressionX = viewportLeft + available * 0.58f - focus;
            expressionX = Math.min(viewportLeft,
                    Math.max(viewportLeft + available - metrics.width, expressionX));
        }
        float baseline = contentTop + metrics.top + dp(2);
        float maximumBaseline = contentTop + (contentBottom - contentTop) * 0.54f;
        drawNaturalNode(canvas, expression, expressionX,
                Math.min(baseline, maximumBaseline), baseSize);
    }

    private NaturalMetrics measureNatural(CnCwExpressionNode node, float textSize) {
        return switch (node.kind()) {
            case TEXT -> naturalTextMetrics(node.text(), textSize);
            case CURSOR -> new NaturalMetrics(Math.max(dp(1.35f), textSize * 0.055f),
                    textSize * 0.78f, textSize * 0.22f);
            case SUPERSCRIPT -> {
                if (node.children().size() < 2) yield measureNaturalRow(node.children(), textSize);
                NaturalMetrics base = measureNatural(node.children().get(0), textSize);
                NaturalMetrics exponent = measureNatural(node.children().get(1), textSize * 0.62f);
                float shift = base.top * 0.54f;
                yield new NaturalMetrics(base.width + exponent.width,
                        Math.max(base.top, shift + exponent.top),
                        Math.max(base.bottom, exponent.bottom - shift));
            }
            case FRACTION -> measureNaturalFraction(node, textSize);
            case ROW, MIXED_FRACTION, RADICAL, NTH_ROOT ->
                    measureNaturalRow(node.children(), textSize);
        };
    }

    private NaturalMetrics measureNaturalFraction(CnCwExpressionNode node, float textSize) {
        if (node.children().size() < 2) return measureNaturalRow(node.children(), textSize);
        float termSize = textSize * 0.70f;
        NaturalMetrics numerator = measureNatural(node.children().get(0), termSize);
        NaturalMetrics denominator = measureNatural(node.children().get(1), termSize);
        float lineOffset = textSize * 0.22f;
        float top = lineOffset + dp(2) + numerator.bottom + numerator.top;
        float bottom = -lineOffset + denominator.top + dp(3) + denominator.bottom;
        return new NaturalMetrics(Math.max(numerator.width, denominator.width) + dp(8),
                top, Math.max(textSize * 0.12f, bottom));
    }

    private NaturalMetrics measureNaturalRow(List<CnCwExpressionNode> children, float textSize) {
        float width = 0f;
        float top = 0f;
        float bottom = 0f;
        for (CnCwExpressionNode child : children) {
            NaturalMetrics childMetrics = measureNatural(child, textSize);
            width += childMetrics.width;
            top = Math.max(top, childMetrics.top);
            bottom = Math.max(bottom, childMetrics.bottom);
        }
        return new NaturalMetrics(width, top, bottom);
    }

    private NaturalMetrics naturalTextMetrics(String text, float textSize) {
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(textSize);
        paint.getFontMetrics(fontMetrics);
        return new NaturalMetrics(paint.measureText(text), -fontMetrics.ascent, fontMetrics.descent);
    }

    private void drawNaturalNode(Canvas canvas, CnCwExpressionNode node,
                                 float left, float baseline, float textSize) {
        switch (node.kind()) {
            case TEXT -> {
                paint.setTypeface(FACE_NORMAL);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTextSize(textSize);
                if (node.selected()) {
                    paint.getFontMetrics(fontMetrics);
                    float width = paint.measureText(node.text());
                    paint.setColor(LCD_DARK);
                    canvas.drawRect(left - dp(1), baseline + fontMetrics.ascent - dp(2),
                            left + width + dp(1), baseline + fontMetrics.descent + dp(2), paint);
                    paint.setColor(LCD);
                } else {
                    paint.setColor(LCD_INK);
                }
                canvas.drawText(node.text(), left, baseline, paint);
            }
            case CURSOR -> {
                NaturalMetrics metrics = measureNatural(node, textSize);
                paint.setColor(LCD_INK);
                paint.setStrokeWidth(Math.max(dp(1.0f), textSize * 0.045f));
                float cursorX = left + metrics.width * 0.5f;
                canvas.drawLine(cursorX, baseline - metrics.top, cursorX,
                        baseline + metrics.bottom, paint);
            }
            case SUPERSCRIPT -> {
                if (node.children().size() < 2) {
                    drawNaturalRow(canvas, node.children(), left, baseline, textSize);
                    return;
                }
                CnCwExpressionNode baseNode = node.children().get(0);
                CnCwExpressionNode exponentNode = node.children().get(1);
                NaturalMetrics base = measureNatural(baseNode, textSize);
                drawNaturalNode(canvas, baseNode, left, baseline, textSize);
                drawNaturalNode(canvas, exponentNode, left + base.width,
                        baseline - base.top * 0.54f, textSize * 0.62f);
            }
            case FRACTION -> drawNaturalFraction(canvas, node, left, baseline, textSize);
            case ROW, MIXED_FRACTION, RADICAL, NTH_ROOT ->
                    drawNaturalRow(canvas, node.children(), left, baseline, textSize);
        }
    }

    private void drawNaturalFraction(Canvas canvas, CnCwExpressionNode node,
                                     float left, float baseline, float textSize) {
        if (node.children().size() < 2) {
            drawNaturalRow(canvas, node.children(), left, baseline, textSize);
            return;
        }
        float termSize = textSize * 0.70f;
        CnCwExpressionNode numeratorNode = node.children().get(0);
        CnCwExpressionNode denominatorNode = node.children().get(1);
        NaturalMetrics numerator = measureNatural(numeratorNode, termSize);
        NaturalMetrics denominator = measureNatural(denominatorNode, termSize);
        NaturalMetrics fraction = measureNaturalFraction(node, textSize);
        float lineY = baseline - textSize * 0.22f;
        float numeratorLeft = left + (fraction.width - numerator.width) * 0.5f;
        float denominatorLeft = left + (fraction.width - denominator.width) * 0.5f;
        drawNaturalNode(canvas, numeratorNode, numeratorLeft,
                lineY - dp(2) - numerator.bottom, termSize);
        paint.setColor(LCD_INK);
        paint.setStrokeWidth(Math.max(dp(0.8f), textSize * 0.035f));
        canvas.drawLine(left + dp(1.5f), lineY, left + fraction.width - dp(1.5f), lineY, paint);
        drawNaturalNode(canvas, denominatorNode, denominatorLeft,
                lineY + denominator.top + dp(3), termSize);
    }

    private void drawNaturalRow(Canvas canvas, List<CnCwExpressionNode> children,
                                float left, float baseline, float textSize) {
        float x = left;
        for (CnCwExpressionNode child : children) {
            drawNaturalNode(canvas, child, x, baseline, textSize);
            x += measureNatural(child, textSize).width;
        }
    }

    private float naturalCursorOffset(CnCwExpressionNode node, float textSize) {
        if (!node.containsCursor()) return -1f;
        if (node.kind() == CnCwExpressionNode.Kind.CURSOR) return 0f;
        if (node.kind() == CnCwExpressionNode.Kind.SUPERSCRIPT
                && node.children().size() >= 2) {
            CnCwExpressionNode base = node.children().get(0);
            if (base.containsCursor()) return naturalCursorOffset(base, textSize);
            return measureNatural(base, textSize).width
                    + naturalCursorOffset(node.children().get(1), textSize * 0.62f);
        }
        if (node.kind() == CnCwExpressionNode.Kind.FRACTION
                && node.children().size() >= 2) {
            float termSize = textSize * 0.70f;
            CnCwExpressionNode numerator = node.children().get(0);
            CnCwExpressionNode denominator = node.children().get(1);
            NaturalMetrics fraction = measureNaturalFraction(node, textSize);
            if (numerator.containsCursor()) {
                return (fraction.width - measureNatural(numerator, termSize).width) * 0.5f
                        + naturalCursorOffset(numerator, termSize);
            }
            return (fraction.width - measureNatural(denominator, termSize).width) * 0.5f
                    + naturalCursorOffset(denominator, termSize);
        }
        float offset = 0f;
        for (CnCwExpressionNode child : node.children()) {
            if (child.containsCursor()) return offset + naturalCursorOffset(child, textSize);
            offset += measureNatural(child, textSize).width;
        }
        return -1f;
    }

    /** Draws an Ans-expanded finished calculation without changing editor tokens. */
    private void drawInspectionProcess(Canvas canvas, String value, RectF lcd,
                                       float contentTop, float contentBottom, float available) {
        String display = value == null ? "" : value;
        float left = lcd.left + dp(6);
        float baseline = contentTop + Math.min(dp(31), (contentBottom - contentTop) * 0.32f);
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        for (float size = 25f; size >= 12f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, left, baseline, paint);
                return;
            }
        }
        paint.setTextSize(sp(12f));
        float width = Math.max(1f, paint.measureText(display));
        float scale = Math.min(1f, available / width);
        canvas.save();
        canvas.scale(scale, 1f, left, baseline);
        canvas.drawText(display, left, baseline, paint);
        canvas.restore();
    }

    /** Draws phone-style handles at the two semantic selection boundaries. */
    private void drawSelectionHandles(Canvas canvas, RectF lcd,
                                      float contentTop, float contentBottom) {
        float startX = displaySemanticBoundaryX(
                semanticSelectionPathForBoundary(state.selectionStart()));
        float endX = displaySemanticBoundaryX(
                semanticSelectionPathForBoundary(state.selectionEnd()));
        float top = contentTop + dp(1.5f);
        float bottom = Math.min(contentBottom - dp(10), contentTop + dp(34));
        paint.setColor(LCD_DARK);
        paint.setStrokeWidth(dp(1.15f));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(startX, top + dp(4), startX, bottom, paint);
        canvas.drawLine(endX, top, endX, bottom - dp(4), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(startX, bottom + dp(2.2f), dp(2.6f), paint);
        canvas.drawCircle(endX, top - dp(2.2f), dp(2.6f), paint);
    }

    /** Draws a result without losing trailing digits to an ellipsis. */
    private void drawFittedResultText(Canvas canvas, String value, RectF lcd,
                                      float contentTop, float contentBottom, float available) {
        float right = lcd.right - dp(6);
        float baseline = contentTop + (contentBottom - contentTop) * 0.86f;
        String display = value == null ? "" : value;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.RIGHT);

        for (float size = 34f; size >= 16f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        String scientific = compactScientificResult(display);
        if (!scientific.equals(display)) {
            // The long-number fallback is created only after the first natural-SCI
            // pass, so route that newly-created E notation back through the natural
            // mantissa × 10 + raised exponent renderer instead of ever drawing E.
            if (drawNaturalScientificResult(canvas, scientific, lcd, contentTop,
                    contentBottom, available)) {
                return;
            }
            // Defensive fallback: even if the structured renderer rejects a future
            // scientific spelling, never expose raw E notation to the calculator LCD.
            display = scientific.replace("E", "×10^").replace("e", "×10^");
            for (float size = 24f; size >= 14f; size -= 1f) {
                paint.setTextSize(sp(size));
                if (paint.measureText(display) <= available) {
                    canvas.drawText(display, right, baseline, paint);
                    return;
                }
            }
        }

        // Last-resort fit still preserves every character instead of adding an ellipsis.
        for (float size = 13f; size >= 8f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        paint.setTextSize(sp(8f));
        float width = Math.max(1f, paint.measureText(display));
        float scale = Math.min(1f, available / width);
        canvas.save();
        canvas.scale(scale, 1f, right, baseline);
        canvas.drawText(display, right, baseline, paint);
        canvas.restore();
    }

    /** Converts only plain numeric results to a compact scientific fallback. */
    private static String compactScientificResult(String value) {
        if (value == null || !value.matches("[−-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+−-]?\\d+)?")) {
            return value == null ? "" : value;
        }
        try {
            double numeric = Double.parseDouble(value.replace('−', '-'));
            if (!Double.isFinite(numeric) || numeric == 0.0d) return value;
            String scientific = String.format(java.util.Locale.US, "%.9E", numeric);
            int marker = scientific.indexOf('E');
            String mantissa = scientific.substring(0, marker);
            String exponent = scientific.substring(marker + 1);
            while (mantissa.contains(".") && mantissa.endsWith("0")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
            if (mantissa.endsWith(".")) mantissa = mantissa.substring(0, mantissa.length() - 1);
            boolean negativeExponent = exponent.startsWith("-");
            exponent = exponent.replace("+", "").replace("-", "");
            while (exponent.length() > 1 && exponent.startsWith("0")) {
                exponent = exponent.substring(1);
            }
            return mantissa + "E" + (negativeExponent ? "-" : "") + exponent;
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    /** Draws SCI/ENG output as a handheld-style mantissa × 10 with a raised exponent. */
    private boolean drawNaturalScientificResult(Canvas canvas, String value, RectF lcd,
                                                float contentTop, float contentBottom,
                                                float available) {
        if (value == null || value.isBlank()) return false;
        int marker = value.indexOf("\u00d710^");
        int markerLength = 4;
        if (marker < 1) {
            int upper = value.lastIndexOf('E');
            int lower = value.lastIndexOf('e');
            int scientific = Math.max(upper, lower);
            if (scientific < 1 || scientific + 1 >= value.length()) return false;
            marker = scientific;
            markerLength = 1;
        }
        String mantissa = value.substring(0, marker).trim();
        String exponent = value.substring(marker + markerLength).trim();
        if (exponent.startsWith("(") && exponent.endsWith(")") && exponent.length() > 2) {
            exponent = exponent.substring(1, exponent.length() - 1);
        }
        // E inside an error message or a complex/expression result is not an exponent.
        if (!SCI_MANTISSA.matcher(mantissa).matches()
                || !SCI_EXPONENT.matcher(exponent).matches()) return false;

        float baseSize = sp(34f);
        float minBaseSize = sp(14f);
        float exponentRatio = 0.62f;
        String base = mantissa + "\u00d710";
        paint.setTypeface(FACE_MEDIUM);

        float exponentSize;
        float baseWidth;
        float exponentWidth;
        while (true) {
            exponentSize = baseSize * exponentRatio;
            paint.setTextSize(baseSize);
            baseWidth = paint.measureText(base);
            paint.setTextSize(exponentSize);
            exponentWidth = paint.measureText(exponent);
            if (baseWidth + exponentWidth <= available || baseSize <= minBaseSize) break;
            baseSize -= sp(1f);
        }

        exponentSize = baseSize * exponentRatio;
        paint.setTextSize(baseSize);
        baseWidth = paint.measureText(base);
        paint.setTextSize(exponentSize);
        exponentWidth = paint.measureText(exponent);
        float totalWidth = baseWidth + exponentWidth;
        float horizontalScale = totalWidth > available ? available / totalWidth : 1f;

        float baseline = contentTop + (contentBottom - contentTop) * 0.86f;
        float right = lcd.right - dp(6);
        canvas.save();
        canvas.translate(right, 0f);
        canvas.scale(horizontalScale, 1f);
        float left = -totalWidth;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(baseSize);
        canvas.drawText(base, left, baseline, paint);
        paint.setTextSize(exponentSize);
        canvas.drawText(exponent, left + baseWidth, baseline - baseSize * 0.54f, paint);
        canvas.restore();
        return true;
    }

    /** Draws handheld-style stacked fractions for exact scalar results. */
    private boolean drawNaturalFractionResult(Canvas canvas, String value, RectF lcd,
                                              float contentTop, float contentBottom,
                                              float available) {
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash + 1 >= value.length()) {
            return false;
        }
        String left = value.substring(0, slash).trim();
        String denominator = value.substring(slash + 1).trim();
        if (!isNaturalTerm(denominator)) return false;

        String whole = "";
        String numerator = left;
        int space = left.lastIndexOf(' ');
        if (space > 0) {
            String candidateWhole = left.substring(0, space).trim();
            String candidateNumerator = left.substring(space + 1).trim();
            if (candidateWhole.matches("[−-]?\\d+") && isNaturalTerm(candidateNumerator)) {
                whole = candidateWhole;
                numerator = candidateNumerator;
            }
        }
        if (!isNaturalTerm(numerator)) return false;

        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(24f));
        float numeratorWidth = paint.measureText(numerator);
        float denominatorWidth = paint.measureText(denominator);
        float fractionWidth = Math.max(numeratorWidth, denominatorWidth) + dp(7);
        float wholeWidth = 0f;
        if (!whole.isEmpty()) {
            paint.setTextSize(sp(32f));
            wholeWidth = paint.measureText(whole) + dp(5);
        }
        if (fractionWidth + wholeWidth > available) return false;

        float right = lcd.right - dp(7);
        float fractionCenter = right - fractionWidth * 0.5f;
        float centerY = contentTop + (contentBottom - contentTop) * 0.75f;
        paint.setStrokeWidth(dp(0.8f));
        paint.setColor(LCD_INK);
        canvas.drawLine(fractionCenter - fractionWidth * 0.5f, centerY,
                fractionCenter + fractionWidth * 0.5f, centerY, paint);
        paint.setTextSize(sp(24f));
        canvas.drawText(numerator, fractionCenter, centerY - dp(6f), paint);
        canvas.drawText(denominator, fractionCenter, centerY + dp(19f), paint);
        if (!whole.isEmpty()) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(sp(32f));
            canvas.drawText(whole, fractionCenter - fractionWidth * 0.5f - dp(3),
                    centerY + dp(4.8f), paint);
        }
        return true;
    }

    private static boolean isNaturalTerm(String value) {
        return !value.isEmpty() && value.matches("[−-]?[0-9πe√^]+(?:√[0-9]+)?");
    }

    private void drawSpreadsheetGrid(Canvas canvas, RectF lcd) {
        float formulaTop = lcd.top + lcd.height() * 0.145f;
        float formulaBottom = formulaTop + dp(13);
        paint.setColor(Color.argb(42, 20, 27, 23));
        canvas.drawRect(lcd.left, formulaTop, lcd.right, formulaBottom, paint);
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(14f));
        String address = ((char) ('A' + state.spreadsheetColumn()))
                + Integer.toString(state.spreadsheetRow() + 1);
        canvas.drawText(address, lcd.left + dp(3),
                centeredBaseline(formulaTop, formulaBottom), paint);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(13f));
        String formula = state.displayText().equals("│")
                ? state.spreadsheetFormula() : state.displayText();
        if (formula.isEmpty()) formula = state.result();
        canvas.drawText(ellipsize(formula, lcd.width() - dp(28)),
                lcd.left + dp(22), centeredBaseline(formulaTop, formulaBottom), paint);

        float gridTop = formulaBottom + dp(2);
        float gridBottom = lcd.bottom - dp(2);
        float rowHeader = dp(14);
        int visibleRows = 4;
        float rowHeight = (gridBottom - gridTop) / (visibleRows + 1);
        float columnWidth = (lcd.width() - rowHeader) / 5f;
        int rowStart = (state.spreadsheetRow() / visibleRows) * visibleRows;
        List<String> values = state.spreadsheetCells();
        paint.setStrokeWidth(dp(0.6f));
        paint.setTextSize(sp(12f));
        for (int column = 0; column < 5; column++) {
            float left = lcd.left + rowHeader + column * columnWidth;
            paint.setColor(Color.argb(42, 20, 27, 23));
            canvas.drawRect(left, gridTop, left + columnWidth, gridTop + rowHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Character.toString((char) ('A' + column)), left + columnWidth * 0.5f,
                    centeredBaseline(gridTop, gridTop + rowHeight), paint);
        }
        for (int visibleRow = 0; visibleRow < visibleRows; visibleRow++) {
            int row = rowStart + visibleRow;
            if (row >= 45) break;
            float top = gridTop + (visibleRow + 1) * rowHeight;
            paint.setColor(Color.argb(42, 20, 27, 23));
            canvas.drawRect(lcd.left, top, lcd.left + rowHeader, top + rowHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Integer.toString(row + 1), lcd.left + rowHeader * 0.5f,
                    centeredBaseline(top, top + rowHeight), paint);
            for (int column = 0; column < 5; column++) {
                float left = lcd.left + rowHeader + column * columnWidth;
                boolean selected = row == state.spreadsheetRow()
                        && column == state.spreadsheetColumn();
                paint.setColor(selected ? LCD_DARK : Color.argb(24, 20, 27, 23));
                canvas.drawRect(left, top, left + columnWidth, top + rowHeight, paint);
                paint.setColor(selected ? LCD : LCD_INK);
                paint.setTypeface(FACE_NORMAL);
                paint.setTextAlign(Paint.Align.RIGHT);
                String value = values.size() > row * 5 + column
                        ? values.get(row * 5 + column) : "";
                canvas.drawText(ellipsize(value, columnWidth - dp(3)),
                        left + columnWidth - dp(2), centeredBaseline(top, top + rowHeight), paint);
            }
        }
    }

    private void drawModeLanding(Canvas canvas, RectF lcd) {
        List<CnCwCommand> commands = state.modeCommands();
        float top = lcd.top + lcd.height() * 0.145f;
        int visible = Math.min(3, commands.size());
        int start = Math.max(0, Math.min(state.selectedIndex() - visible + 1,
                Math.max(0, commands.size() - visible)));
        float rowHeight = (lcd.bottom - top - dp(2)) / Math.max(1, visible);
        for (int row = 0; row < visible; row++) {
            int index = start + row;
            float rowTop = top + row * rowHeight;
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRoundRect(lcd.left + dp(3), rowTop,
                        lcd.right - dp(3), rowTop + rowHeight - dp(1), dp(1.5f), dp(1.5f), paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(sp(18f));
            canvas.drawText(commands.get(index).label(), lcd.left + dp(7),
                    centeredBaseline(rowTop, rowTop + rowHeight), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(12f));
            canvas.drawText(ellipsize(commands.get(index).description(), lcd.width() * 0.48f),
                    lcd.right - dp(7), centeredBaseline(rowTop, rowTop + rowHeight), paint);
        }
    }

    private void drawScrollBar(Canvas canvas, RectF lcd, int start, int visible,
                               int total, float top, float bottom) {
        float trackLeft = lcd.right - dp(2.3f);
        paint.setColor(Color.argb(70, 20, 27, 23));
        canvas.drawRect(trackLeft, top, lcd.right - dp(1.1f), bottom, paint);
        float thumbHeight = (bottom - top) * visible / total;
        float thumbTop = top + (bottom - top - thumbHeight) * start / Math.max(1, total - visible);
        paint.setColor(LCD_INK);
        canvas.drawRect(trackLeft, thumbTop, lcd.right - dp(1.1f), thumbTop + thumbHeight, paint);
    }

    private void drawPageRocker(Canvas canvas) {
        KeyHit up = findSpec(CnCwKey.PAGE_UP);
        KeyHit down = findSpec(CnCwKey.PAGE_DOWN);
        if (up == null || down == null) return;
        scratch.set(Math.min(up.visualBounds.left, down.visualBounds.left) - dp(2),
                up.visualBounds.top - dp(4),
                Math.max(up.visualBounds.right, down.visualBounds.right) + dp(2),
                down.visualBounds.bottom + dp(4));
        paint.setColor(Color.rgb(213, 221, 214));
        canvas.drawRoundRect(scratch, scratch.width() * 0.5f, scratch.width() * 0.5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.rgb(111, 126, 117));
        canvas.drawRoundRect(scratch, scratch.width() * 0.5f, scratch.width() * 0.5f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNavigationPlate(Canvas canvas) {
        KeyHit up = findNavSpec(CnCwKey.UP);
        KeyHit down = findNavSpec(CnCwKey.DOWN);
        KeyHit left = findNavSpec(CnCwKey.LEFT);
        KeyHit right = findNavSpec(CnCwKey.RIGHT);
        if (up == null || down == null || left == null || right == null) return;
        scratch.set(left.visualBounds.left - dp(5), up.visualBounds.top - dp(5),
                right.visualBounds.right + dp(5), down.visualBounds.bottom + dp(5));
        paint.setColor(Color.rgb(218, 224, 217));
        canvas.drawRoundRect(scratch, scratch.height() * 0.33f,
                scratch.height() * 0.33f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.85f));
        paint.setColor(Color.rgb(183, 193, 185));
        canvas.drawRoundRect(scratch, scratch.height() * 0.33f,
                scratch.height() * 0.33f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDirectionGlyph(Canvas canvas, CnCwKey key, RectF visual) {
        float centerX = visual.centerX();
        float centerY = visual.centerY();
        float size = Math.min(visual.width(), visual.height()) * 0.22f;
        iconPath.reset();
        switch (key) {
            case UP -> {
                iconPath.moveTo(centerX, centerY - size);
                iconPath.lineTo(centerX - size * 0.82f, centerY + size * 0.70f);
                iconPath.lineTo(centerX + size * 0.82f, centerY + size * 0.70f);
            }
            case DOWN -> {
                iconPath.moveTo(centerX, centerY + size);
                iconPath.lineTo(centerX - size * 0.82f, centerY - size * 0.70f);
                iconPath.lineTo(centerX + size * 0.82f, centerY - size * 0.70f);
            }
            case LEFT -> {
                iconPath.moveTo(centerX - size, centerY);
                iconPath.lineTo(centerX + size * 0.70f, centerY - size * 0.82f);
                iconPath.lineTo(centerX + size * 0.70f, centerY + size * 0.82f);
            }
            case RIGHT -> {
                iconPath.moveTo(centerX + size, centerY);
                iconPath.lineTo(centerX - size * 0.70f, centerY - size * 0.82f);
                iconPath.lineTo(centerX - size * 0.70f, centerY + size * 0.82f);
            }
            default -> { return; }
        }
        iconPath.close();
        paint.setColor(Color.rgb(35, 63, 50));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(iconPath, paint);
    }

    private void drawPageRockerGlyph(Canvas canvas, CnCwKey key, RectF visual) {
        float centerX = visual.centerX();
        float centerY = visual.centerY();
        float size = Math.min(visual.width(), visual.height()) * 0.20f;
        iconPath.reset();
        if (key == CnCwKey.PAGE_UP) {
            iconPath.moveTo(centerX - size, centerY + size * 0.35f);
            iconPath.lineTo(centerX, centerY - size * 0.65f);
            iconPath.lineTo(centerX + size, centerY + size * 0.35f);
        } else {
            iconPath.moveTo(centerX - size, centerY - size * 0.35f);
            iconPath.lineTo(centerX, centerY + size * 0.65f);
            iconPath.lineTo(centerX + size, centerY - size * 0.35f);
        }
        paint.setColor(Color.rgb(35, 63, 50));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(iconPath, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private KeyHit findNavSpec(CnCwKey key) {
        KeyHit best = null;
        float target = getWidth() * 0.60f;
        float distance = Float.MAX_VALUE;
        for (KeyHit hit : hitMap) {
            if (hit.spec.key != key || hit.spec.kind != KeyKind.NAV) continue;
            float candidate = Math.abs(hit.touchBounds.centerX() - target);
            if (candidate < distance) {
                distance = candidate;
                best = hit;
            }
        }
        return best;
    }

    private void drawKey(Canvas canvas, KeyHit hit) {
        boolean pressed = touchRouter.isPressed(hit.spec.key);
        int base = colorFor(hit.spec.kind);
        int color = pressed ? blend(base, INK_DARK, 0.13f) : base;
        RectF visual = hit.visualBounds;
        float radius = hit.spec.circular ? Math.min(visual.width(), visual.height()) * 0.5f
                : dp(6f);
        boolean pageRocker = hit.spec.key == CnCwKey.PAGE_UP
                || hit.spec.key == CnCwKey.PAGE_DOWN;
        float pressDepth = pressed ? dp(1.7f) : 0f;

        boolean directionKey = hit.spec.key == CnCwKey.UP || hit.spec.key == CnCwKey.DOWN
                || hit.spec.key == CnCwKey.LEFT || hit.spec.key == CnCwKey.RIGHT;
        canvas.save();
        canvas.translate(0, pressDepth);
        if (!pageRocker) {
            float shadowDepth = pressed ? dp(0.8f) : dp(2.45f);
            paint.setColor(KEY_SHADOW);
            canvas.drawRoundRect(visual.left, visual.top + shadowDepth, visual.right,
                    visual.bottom + shadowDepth, radius, radius, paint);
            paint.setColor(color);
            canvas.drawRoundRect(visual, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(0.85f));
            paint.setColor(pressed ? blend(KEY_BORDER, INK_DARK, 0.20f) : KEY_BORDER);
            canvas.drawRoundRect(visual, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        if (directionKey) {
            drawDirectionGlyph(canvas, hit.spec.key, visual);
        } else if (pageRocker) {
            drawPageRockerGlyph(canvas, hit.spec.key, visual);
        } else {
            boolean darkInk = true;
            paint.setColor(darkInk ? INK_DARK : INK_LIGHT);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            if (hit.spec.secondary.isEmpty()) {
                drawFittedCentered(canvas, hit.spec.main, visual.centerX(),
                        visual.top, visual.bottom, hit.visual.mainTextSize(),
                        visual.width() * 0.88f, dp(11), darkInk ? INK_DARK : INK_LIGHT,
                        FACE_NORMAL);
            } else {
                // Secondary legends get a real band inside their own keycap.
                // This keeps them centered and prevents them from colliding with
                // the key above on compact phone layouts.
                drawFittedCentered(canvas, hit.spec.secondary, visual.centerX(),
                        visual.top + dp(1), visual.top + visual.height() * 0.34f,
                        scaledSecondarySize(hit), visual.width() * 0.92f,
                        dp(9), SHIFT_INK, FACE_MEDIUM);
                drawFittedCentered(canvas, hit.spec.main, visual.centerX(),
                        visual.top + visual.height() * 0.27f, visual.bottom,
                        scaledMainSize(hit), visual.width() * 0.90f,
                        dp(11), darkInk ? INK_DARK : INK_LIGHT, FACE_NORMAL);
            }
        }
        canvas.restore();

        if (state.shiftArmed() && hit.spec.key == CnCwKey.SHIFT) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(visual.left + dp(1), visual.top + dp(1),
                    visual.right - dp(1), visual.bottom - dp(1), radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) cancelGestures();
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && displayBounds(getWidth()).contains(event.getX(), event.getY())) {
                    if (state.screen() == CnCwScreen.HOME) {
                        homePointerId = pointerId;
                        pressedHomeIndex = homeItemAt(event.getX(), event.getY());
                        pressedHomeViewport = state.homeViewportStart();
                        homeDownX = event.getX(); homeDownY = event.getY();
                        return true;
                    }
                    if (state.hasWorkflowInput() && !state.resultShown()) {
                        if (!selectWorkflowActionAt(event.getX(), event.getY())) {
                            selectWorkflowCellAt(event.getX(), event.getY());
                        }
                        return true;
                    }
                    displayPressed = true;
                    displayPointerId = pointerId;
                    displaySelectionMode = false;
                    displayLongPressTriggered = false;
                    selectionTapCandidate = false;
                    selectionDragEdge = 0;
                    displayDownX = event.getX();
                    displayDownY = event.getY();
                    lastDragCursor = state.cursor();
                    lastDragSemantic = state.semanticCursor();

                    if (!isStructuredResultScreen() && state.hasSelection()) {
                        RectF lcd = displayBounds(getWidth());
                        float contentTop = lcd.top + lcd.height() * 0.145f;
                        float contentBottom = lcd.bottom - dp(3);
                        CnCwCursorPath startPath = semanticSelectionPathForBoundary(
                                state.selectionStart());
                        CnCwCursorPath endPath = semanticSelectionPathForBoundary(
                                state.selectionEnd());
                        float startX = displaySemanticBoundaryX(startPath);
                        float endX = displaySemanticBoundaryX(endPath);
                        // Keep the handles visually small, but give each boundary a much
                        // larger phone-style grab zone. Users should not need to land on
                        // the tiny knob itself before they can adjust an existing selection.
                        float handleHitTop = contentTop - dp(10);
                        float handleHitBottom = Math.min(contentBottom, contentTop + dp(52));
                        float handleXSlop = dp(26);
                        boolean inHandleBand = event.getY() >= handleHitTop
                                && event.getY() <= handleHitBottom;
                        float startXDistance = Math.abs(event.getX() - startX);
                        float endXDistance = Math.abs(event.getX() - endX);
                        boolean startHit = inHandleBand && startXDistance <= handleXSlop;
                        boolean endHit = inHandleBand && endXDistance <= handleXSlop;
                        if (startHit || endHit) {
                            selectionDragEdge = startHit && endHit
                                    ? (startXDistance <= endXDistance ? -1 : 1)
                                    : startHit ? -1 : 1;
                            displaySelectionMode = true;
                            lastDragCursor = selectionDragEdge < 0
                                    ? state.selectionStart() : state.selectionEnd();
                            lastDragSemantic = selectionDragEdge < 0
                                    ? startPath : endPath;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            return true;
                        }
                        float left = Math.min(startX, endX) - dp(6);
                        float right = Math.max(startX, endX) + dp(6);
                        float textTop = contentTop - dp(4);
                        float textBottom = Math.min(contentBottom, contentTop + dp(38));
                        if (event.getX() >= left && event.getX() <= right
                                && event.getY() >= textTop && event.getY() <= textBottom) {
                            selectionTapCandidate = true;
                            return true;
                        }
                    }

                    displayLongPress = () -> {
                        if (displayPressed) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            displayLongPressTriggered = true;
                            if (isStructuredResultScreen() || (state.resultShown() && state.hasAns()
                                    && isResultBand(displayDownY))) {
                                // A long-press on the lower result line must expose the
                                // clipboard actions directly. Previously it tried to select
                                // the expression above, making “复制 Ans” effectively unreachable.
                                displaySelectionMode = false;
                                selectionDragEdge = 0;
                                postInvalidateOnAnimation();
                                showClipboardMenu();
                                return;
                            }
                            if (machine.cursorLimit() == 0) {
                                // There is nothing to select on an empty editor. Long-press
                                // should still expose the standard phone action the user
                                // needs here: paste from the system clipboard.
                                displaySelectionMode = false;
                                selectionDragEdge = 0;
                                postInvalidateOnAnimation();
                                showPasteOnlyMenu();
                                return;
                            }
                            CnCwCursorPath anchor = displaySemanticPosition(
                                    displayDownX, displayDownY);
                            invalidateEvaluation();
                            state = machine.selectTouchWord(anchor);
                            lastDragCursor = state.cursor();
                            lastDragSemantic = state.semanticCursor();
                            selectionDragEdge = 0;
                            displaySelectionMode = true;
                            postInvalidateOnAnimation();
                        }
                    };
                    gestureHandler.postDelayed(displayLongPress, 360);
                    return true;
                }
                KeyHit hit = findHit(event.getX(actionIndex), event.getY(actionIndex));
                if (hit != null && touchRouter.pointerDown(pointerId, hit.spec.key)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    dispatchKey(hit.spec.key);
                    scheduleKeyRepeat(pointerId, hit.spec.key);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (homePointerId >= 0) {
                    int index = event.findPointerIndex(homePointerId);
                    if (index < 0 || Math.abs(event.getX(index) - homeDownX) >= dp(10)
                            || Math.abs(event.getY(index) - homeDownY) >= dp(10)) pressedHomeIndex = -1;
                    return true;
                }
                if (displayPressed) {
                    int displayIndex = event.findPointerIndex(displayPointerId);
                    if (displayIndex < 0) return true;
                    float displayX = event.getX(displayIndex);
                    float displayY = event.getY(displayIndex);
                    if (displaySelectionMode) {
                        moveSelectionBoundaryToDisplayPosition(displayX, displayY);
                        return true;
                    }
                    float dx = displayX - displayDownX;
                    float dy = displayY - displayDownY;
                    if (selectionTapCandidate) {
                        if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) {
                            selectionTapCandidate = false;
                        }
                        return true;
                    }
                    if (Math.abs(dx) > dp(4) && Math.abs(dx) > Math.abs(dy)) {
                        if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
                        moveCursorToDisplayPosition(displayX, displayY, true);
                    }
                    return true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Every pointer owns its release, including the last key finger
                // after a display finger has already left the screen.
                touchRouter.pointerUp(pointerId);
                if (pointerId == repeatingPointerId) stopKeyRepeat();
                if (pointerId == homePointerId) {
                    int index = pressedHomeIndex;
                    homePointerId = -1; pressedHomeIndex = -1;
                    if (index >= 0 && state.screen() == CnCwScreen.HOME
                            && state.homeViewportStart() == pressedHomeViewport
                            && homeItemAt(event.getX(actionIndex), event.getY(actionIndex)) == index
                            && Math.abs(event.getX(actionIndex) - homeDownX) < dp(10)
                            && Math.abs(event.getY(actionIndex) - homeDownY) < dp(10)) {
                        invalidateEvaluation();
                        state = machine.activateHomeItem(index);
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        performClick(); postInvalidateOnAnimation();
                    }
                    return true;
                }
                if (displayPressed && pointerId == displayPointerId) {
                    displayPressed = false;
                    displayPointerId = -1;
                    if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
                    if (displaySelectionMode || displayLongPressTriggered) {
                        displaySelectionMode = false;
                        displayLongPressTriggered = false;
                        selectionDragEdge = 0;
                        selectionTapCandidate = false;
                        // Phone-style behavior: releasing a handle keeps the selection.
                        postInvalidateOnAnimation();
                        return true;
                    }
                    float dx = event.getX(actionIndex) - displayDownX;
                    float dy = event.getY(actionIndex) - displayDownY;
                    if (selectionTapCandidate) {
                        selectionTapCandidate = false;
                        if (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12)) {
                            showClipboardMenu();
                        }
                        return true;
                    }
                    if (Math.abs(dx) < dp(18) && Math.abs(dy) < dp(18)) {
                        moveCursorToDisplayPosition(event.getX(actionIndex), event.getY(actionIndex), false);
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                postInvalidateOnAnimation();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                cancelGestures();
                postInvalidateOnAnimation();
                return true;
            }
            default -> { return true; }
        }
    }

    /**
     * Basic phone-style repeat for the low-risk editing keys. The first tap
     * has already been committed by pointer-down; repeat only starts after a
     * long-press delay and is cancelled as soon as that pointer is released.
     */
    private void scheduleKeyRepeat(int pointerId, CnCwKey key) {
        if (!isRepeatableKey(key)) return;
        stopKeyRepeat();
        repeatingPointerId = pointerId;
        repeatingKey = key;
        keyRepeat = new Runnable() {
            @Override public void run() {
                if (repeatingKey == null) return;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                dispatchKey(repeatingKey);
                gestureHandler.postDelayed(this, 72);
            }
        };
        gestureHandler.postDelayed(keyRepeat, 420);
    }

    private void stopKeyRepeat() {
        if (keyRepeat != null) gestureHandler.removeCallbacks(keyRepeat);
        keyRepeat = null;
        repeatingKey = null;
        repeatingPointerId = -1;
    }

    /** No delayed input is allowed to survive a lost gesture or inactive window. */
    private void cancelGestures() {
        homePointerId = -1;
        pressedHomeIndex = -1;
        displayPressed = false;
        displayPointerId = -1;
        displaySelectionMode = false;
        displayLongPressTriggered = false;
        selectionTapCandidate = false;
        selectionDragEdge = 0;
        if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
        displayLongPress = null;
        stopKeyRepeat();
        touchRouter.cancelAll();
    }

    private boolean isRepeatableKey(CnCwKey key) {
        return switch (key) {
            case DEL, DOT, DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
                    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9 -> true;
            default -> false;
        };
    }

    private void moveCursorToDisplayPosition(float x, float y, boolean haptic) {
        if (isStructuredResultScreen()) return;
        moveCursorAtomically(displaySemanticPosition(x, y), haptic);
    }

    /**
     * Maps screen geometry to a semantic editor position. Nested spans win when
     * the pointer is vertically close to their visual slot; otherwise the old
     * x-only token boundary remains the safe fallback.
     */
    private CnCwCursorPath displaySemanticPosition(float x, float y) {
        int legacy = displayCursorPosition(x);
        List<CnCwSemanticSpan> spans = state.semanticSpans();
        if (spans.isEmpty()) return CnCwCursorPath.rootBoundary(legacy);

        RectF lcd = displayBounds(getWidth());
        float contentTop = lcd.top + lcd.height() * 0.145f;
        float contentBottom = lcd.bottom - dp(3);
        float baseSize = sp(25f);
        NaturalMetrics metrics = measureNatural(state.naturalExpression(), baseSize);
        float baseline = Math.min(contentTop + metrics.top + dp(2),
                contentTop + (contentBottom - contentTop) * 0.54f);

        CnCwSemanticSpan best = null;
        float bestScore = Float.MAX_VALUE;
        for (CnCwSemanticSpan span : spans) {
            float[] range = semanticSpanXRange(span);
            float left = Math.min(range[0], range[1]);
            float right = Math.max(range[0], range[1]);
            float pad = dp(12);
            if (x < left - pad || x > right + pad) continue;
            float centerY = semanticSlotCenterY(span.slot(), baseline, baseSize);
            float tolerance = semanticSlotYTolerance(span.slot(), baseSize);
            float vertical = Math.abs(y - centerY);
            if (vertical > tolerance) continue;
            float horizontal = x < left ? left - x : x > right ? x - right : 0f;
            float widthPenalty = Math.max(dp(4), right - left) * 0.018f;
            float depthBonus = span.childPath().size() * dp(1.5f);
            float score = vertical + horizontal * 1.25f + widthPenalty - depthBonus;
            if (score < bestScore) {
                bestScore = score;
                best = span;
            }
        }
        if (best == null) return CnCwCursorPath.rootBoundary(legacy);

        float[] range = semanticSpanXRange(best);
        float left = Math.min(range[0], range[1]);
        float right = Math.max(range[0], range[1]);
        int length = best.length();
        int offset;
        if (length <= 0 || right - left < dp(1)) {
            offset = 0;
        } else {
            float ratio = Math.max(0f, Math.min(1f, (x - left) / (right - left)));
            offset = Math.round(ratio * length);
        }
        return best.position(offset);
    }

    /** Horizontal range used by one semantic slot in the current LCD layout. */
    private float[] semanticSpanXRange(CnCwSemanticSpan span) {
        boolean stackedFraction = span.slot() == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                || span.slot() == CnCwCursorPath.Slot.FRACTION_DENOMINATOR;

        // The structural ^ token is not drawn at full-size between the base and
        // exponent.  Legacy boundary projection nevertheless allocates width to
        // its key label, which shifted exponent hit-testing to the right and made
        // base/exponent dragging feel sticky.  Anchor the exponent at the base's
        // visual end and measure only the visible exponent tokens at 0.62 scale,
        // exactly matching drawNaturalNode(SUPERSCRIPT).
        if (span.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT
                && !span.childPath().isEmpty()) {
            int templateBoundary = Math.max(span.containerStartBoundary(),
                    Math.min(span.containerEndBoundary(), span.childPath().get(0)));
            float left = displayBoundaryX(templateBoundary);
            float width = semanticTokenWidth(span.startBoundary(), span.endBoundary(), 0.62f);
            return new float[]{left, left + Math.max(dp(5), width)};
        }

        int start = stackedFraction ? span.containerStartBoundary() : span.startBoundary();
        int end = stackedFraction ? span.containerEndBoundary() : span.endBoundary();
        float left = displayBoundaryX(start);
        float right = displayBoundaryX(end);
        if (Math.abs(right - left) < dp(4)) {
            left = displayBoundaryX(span.containerStartBoundary());
            right = displayBoundaryX(span.containerEndBoundary());
        }
        return new float[]{left, right};
    }

    /** Measures visible token labels for a semantic slot at natural-display scale. */
    private float semanticTokenWidth(int startBoundary, int endBoundary, float textScale) {
        List<String> labels = machine.cursorTokenDisplays();
        int start = Math.max(0, Math.min(labels.size(), startBoundary));
        int end = Math.max(start, Math.min(labels.size(), endBoundary));
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(25f) * textScale);
        float width = 0f;
        for (int index = start; index < end; index++) {
            width += Math.max(dp(3), paint.measureText(labels.get(index)));
        }
        return width;
    }

    private float semanticSlotCenterY(CnCwCursorPath.Slot slot,
                                      float baseline, float baseSize) {
        return switch (slot) {
            case FRACTION_NUMERATOR -> baseline - baseSize * 0.63f;
            case FRACTION_DENOMINATOR -> baseline + baseSize * 0.30f;
            case SUPERSCRIPT_EXPONENT, ROOT_INDEX -> baseline - baseSize * 0.55f;
            case SUPERSCRIPT_BASE, RADICAL_CONTENT, ROOT_CONTENT, FUNCTION_ARGUMENT, ROW ->
                    baseline - baseSize * 0.18f;
        };
    }

    private float semanticSlotYTolerance(CnCwCursorPath.Slot slot, float baseSize) {
        return switch (slot) {
            case FRACTION_NUMERATOR, FRACTION_DENOMINATOR -> baseSize * 0.58f;
            case SUPERSCRIPT_EXPONENT, ROOT_INDEX -> baseSize * 0.48f;
            default -> baseSize * 0.62f;
        };
    }

    /** Converts a display x-coordinate into the nearest semantic boundary. */
    private int displayCursorPosition(float x) {
        List<String> labels = machine.cursorTokenDisplays();
        if (labels.isEmpty()) return 0;
        RectF lcd = displayBounds(getWidth());
        float baseSize = sp(25f);
        NaturalMetrics natural = measureNatural(state.naturalExpression(), baseSize);
        float available = lcd.width() - dp(12);
        float expressionX = lcd.left + dp(6);
        if (natural.width > available) {
            float cursorOffset = naturalCursorOffset(state.naturalExpression(), baseSize);
            float focus = cursorOffset < 0 ? natural.width : cursorOffset;
            expressionX = expressionX + available * 0.58f - focus;
            expressionX = Math.min(lcd.left + dp(6),
                    Math.max(lcd.left + dp(6) + available - natural.width, expressionX));
        }
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(baseSize);
        float rawWidth = 0f;
        for (String label : labels) rawWidth += Math.max(dp(4), paint.measureText(label));
        float scale = rawWidth <= 0 ? 1f : natural.width / rawWidth;
        float boundary = expressionX;
        int target = 0;
        for (int i = 0; i < labels.size(); i++) {
            float width = Math.max(dp(4), paint.measureText(labels.get(i))) * scale;
            if (x >= boundary + width * 0.5f) target = i + 1;
            boundary += width;
        }
        return Math.max(0, Math.min(machine.cursorLimit(), target));
    }

    /** Returns the approximate x-coordinate of a semantic insertion boundary. */
    private float displayBoundaryX(int boundaryIndex) {
        List<String> labels = machine.cursorTokenDisplays();
        RectF lcd = displayBounds(getWidth());
        float baseSize = sp(25f);
        NaturalMetrics natural = measureNatural(state.naturalExpression(), baseSize);
        float available = lcd.width() - dp(12);
        float expressionX = lcd.left + dp(6);
        if (natural.width > available) {
            float cursorOffset = naturalCursorOffset(state.naturalExpression(), baseSize);
            float focus = cursorOffset < 0 ? natural.width : cursorOffset;
            expressionX = expressionX + available * 0.58f - focus;
            expressionX = Math.min(lcd.left + dp(6),
                    Math.max(lcd.left + dp(6) + available - natural.width, expressionX));
        }
        if (labels.isEmpty()) return expressionX;
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(baseSize);
        float rawWidth = 0f;
        for (String label : labels) rawWidth += Math.max(dp(4), paint.measureText(label));
        float scale = rawWidth <= 0 ? 1f : natural.width / rawWidth;
        int clamped = Math.max(0, Math.min(labels.size(), boundaryIndex));
        float x = expressionX;
        for (int i = 0; i < clamped; i++) {
            x += Math.max(dp(4), paint.measureText(labels.get(i))) * scale;
        }
        return x;
    }

    private CnCwCursorPath semanticSelectionPathForBoundary(int boundary) {
        CnCwCursorPath anchor = state.semanticSelectionAnchor();
        CnCwCursorPath focus = state.semanticSelectionFocus();
        if (anchor != null && anchor.legacyTokenBoundary() == boundary) return anchor;
        if (focus != null && focus.legacyTokenBoundary() == boundary) return focus;
        return CnCwCursorPath.rootBoundary(boundary);
    }

    /** Projects a semantic selection/cursor boundary back onto the LCD x-axis. */
    private float displaySemanticBoundaryX(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary()) {
            return displayBoundaryX(path == null ? state.cursor() : path.legacyTokenBoundary());
        }
        for (CnCwSemanticSpan span : state.semanticSpans()) {
            if (!span.matches(path)) continue;
            float[] range = semanticSpanXRange(span);
            float left = range[0];
            float right = range[1];
            if (span.length() <= 0) return left;
            float ratio = Math.max(0f, Math.min(1f,
                    path.offset() / (float) span.length()));
            return left + (right - left) * ratio;
        }
        return displayBoundaryX(path.legacyTokenBoundary());
    }

    private void moveSelectionBoundaryToDisplayPosition(float x, float y) {
        CnCwCursorPath target = displaySemanticPosition(x, y);
        int legacy = target.legacyTokenBoundary();
        if (selectionDragEdge == 0) {
            if (legacy <= state.selectionStart() || x < displayDownX) selectionDragEdge = -1;
            else if (legacy >= state.selectionEnd() || x > displayDownX) selectionDragEdge = 1;
            else return;
        }
        if (target.equals(lastDragSemantic)) return;
        invalidateEvaluation();
        state = selectionDragEdge < 0
                ? machine.moveTouchSelectionStart(target)
                : machine.moveTouchSelectionEnd(target);
        lastDragCursor = selectionDragEdge < 0
                ? state.selectionStart() : state.selectionEnd();
        lastDragSemantic = semanticSelectionPathForBoundary(lastDragCursor);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
    }

    private void moveCursorAtomically(CnCwCursorPath target, boolean haptic) {
        if (target == null) target = CnCwCursorPath.rootBoundary(state.cursor());
        if (haptic && target.equals(lastDragSemantic)) return;
        invalidateEvaluation();
        state = machine.moveCursorTo(target);
        lastDragCursor = state.cursor();
        lastDragSemantic = state.semanticCursor();
        if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
    }

    private void copyDisplayText() {
        String text = cleanClipboardText(state.displayText());
        if (text == null || text.isBlank() || text.equals("│")) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), "已复制公式", Toast.LENGTH_SHORT).show();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private boolean isResultBand(float y) {
        RectF lcd = displayBounds(getWidth());
        float contentTop = lcd.top + lcd.height() * 0.145f;
        float contentBottom = lcd.bottom - dp(3);
        return y >= contentTop + (contentBottom - contentTop) * 0.58f;
    }

    private void showPasteOnlyMenu() {
        new AlertDialog.Builder(getContext()).setItems(new String[]{"粘贴"}, (dialog, which) -> {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            pasteClipboardText();
        }).show();
    }

    private void showClipboardMenu() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        boolean hasSelection = state.hasSelection();
        boolean hasAns = state.hasAns();
        List<String> actions = new ArrayList<>();
        if (hasSelection && !isStructuredResultScreen()) actions.add("复制选区");
        if (!isStructuredResultScreen()) actions.add("复制计算过程");
        actions.add("复制计算结果");
        if (hasAns) actions.add("复制 Ans");
        actions.add("粘贴");
        String[] items = actions.toArray(new String[0]);
        new AlertDialog.Builder(getContext()).setItems(items, (dialog, which) -> {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            String action = items[which];
            switch (action) {
                case "复制选区" -> copyText(cleanClipboardText(machine.selectedExpression()), "已复制选区");
                case "复制计算过程" -> copyText(cleanClipboardText(machine.calculationProcessDisplay()), "已复制展开后的计算过程");
                case "复制计算结果" -> copyText(decimalResult(state.result()), "已复制十进制结果");
                case "复制 Ans" -> copyText(ansClipboardText(), "已复制 Ans");
                default -> pasteClipboardText();
            }
        }).show();
    }

    private String ansClipboardText() {
        if (!state.hasAns()) return "";
        String currentResult = cleanClipboardText(state.result());
        if (state.resultShown() && !currentResult.isEmpty()) {
            return currentResult;
        }
        return BigDecimal.valueOf(state.ans()).stripTrailingZeros().toPlainString();
    }

    private String cleanClipboardText(String text) {
        if (text == null) return "";
        return text.replace("│", "").replace("▌", "").trim();
    }

    private String decimalResult(String text) {
        String clean = cleanClipboardText(text);
        if (clean.isEmpty()) return clean;
        try {
            return BigDecimal.valueOf(Double.parseDouble(clean)).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            int slash = clean.indexOf('/');
            if (slash > 0 && slash == clean.lastIndexOf('/')) {
                try {
                    BigDecimal a = new BigDecimal(clean.substring(0, slash).trim());
                    BigDecimal b = new BigDecimal(clean.substring(slash + 1).trim());
                    return a.divide(b, 12, java.math.RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString();
                } catch (ArithmeticException | NumberFormatException ignoredAgain) { }
            }
            return clean;
        }
    }

    private String decimalDisplayResult(String text) {
        if (text == null || text.contains("\n") || text.contains("=")) return text;
        return decimalResult(text);
    }

    private void copyText(String text, String message) {
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        if (value == null) return;

        invalidateEvaluation();
        int accepted = machine.pasteExpression(value.toString());
        state = machine.state();
        postInvalidateOnAnimation();
        String message = accepted < 0 ? "包含无法识别的符号，未粘贴"
                : accepted == 0 ? "没有可识别内容" : "已粘贴";
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        CnCwKey key = mapHardwareKey(keyCode, event);
        if (key == null) return super.onKeyDown(keyCode, event);
        if (event.getRepeatCount() == 0) {
            dispatchKey(key);
        }
        return true;
    }

    private void dispatchKey(CnCwKey key) {
        if (handleTableResultNavigation(key)) return;
        // Input mutations invalidate this request through the shared revision gate.
        // Keep identical in-flight work, including a result awaiting its UI callback.
        if (evaluating && pendingEvaluation != null && key == pendingEvaluationKey) return;
        long revision = invalidateEvaluation();
        if (machine.requiresEvaluation(key)) {
            CnCwMachine snapshot = machine.copyForEvaluation();
            evaluating = true;
            pendingEvaluationKey = key;
            postInvalidateOnAnimation();
            pendingEvaluation = evaluationExecutor.submit(() -> {
                CnCwUiState next;
                try {
                    next = snapshot.dispatch(key);
                } catch (CancellationException cancelled) {
                    post(() -> {
                        if (revision != inputRevision) return;
                        pendingEvaluation = null;
                        pendingEvaluationKey = null;
                        evaluating = false;
                        postInvalidateOnAnimation();
                    });
                    return;
                }
                post(() -> {
                    if (revision != inputRevision) return;
                    machine = snapshot;
                    state = next;
                    pendingEvaluation = null;
                    pendingEvaluationKey = null;
                    evaluating = false;
                    postInvalidateOnAnimation();
                });
            });
            return;
        }
        state = machine.dispatch(key);
        if (key == CnCwKey.ON && state.screen() == CnCwScreen.HOME) {
            state = machine.dispatch(CnCwKey.OK);
        }
        postInvalidateOnAnimation();
    }

    private boolean handleTableResultNavigation(CnCwKey key) {
        if (!state.resultShown() || !state.hasStructuredApplicationResult()) return false;
        CnCwModeEngine.ModeResult result = state.applicationResult();
        if (result == null || result.layout() != CnCwModeEngine.ResultLayout.TABLE) return false;
        int visibleRows = tableVisibleRows(result);
        int delta = switch (key) {
            case UP -> -1;
            case DOWN -> 1;
            case PAGE_UP -> -visibleRows;
            case PAGE_DOWN -> visibleRows;
            default -> 0;
        };
        if (delta == 0) return false;
        if (tableResult != result) {
            tableResult = result;
            tableFirstRow = 0;
        }
        int maxStart = Math.max(0, result.rows() - visibleRows);
        int next = Math.max(0, Math.min(maxStart, tableFirstRow + delta));
        if (next != tableFirstRow) {
            tableFirstRow = next;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        postInvalidateOnAnimation();
        return true;
    }

    private void cancelPendingEvaluation() {
        if (pendingEvaluation != null) {
            pendingEvaluation.cancel(true);
            pendingEvaluation = null;
        }
        evaluating = false;
        pendingEvaluationKey = null;
    }

    /** All View-to-machine edits share the same stale-result gate. */
    private long invalidateEvaluation() {
        inputRevision++;
        cancelPendingEvaluation();
        return inputRevision;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelGestures();
            invalidateEvaluation();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (evaluationExecutor.isShutdown()) evaluationExecutor = createEvaluationExecutor();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelGestures();
        invalidateEvaluation();
        evaluationExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }

    private CnCwKey mapHardwareKey(int keyCode, KeyEvent event) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP -> CnCwKey.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN -> CnCwKey.DOWN;
            case KeyEvent.KEYCODE_PAGE_UP -> CnCwKey.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN -> CnCwKey.PAGE_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT -> CnCwKey.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> CnCwKey.RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER -> CnCwKey.OK;
            case KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CnCwKey.EXE;
            case KeyEvent.KEYCODE_BACK -> CnCwKey.BACK;
            case KeyEvent.KEYCODE_ESCAPE -> CnCwKey.AC;
            case KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> CnCwKey.DEL;
            case KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> CnCwKey.DIGIT_0;
            case KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> CnCwKey.DIGIT_1;
            case KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> CnCwKey.DIGIT_2;
            case KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> CnCwKey.DIGIT_3;
            case KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> CnCwKey.DIGIT_4;
            case KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> CnCwKey.DIGIT_5;
            case KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> CnCwKey.DIGIT_6;
            case KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> CnCwKey.DIGIT_7;
            case KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> CnCwKey.DIGIT_8;
            case KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> CnCwKey.DIGIT_9;
            case KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT -> CnCwKey.DOT;
            case KeyEvent.KEYCODE_COMMA -> CnCwKey.COMMA;
            case KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_PLUS -> CnCwKey.ADD;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_MINUS -> CnCwKey.SUBTRACT;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_STAR -> CnCwKey.MULTIPLY;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_SLASH -> CnCwKey.DIVIDE;
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> CnCwKey.OPEN_PAREN;
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> CnCwKey.CLOSE_PAREN;
            case KeyEvent.KEYCODE_EQUALS -> CnCwKey.EQUALS;
            case KeyEvent.KEYCODE_H -> CnCwKey.HOME;
            default -> null;
        };
    }

    private KeyHit findHit(float x, float y) {
        float extension = dp(2.2f);
        for (KeyHit hit : hitMap) {
            if (x >= hit.touchBounds.left - extension && x <= hit.touchBounds.right + extension
                    && y >= hit.touchBounds.top - extension && y <= hit.touchBounds.bottom + extension) {
                return hit;
            }
        }
        return null;
    }

    private KeyHit findSpec(CnCwKey key) {
        for (KeyHit hit : hitMap) if (hit.spec.key == key) return hit;
        return null;
    }

    private String compactDisplayMode() {
        return switch (state.settings().displayMode()) {
            case FIX -> "FIX" + state.settings().displayDigits();
            case SCI -> "SCI" + state.settings().displayDigits();
            case NORM_1 -> "N1";
            case NORM_2 -> "N2";
        };
    }

    private String ellipsize(String text, float maxWidth) {
        if (text == null || paint.measureText(text) <= maxWidth) return text == null ? "" : text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }

    private float centeredBaseline(float top, float bottom) {
        paint.getFontMetrics(fontMetrics);
        return (top + bottom) * 0.5f - (fontMetrics.ascent + fontMetrics.descent) * 0.5f;
    }

    private void drawFittedCentered(Canvas canvas, String text, float centerX,
                                    float top, float bottom, float requestedSize,
                                    float maxWidth, float minimumSize, int color,
                                    Typeface typeface) {
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
        float size = requestedSize;
        paint.setTextSize(size);
        while (size > minimumSize && paint.measureText(text) > maxWidth) {
            size -= dp(0.5f);
            paint.setTextSize(size);
        }
        paint.setColor(color);
        canvas.drawText(text, centerX, centeredBaseline(top, bottom), paint);
    }

    private static float textLengthScale(String text) {
        if (text == null) return 1f;
        int length = text.codePointCount(0, text.length());
        if (length <= 2) return 1f;
        if (length == 3) return 0.96f;
        if (length == 4) return 0.90f;
        if (length == 5) return 0.84f;
        return 0.78f;
    }

    private float scaledSecondarySize(KeyHit hit) {
        if (hit.spec.kind == KeyKind.SHIFT) return hit.visual.secondaryTextSize();
        if (hit.spec.kind == KeyKind.STRIP || hit.spec.kind == KeyKind.FUNCTION) {
            return hit.visual.secondaryTextSize() * 0.86f;
        }
        return hit.visual.secondaryTextSize();
    }

    private float scaledMainSize(KeyHit hit) {
        if (hit.spec.kind == KeyKind.STRIP) return hit.visual.mainTextSize() * 0.90f;
        if (hit.spec.kind == KeyKind.FUNCTION) return hit.visual.mainTextSize() * 0.94f;
        return hit.visual.mainTextSize();
    }

    private static int colorFor(KeyKind kind) {
        return switch (kind) {
            case NUMBER -> KEY_NUMBER;
            case OPERATOR -> KEY_OPERATOR;
            case FUNCTION -> KEY_FUNCTION;
            case CONTROL, STRIP -> KEY_CONTROL;
            case NAV -> KEY_NAV;
            case OK -> KEY_OK;
            case SHIFT -> KEY_SHIFT;
            case ACTION -> KEY_ACTION;
            case EQUALS -> KEY_EXECUTE;
        };
    }

    private static int blend(int from, int to, float amount) {
        int red = (int) (Color.red(from) * (1f - amount) + Color.red(to) * amount);
        int green = (int) (Color.green(from) * (1f - amount) + Color.green(to) * amount);
        int blue = (int) (Color.blue(from) * (1f - amount) + Color.blue(to) * amount);
        return Color.rgb(red, green, blue);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }

    private enum KeyKind {
        NUMBER, OPERATOR, FUNCTION, CONTROL, STRIP, NAV, OK, SHIFT, ACTION, EQUALS
    }

    private record KeySpec(CnCwKey key, String main, String secondary, KeyKind kind,
                           boolean circular) { }
    private record KeyHit(KeySpec spec, RectF touchBounds, RectF visualBounds,
                          Cw991LayoutMetrics.KeyVisual visual) { }
    private record NaturalMetrics(float width, float top, float bottom) { }
}
