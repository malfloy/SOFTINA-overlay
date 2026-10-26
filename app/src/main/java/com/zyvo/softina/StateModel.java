package com.zyvo.softina;

/** Состояние меню: флаги + счётчики. Сериализуется в JSON для нативного антиидота. */
public class StateModel {
    public int tab = 0;
    public boolean enabled = true, box = true, tracers = true;
    public boolean fly = false, noclip = false, god = false;
    public String status = "Ожидаю данные от инжекта…";
    public String footer = "root · подключение к игре";

    public boolean flag(String key) {
        switch (key) {
            case "box": return box;
            case "tracers": return tracers;
            case "enabled": return enabled;
            case "fly": return fly;
            case "noclip": return noclip;
            case "god": return god;
            default: return false;
        }
    }
    public void flip(String key) {
        switch (key) {
            case "box": box = !box; break;
            case "tracers": tracers = !tracers; break;
            case "enabled": enabled = !enabled; break;
            case "fly": fly = !fly; break;
            case "noclip": noclip = !noclip; break;
            case "god": god = !god; break;
        }
    }
    public String toJson() {
        return "{\"box\":" + box + ",\"tracers\":" + tracers + ",\"enabled\":" + enabled
            + ",\"fly\":" + fly + ",\"noclip\":" + noclip + ",\"god\":" + god + "}";
    }
}
