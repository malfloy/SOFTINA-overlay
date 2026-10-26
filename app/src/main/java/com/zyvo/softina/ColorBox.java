package com.zyvo.softina;

/** Цвет ESP по HP: 1 = зелёный, 0 = красный (HSL-хелпер). */
public class ColorBox {
    public static int hsl(float hp01) {
        float h = Math.max(0f, Math.min(1f, hp01)) * 130f; // 0(red)..130(green)
        return hsvHue(h, 0.85f, 1f, 0xd8);
    }
    private static final float[] tmp = new float[3];
    public static int hsvHue(float hue, float sat, float val, int alpha) {
        float hh = hue / 60f;
        int i = (int)Math.floor(hh);
        float f = hh - i;
        float p = val * (1 - sat), q = val * (1 - sat * f), t = val * (1 - sat * (1 - f));
        float r, g, b;
        switch (i) {
            case 0: r = val; g = t; b = p; break;
            case 1: r = q; g = val; b = p; break;
            case 2: r = p; g = val; b = t; break;
            case 3: r = p; g = q; b = val; break;
            case 4: r = t; g = p; b = val; break;
            default: r = val; g = p; b = q; break;
        }
        return (alpha << 24) | (((int)(r * 255)) << 16) | (((int)(g * 255)) << 8) | ((int)(b * 255));
    }
}
