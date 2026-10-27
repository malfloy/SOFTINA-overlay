package com.zyvo.softina;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Root bridge: пишет команды в cmd-файл игры и стримит ESP-данные из её logs. */
public class SoftEngine {
    public interface EspListener { void onFrame(EspData d); void onStatus(String s); }

    public static class EspData {
        public int screenW = 1080, screenH = 1920;
        public int mode = 0;          // 0=GL hook, 1=headless (vulkan)
        public int players = 0;
        public float[] box = new float[0];   // x, y, w, h + hp на игрока
        public float[] tracer = new float[0];// x1, y1, x2, y2
    }

    public static final String CMD_PATH = "/data/data/com.heesoyam.berda/files/softina_cmd.json";
    public static final String ESP_PATH = "/data/data/com.heesoyam.berda/cache/softina_esp.jsonl";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private Process tailProc;
    public EspListener listener;

    public void start(Context ctx) {
        if (running || listener == null) return;
        running = true;
        new Thread(() -> {
            // 1) создадим/проверим cmd-файл игры
            sh("mkdir -p /data/data/com.heesoyam.berda/files /data/data/com.heesoyam.berda/cache");
            sh("echo '{}' > '" + CMD_PATH + "'");
            sh("touch '" + ESP_PATH + "'");
            sh("chmod 666 '" + ESP_PATH + "' 2>/dev/null");
            // 2) стрим ESP: отдельный long-lived su (tail -f) — fork только один раз
            try {
                ProcessBuilder pb = new ProcessBuilder("su", "-c", "tail -F -n 0 '" + ESP_PATH + "'");
                pb.redirectErrorStream(true);
                tailProc = pb.start();
                BufferedReader r = new BufferedReader(new java.io.InputStreamReader(tailProc.getInputStream(), "UTF-8"));
                EspData cur = new EspData();
                String line;
                while (running && (line = r.readLine()) != null) {
                    EspData d = parse(line, cur);
                    if (d != null) {
                        cur = d;
                        EspListener l = listener;
                        ui.post(() -> l.onFrame(d));
                        ui.post(() -> l.onStatus(fmtStatus(d)));
                    }
                }
            } catch (Exception ignored) { }
        }, "softina-esp").start();
    }

    public void stop() {
        running = false;
        if (tailProc != null) { tailProc.destroy(); tailProc = null; }
    }

    public interface InjectCb { void done(String result); }

    /** ptrace-инжект libbasemenu.so в запущенный BERDA (wrap-prop не трогаем). */
    public void runInject(final InjectCb cb) {
        new Thread(() -> {
            String out = shOut("sh /data/local/tmp/soft_inject.sh 2>&1").trim();
            if (out.isEmpty()) out = "нет ответа от su";
            final String f = out;
            ui.post(() -> cb.done(f));
        }, "softina-inject").start();
    }

    private String shOut(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder b = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) b.append(l).append('\n');
            p.waitFor();
            r.close();
            return b.toString();
        } catch (Exception e) {
            return "su error: " + e.getMessage();
        }
    }

    /** Команда от UI: записать JSON в cmd-файл игры. */
    public void sendState(String json) {
        final String sj = json;
        new Thread(() -> sh("printf '%s' '" + sj.replace("'", "") + "' > '" + CMD_PATH + "'"), "softina-cmd").start();
    }

    public static String statusOnce(Context ctx) {
        return "{\"ping\":" + System.currentTimeMillis() + "}";
    }

    private static String fmtStatus(EspData d) {
        return (d.mode == 0 ? "GL-hook" : "headless") + "  ·  players: " + d.players
            + "  ·  screen " + d.screenW + "x" + d.screenH;
    }

    // ---- околи парсер однострочного JSON: {"mode":1,"w":..,"h":..,"p":[x,y,w,h,hp,...],"t":[x1,y1,x2,y2,...]} ----
    private static EspData parse(String line, EspData prev) {
        try {
            EspData d = new EspData();
            d.mode = num(line, "mode", prev.mode);
            d.screenW = num(line, "w", prev.screenW);
            d.screenH = num(line, "h", prev.screenH);
            d.box = arr(line, "p");
            d.players = d.box.length / 5;
            d.tracer = arr(line, "t");
            return d;
        } catch (Exception e) { return null; }
    }

    private static int num(String s, String key, int def) {
        int i = s.indexOf("\"" + key + "\":");
        if (i < 0) return def;
        i += key.length() + 3;
        int j = i;
        while (j < s.length() && ("-0123456789".indexOf(s.charAt(j)) >= 0)) j++;
        try { return Integer.parseInt(s.substring(i, j)); } catch (Exception e) { return def; }
    }

    /** float-массив из "key":[v,v,...] — с плоскими числами. */
    private static float[] arr(String s, String key) {
        int i = s.indexOf("\"" + key + "\":[");
        if (i < 0) return new float[0];
        i += key.length() + 3;
        int j = s.indexOf(']', i);
        if (j < 0) return new float[0];
        String[] parts = s.substring(i + 1, j).split(",");
        float[] out = new float[parts.length];
        try {
            for (int k = 0; k < parts.length; k++) out[k] = Float.parseFloat(parts[k].trim());
            return out;
        } catch (Exception e) { return new float[0]; }
    }

    private static void sh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
        } catch (Exception ignored) { }
    }
}
