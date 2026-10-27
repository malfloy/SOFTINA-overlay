package com.zyvo.softina;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private Button startBtn;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.setBackgroundColor(0xFF0B0C14);

        // пустой распор: прижимаем карточки и кнопки к низу экрана
        root.addView(new View(this), new LinearLayout.LayoutParams(-2, 0, 1f));

        TextView logo = new TextView(this);
        logo.setText("SOFTINA");
        logo.setTextSize(34f);
        logo.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
        logo.setTextColor(0xFFF2F3FF);
        root.addView(logo, lp(-2, -2, 0, 0, 0));

        TextView sub = new TextView(this);
        sub.setText("v2.0  ·  оверлей-меню для BERDA\nрисует поверх любого рендера (GL / Vulkan)");
        sub.setTextSize(13f);
        sub.setTextColor(0xFF9A9FB8);
        root.addView(sub, lp(-2, -2, 0, 8, 0));

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        
        cards.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(cards, lp(-1, -2, 0, 24, 0));
        statusView = new TextView(this);
        statusView.setTextSize(14f);
        statusView.setTextColor(0xFFF2F3FF);
        statusView.setLineSpacing(dp(3), 1.1f);
        cards.addView(statusView, lp(-2, -2));

        TextView hint = new TextView(this);
        hint.setText("Порядок работы: 1) разрешай оверлей 2) SOFTINA попроси root — согласись\n3) открой BERDA — снизу экрана появится светлая ручка и меню");
        hint.setTextSize(12f);
        hint.setTextColor(0xFF9A9FB8);
        root.addView(hint, lp(-2, -2, 0, 16, 0));

        startBtn = new Button(this);
        startBtn.setText("ЗАПУСТИТЬ ОВЕРЛЕЙ");
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setTextSize(15f);
        startBtn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable gd = new GradientDrawable();
        gd.setColors(new int[]{0xFF7C5CFF, 0xFF5B41CC});
        gd.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        gd.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), dp(28), dp(28), dp(28), dp(28)});
        startBtn.setBackground(gd);
        startBtn.setAllCaps(false);
        root.addView(startBtn, lp(-1, dp(56), 0, 12, 0));

        Button stop = new Button(this);
        stop.setText("ОСТАНОВИТЬ");
        stop.setTextColor(0xFF9A9FB8);
        stop.setTextSize(13f);
        stop.setAllCaps(false);
        stop.setBackgroundColor(0x00000000);
        root.addView(stop, lp(-1, dp(44), 0, 0, 0));

        startBtn.setOnClickListener(v -> tryStart());
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean root = checkRoot();
        boolean game = isInstalled("com.heesoyam.berda");
        statusView.setText((root ? "● " : "○ ") + "Root: " + (root ? "OK" : "нет")
            + "\n● Оверлей: " + (overlay ? "разрешён" : "не разрешён")
            + "\n● BERDA: " + (game ? "установлена" : "нет"));
        startBtn.setEnabled(true);
    }

    private boolean checkRoot() {
        try {
            // su сначала может молча не ответить — шлём три попытки с таймаутом
            for (int i = 0; i < 3; i++) {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                java.util.Scanner s = new java.util.Scanner(p.getInputStream());
                boolean ok = s.findInLine("uid=0") != null;
                s.close();
                p.waitFor();
                if (ok) return true;
                Thread.sleep(300);
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) { return false; }
    }

    private void tryStart() {
        if (!checkRoot()) { Toast.makeText(this, "Root не найден", Toast.LENGTH_LONG).show(); return; }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "В настройках включи «Поверх других окон» для SOFTINA", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Оверлей запущен — открывай BERDA", Toast.LENGTH_LONG).show();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private static LinearLayout.LayoutParams lp(int w, int h) {
        return lp(w, h, 0, 0, 0);
    }
    private static LinearLayout.LayoutParams lp(int w, int h, int mt, int ml, int extra) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        if (mt > 0) p.topMargin = mt;
        if (ml > 0) p.leftMargin = ml;
        if (extra > 0) p.bottomMargin = extra;
        return p;
    }
}
