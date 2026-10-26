package com.zyvo.softina;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Всё рисование главного меню (тонкое стекло, вкладки, тогглы, кнопки).
 * Окно рисуется в координатах самого panel-вью (wrapContent задаёт service).
 */
public class SoftPanelUI {
    public interface Delegate {
        StateModel state();
        void onTab(int tab);
        void onToggle(String key);
        void onClose();
    }

    private final float S;                 // 1dp в px
    private final Delegate d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int ACCENT = 0xFF7C5CFF;
    private static final int GLASS  = 0xF2141620;
    private static final int TXT_1  = 0xFFF2F3FF;
    private static final int TXT_2  = 0xFF9AA0BC;
    private static final String[] TABS = {"VISUALS", "MOVEMENT", "DANGER"};

    /** цвет с новым альфа-каналом */
    static int putA(int color, float alpha) {
        int a = (int) alpha;
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | (color & 0xFFFFFF);
    }

    // вычисленные hit-зоны (в координатах вью)
    private final RectF hzClose = new RectF();
    private final RectF[] hzTab = { new RectF(), new RectF(), new RectF() };
    private final RectF[] hzRow = new RectF[16];
    private final String[] keyRow = new String[16];
    private int rows;

    private long animStart;
    private float openAnim;
    private boolean pressToggle = false;
    private String pressKey;

    public SoftPanelUI(float density, Delegate dlg) { S = density; d = dlg; }

    public void markShown() { animStart = System.currentTimeMillis(); }

    public void tick() {
        float t = clamp((System.currentTimeMillis() - animStart) / 260f);
        if (t != openAnim) openAnim = ease(t);
    }

    public int widthDp()  { return 330; }
    public int heightDp() { return 430; }

    /** true = окно отрисовано полностью (можно разрешать тапы). */
    public boolean draw(Canvas c) {
        final int W = c.getWidth(), H = c.getHeight();
        final float w = W - 1f, h = H - 1f, r = 20 * S, e = openAnim;

        hzClose.set(0, 0, 0, 0);
        hzTab[0].setEmpty(); hzTab[1].setEmpty(); hzTab[2].setEmpty();
        rows = 0;
        if (e <= 0.02f) return false;

        // внешняя рамка-свечение
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.6f * S);
        fill.setColor(putA(ACCENT, 100 * e));
        c.drawRoundRect(new RectF(1, 1, w - 1, h - 1), r, r, fill);

        // стекло
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(putA(GLASS, 249 * e));
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, fill);

        final float th = 52 * S;

        // верхний градиент
        LinearGradient top = new LinearGradient(0, 0, w, th,
            putA(ACCENT, 46 * e), putA(ACCENT, 0), Shader.TileMode.CLAMP);
        c.save(); c.clipRect(0, 0, w, th); c.drawPaint(top); c.restore();

        // заголовок
        float pulse = 0.7f + 0.3f * (float)Math.sin(System.currentTimeMillis() / 260.0);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(putA(ACCENT, (int)(235 * pulse) * e));
        c.drawCircle(18 * S + 3 * S, th / 2, 3.2f * S, fill);
        drawText(c, 30 * S, th / 2, "SOFTINA", 17 * S, putA(TXT_1, 250 * e), true, false);
        drawText(c, 30 * S + 74 * S, th / 2 + 1.5f * S, "v2.0", 10 * S, putA(TXT_2, 210 * e), false, false);
        drawText(c, w - 17 * S, th / 2, "X", 13 * S, putA(0xFF8A90A8, 240 * e), true, false);
        hzClose.set(w - 30 * S, 4 * S, w, th - 4 * S);

        // линия-акцент под заголовком
        fill.setShader(new LinearGradient(16 * S, 0, w - 16 * S, 0,
            putA(ACCENT, 220 * e), putA(ACCENT, 0), Shader.TileMode.CLAMP));
        fill.setStyle(Paint.Style.FILL);
        c.drawRect(16 * S, th - 2 * S, w - 16 * S, th - 0.5f * S, fill);
        fill.setShader(null);

        // левая колонка вкладок
        float sy = th + 10 * S, shh = h - sy - 12 * S;
        fill.setColor(putA(0xFFFFFFFF, 8 * e));
        c.drawRoundRect(new RectF(10 * S, sy, 10 * S + 92 * S, sy + shh), 12 * S, 12 * S, fill);

        float contentX = 10 * S + 92 * S + 10 * S;
        float contentW = w - contentX - 12 * S;

        for (int i = 0; i < TABS.length; i++) {
            float ty = sy + 7 * S + i * 40 * S;
            boolean sel = (i == d.state().tab);
            RectF rr = new RectF(16 * S, ty, 96 * S - 6 * S, ty + 34 * S);
            if (sel) {
                fill.setColor(putA(ACCENT, 62 * e));
                c.drawRoundRect(rr, 10 * S, 10 * S, fill);
                fill.setColor(putA(TXT_1, 240 * e));
                c.drawRoundRect(new RectF(rr.left + 2 * S, ty + 8 * S, rr.left + 5.5f * S, ty + 26 * S), 2 * S, 2 * S, fill);
            }
            drawText(c, rr.left + 11 * S, ty + 17 * S, TABS[i], 10.5f * S, sel ? putA(TXT_1, 255 * e) : putA(0xFF969CB6, 215 * e), sel, false);
            hzTab[i].set(rr);
        }

        // ---- контент ----
        float cy = sy + 6 * S;
        StateModel s = d.state();

        if (s.tab == 0) {
            cy = section(c, "ПОДКЛЮЧЕНИЕ", cy, contentX, contentW, e);
            cy = toggleRow(c, "ESP Box", "box", cy, contentX, contentW, e);
            cy = toggleRow(c, "Трейсеры", "tracers", cy, contentX, contentW, e);
            cy = toggleRow(c, "Отрисовка включена", "enabled", cy, contentX, contentW, e);
        } else if (s.tab == 1) {
            cy = section(c, "ПОБЕГИ · ДВИЖЕНИЕ", cy, contentX, contentW, e);
            cy = toggleRow(c, "Fly свобода", "fly", cy, contentX, contentW, e);
            cy = toggleRow(c, "NoClip сквозь стены", "noclip", cy, contentX, contentW, e);
            cy = toggleRow(c, "God — без урона", "god", cy, contentX, contentW, e);
        } else {
            cy = section(c, "ДИАГНОСТИКА", cy, contentX, contentW, e);
            drawText(c, contentX + 12 * S, cy + 15 * S, s.status, 10.5f * S, putA(TXT_2, 235 * e), false, false);
            cy += 30 * S;
            cy = buttonRow(c, "ПРОВЕРИТЬ СВЯЗЬ", "send", cy, contentX, contentW, e);
        }

        // строка статуса снизу
        drawText(c, 18 * S, h - 12 * S, s.footer, 10f * S, putA(TXT_2, 210 * e), false, false);
        return e > 0.85f;
    }

    // ---- hit тестирование UI событий (координаты окна: (0,0) = левый верх окна) ----
    public int tabAt(float x, float y) {
        for (int i = 0; i < 3; i++) if (hzTab[i].contains(x, y)) return i;
        return -1;
    }
    public String rowAt(float x, float y) {
        for (int i = 0; i < rows; i++) if (azRowContains(i, x, y)) return keyRow[i];
        return null;
    }
    public boolean closeAt(float x, float y) { return hzClose.contains(x, y); }

    private boolean azRowContains(int i, float x, float y) {
        return hzRow[i] != null && hzRow[i].contains(x, y);
    }

    // ================= helpers =================
    private float section(Canvas c, String t, float cy, float cx0, float pw, float e) {
        drawText(c, cx0 + 2 * S, cy + 12 * S, t, 9.5f * S, putA(ACCENT, 235 * e), true, false);
        return cy + 22 * S;
    }

    private float toggleRow(Canvas c, String label, String key, float cy, float cx0, float pw, float e) {
        boolean on = d.state().flag(key);
        RectF card = new RectF(cx0, cy, cx0 + pw, cy + 42 * S);
        fill.setShader(null);
        fill.setColor(putA(0xFFFFFFFF, (on ? 13 : 8) * e));
        fill.setStyle(Paint.Style.FILL);
        c.drawRoundRect(card, 12 * S, 12 * S, fill);
        drawText(c, card.left + 12 * S, card.centerY(), label, 12 * S, putA(0xFFE4E7F7, 240 * e), false, false);

        float swW = 42 * S, swH = 23 * S;
        RectF sw = new RectF(card.right - swW - 10 * S, card.centerY() - swH / 2, card.right - 10 * S, card.centerY() + swH / 2);
        fill.setColor(putA(on ? ACCENT : 0xFF454A60, on ? 235 * e : 150));
        c.drawRoundRect(sw, swH / 2, swH / 2, fill);
        float knobR = swH - 5 * S;
        float knobX = on ? sw.right - knobR - 2.5f * S : sw.left + 2.5f * S;
        fill.setColor(putA(TXT_1, 255 * e));
        c.drawCircle(knobX + (knobR) / 2, sw.centerY(), (knobR) / 2, fill);

        hzRow[rows] = card; keyRow[rows++] = key;
        return cy + 49 * S;
    }

    private float buttonRow(Canvas c, String label, String key, float cy, float cx0, float pw, float e) {
        RectF card = new RectF(cx0, cy, cx0 + pw, cy + 40 * S);
        fill.setShader(new LinearGradient(card.left, 0, card.right, 0,
            putA(ACCENT, 235 * e), putA(0xFF5B41CC, 235 * e), Shader.TileMode.CLAMP));
        fill.setStyle(Paint.Style.FILL);
        c.drawRoundRect(card, 12 * S, 12 * S, fill);
        fill.setShader(null);
        drawText(c, card.centerX(), card.centerY(), label, 12 * S, putA(TXT_1, 255 * e), true, false);
        hzRow[rows] = card; keyRow[rows++] = key;
        return cy + 47 * S;
    }

    /** cx = левый край (или центр при centered), cy = вертикальный центр строки. */
    private void drawText(Canvas c, float cx, float cy, String s, float size, int colr, boolean bold, boolean centered) {
        text.setColor(colr);
        text.setTextSize(size);
        text.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                              : Typeface.create("sans-serif", Typeface.NORMAL));
        if (centered) cx -= text.measureText(s) / 2;
        c.drawText(s, cx, cy + size * 0.36f, text);
    }
    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
}
