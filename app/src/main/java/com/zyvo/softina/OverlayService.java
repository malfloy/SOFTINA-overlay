package com.zyvo.softina;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/** Живёт в фоне, держит 3 окна: bubble (ручка) + panel (меню) + esp (боксы). */
public class OverlayService extends Service {
    private static final String TAG = "Softina";

    private WindowManager wm;
    private Handler ui;
    private SoftEngine engine;
    private SoftPanelUI panelUI;

    private View bubbleView, panelView, espView;
    private WindowManager.LayoutParams bubbleLp, panelLp, espLp;
    private float density;
    private boolean panelShown = false;

    private final SoftPanelUI.Delegate delegate = new SoftPanelUI.Delegate() {
        @Override public StateModel state() { return panelUI.st; }
        @Override public void onTab(int tab) { panelUI.st.tab = tab; redrawPanel(); }
        @Override public void onToggle(String key) {
            panelUI.st.flip(key);
            engine.sendState(panelUI.st.toJson());
            redrawPanel();
            redrawEsp();
        }
        @Override public void onClose() { showPanel(false); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        ui = new Handler(Looper.getMainLooper());
        density = getResources().getDisplayMetrics().density;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        engine = new SoftEngine();
        panelUI = new SoftPanelUI(density, delegate);

        // уведомление foreground — сервис живёт поверх всего
        // (канал создаётся лениво в notifyStart())
        notifyStart();

        bubbleLp = params(dp(46), dp(46), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(160));
        panelLp = params(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START, dp(60), dp(140));
        espLp = params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.TOP | Gravity.START, 0, 0);
        espLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        addBubble();
        addEsp();
        engine.listener = new SoftEngine.EspListener() {
            @Override public void onFrame(SoftEngine.EspData d) { state.espData = d; redrawEsp(); }
            @Override public void onStatus(String s) { panelUI.st.status = s; redrawPanel(); }
        };
        engine.start(this);
    }

    private final StateModel state = new StateModel();

    // ------------------------------------------------------------ окна

    private WindowManager.LayoutParams params(int w, int h, int grav, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, type(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = grav; lp.x = x; lp.y = y;
        return lp;
    }

    private int dp(float v) { return (int)(v * density + 0.5f); }

    private int type() {
        return android.os.Build.VERSION.SDK_INT >= 26
            ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 2003;
    }

    private void addBubble() {
        bubbleView = new View(this) {
            @Override public void draw(Canvas c) {
                super.draw(c);
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                float pulse = 0.6f + 0.4f * (float)Math.abs(Math.sin(System.currentTimeMillis() / 700.0));
                p.setColor(android.graphics.Color.argb((int)(45 * pulse), 0x7C, 0x5C, 0xFF));
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, p);
                p.setColor(0xFF7C5CFF);
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - dp(5), p);
                android.graphics.Typeface tf = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL);
                p.setColor(0xFFFFFFFF); p.setTypeface(tf);
                p.setTextSize(getWidth() * 0.42f);
                float tw = p.measureText("S");
                c.drawText("S", (getWidth() - tw) / 2f, getHeight() / 2f + p.getTextSize() * 0.36f, p);
            }
        };
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY; private float dx, dy; private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); moved = false;
                        dx = bubbleLp.x; dy = bubbleLp.y;
                        v.performClick();
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        float nx = e.getRawX() - downX, ny = e.getRawY() - downY;
                        if (Math.abs(nx) > dp(8) || Math.abs(ny) > dp(8)) moved = true;
                        if (moved) { bubbleLp.x = (int)(dx + nx); bubbleLp.y = (int)(dy + ny); wm.updateViewLayout(v, bubbleLp); }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) panelShown = !panelShown;
                        showPanel(panelShown);
                        break;
                }
                return true;
            }
        });
        wm.addView(bubbleView, bubbleLp);
        ui.post(bubblePulse);
    }

    private final Runnable bubblePulse = new Runnable() {
        @Override public void run() {
            if (bubbleView != null && bubbleView.isAttachedToWindow()) {
                bubbleView.invalidate();
                ui.postDelayed(this, 40);
            }
        }
    };

    private void addEsp() {
        espView = new View(this) {
            @Override public void draw(Canvas c) {
                super.draw(c);
                if (panelUI == null || state.espData == null) return;
                SoftEngine.EspData d = state.espData;
                if (!panelUI.st.enabled) return;
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.6f));
                int n = d.box.length / 5;
                for (int i = 0; i < n; i++) {
                    float x = d.box[i * 5], y = d.box[i * 5 + 1];
                    float bw = d.box[i * 5 + 2], bh = d.box[i * 5 + 3];
                    float hp = d.box[i * 5 + 4];
                    if (bw <= 0 || bh <= 0) continue;
                    p.setColor(ColorBox.hsl(hp));
                    c.drawRect(x, y, x + bw, y + bh, p);
                    // полоска HP справа
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    c.drawRect(x + bw + dp(2), y + bh * (1 - hp), x + bw + dp(5), y + bh, p);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                }
                if (panelUI.st.tracers) {
                    p.setStrokeWidth(dp(1.2f));
                    p.setColor(0x40FFFFFF);
                    int nt = d.tracer.length / 4;
                    for (int i = 0; i < nt; i++)
                        c.drawLine(d.tracer[i * 4], d.tracer[i * 4 + 1], d.tracer[i * 4 + 2], d.tracer[i * 4 + 3], p);
                }
            }
        };
        wm.addView(espView, espLp);
    }

    private void showPanel(boolean on) {
        if (on && panelView == null) {
            panelUI.markShown();
        panelView = new View(this) {
            @Override public void draw(Canvas c) {
                super.draw(c);
                if (panelUI != null) panelUI.draw(c);
            }
            @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && panelUI != null && panelUI.closeAt(ev.getX(), ev.getY())) {
                        showPanel(false); panelShown = false; return true;
                    }
                    if (panelUI != null) {
                        int tab = panelUI.tabAt(ev.getX(), ev.getY());
                        if (tab >= 0 && ev.getActionMasked() == MotionEvent.ACTION_DOWN) { delegate.onTab(tab); return true; }
                        String key = panelUI.rowAt(ev.getX(), ev.getY());
                        if (key != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) { delegate.onToggle(key); return true; }
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
            panelView.setAlpha(0);
            wm.addView(panelView, panelLp);
            ui.postDelayed(panelPulse, 30);
            engine.sendState(panelUI.st.toJson());
        } else if (!on && panelView != null) {
            wm.removeView(panelView);
            panelView = null;
        }
        if (panelShown != on) {
            panelShown = on;
            if (bubbleView != null) bubbleView.invalidate();
        }
    }

    private final Runnable panelPulse = new Runnable() {
        @Override public void run() {
            if (panelView == null) return;
            panelUI.tick();
            panelView.setAlpha(1f);
            panelView.invalidate();
            ui.postDelayed(this, 16);
        }
    };

    private void redrawPanel() { if (panelView != null) panelView.invalidate(); }
    private void redrawEsp() { if (espView != null) espView.invalidate(); }

    @Override
    public void onDestroy() {
        engine.stop();
        for (View v : new View[]{bubbleView, panelView, espView}) {
            if (v != null && v.isAttachedToWindow()) try { wm.removeView(v); } catch (Exception ignored) { }
        }
        bubbleView = panelView = espView = null;
        super.onDestroy();
    }

    private void addEsp() {
        espView = new View(this) {
            @Override public void draw(Canvas c) {
                super.draw(c);
                if (panelUI == null || state.espData == null) return;
                SoftEngine.EspData d = state.espData;
                if (!panelUI.st.enabled) return;
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.6f));
                int n = d.box.length / 5;
                for (int i = 0; i < n; i++) {
                    float x = d.box[i * 5], y = d.box[i * 5 + 1];
                    float bw = d.box[i * 5 + 2], bh = d.box[i * 5 + 3];
                    float hp = d.box[i * 5 + 4];
                    if (bw <= 0 || bh <= 0) continue;
                    p.setColor(ColorBox.hsl(hp));
                    c.drawRect(x, y, x + bw, y + bh, p);
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    c.drawRect(x + bw + dp(2), y + bh * (1 - hp), x + bw + dp(5), y + bh, p);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                }
                if (panelUI.st.tracers) {
                    p.setStrokeWidth(dp(1.2f));
                    p.setColor(0x40FFFFFF);
                    int nt = d.tracer.length / 4;
                    for (int i = 0; i < nt; i++)
                        c.drawLine(d.tracer[i * 4], d.tracer[i * 4 + 1], d.tracer[i * 4 + 2], d.tracer[i * 4 + 3], p);
                }
            }
        };
        wm.addView(espView, espLp);
    }

    private void notifyStart() {
        android.app.NotificationChannel ch = new android.app.NotificationChannel("softina", "SOFTINA overlay",
            android.app.NotificationManager.IMPORTANCE_LOW);
        ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        android.app.Notification n = new android.app.Notification.Builder(this, "softina")
            .setContentTitle("SOFTина активна")
            .setContentText("Оверлей работает")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build();
        startForeground(1, n);
    }
}
