package pl.gemshop;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Male helpery rysowania w przestrzeni ekranu (int px). */
public final class Gfx {
    private static final Map<String, List<int[]>> OUT = new HashMap<>();

    private Gfx() {}

    public static int alpha(int c, float m) {
        int a = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, m)));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            int x = (a >>> sh) & 0xFF, y = (b >>> sh) & 0xFF;
            r |= (Math.round(x + (y - x) * t) & 0xFF) << sh;
        }
        return r;
    }

    public static void rr(DrawContext g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0 || ((c >>> 24) & 0xFF) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, c); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int in = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + in, y + i, x + w - in, y + i + 1, c);
            g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, c);
        }
        if (h - 2 * r > 0) g.fill(x, y + r, x + w, y + h - r, c);
    }

    public static void circle(DrawContext g, int cx, int cy, int r, int c) {
        if (r <= 0 || ((c >>> 24) & 0xFF) == 0) return;
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(r * r - yy * yy));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, c);
        }
    }

    /** Obrys 1px zaokraglonego prostokata jako lista "runow" {x0,y0,x1,y1}. */
    public static List<int[]> outline(int w, int h, int r) {
        String key = w + "x" + h + "x" + r;
        List<int[]> cached = OUT.get(key);
        if (cached != null) return cached;
        r = Math.min(r, Math.min(w, h) / 2);
        List<int[]> runs = new ArrayList<>();
        if (r <= 0) {
            runs.add(new int[]{0, 0, w, 1}); runs.add(new int[]{0, h - 1, w, h});
            runs.add(new int[]{0, 1, 1, h - 1}); runs.add(new int[]{w - 1, 1, w, h - 1});
        } else {
            int[] ins = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                ins[i] = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            }
            runs.add(new int[]{ins[0], 0, w - ins[0], 1});
            runs.add(new int[]{ins[0], h - 1, w - ins[0], h});
            for (int i = 1; i < r; i++) {
                int x0 = ins[i], x1 = Math.max(ins[i - 1], ins[i] + 1);
                runs.add(new int[]{x0, i, x1, i + 1});
                runs.add(new int[]{w - x1, i, w - x0, i + 1});
                runs.add(new int[]{x0, h - 1 - i, x1, h - i});
                runs.add(new int[]{w - x1, h - 1 - i, w - x0, h - i});
            }
            if (h - 2 * r > 0) {
                runs.add(new int[]{0, r, 1, h - r});
                runs.add(new int[]{w - 1, r, w, h - r});
            }
        }
        OUT.put(key, runs);
        return runs;
    }

    public static void outlineRR(DrawContext g, int x, int y, int w, int h, int r, int c) {
        for (int[] ru : outline(w, h, r)) g.fill(x + ru[0], y + ru[1], x + ru[2], y + ru[3], c);
    }

    /** Linia o grubosci w jako obrocony quad (w warstwie GUI). */
    public static void line(DrawContext g, float x1, float y1, float x2, float y2, float w, int c) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f || ((c >>> 24) & 0xFF) == 0) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        float[] xs = {x1 + nx, x1 - nx, x2 - nx, x2 + nx};
        float[] ys = {y1 + ny, y1 - ny, y2 - ny, y2 + ny};
        float area = 0;
        for (int i = 0; i < 4; i++) { int j = (i + 1) & 3; area += xs[i] * ys[j] - xs[j] * ys[i]; }
        VertexConsumer vc = g.getVertexConsumers().getBuffer(RenderLayer.getGui());
        Matrix4f m = g.getMatrices().peek().getPositionMatrix();
        if (area > 0) {
            for (int k = 3; k >= 0; k--) vc.vertex(m, xs[k], ys[k], 0f).color(c);
        } else {
            for (int k = 0; k < 4; k++) vc.vertex(m, xs[k], ys[k], 0f).color(c);
        }
    }

    private static Text t(String s, boolean bold) {
        return bold ? Text.literal(s).formatted(Formatting.BOLD) : Text.literal(s);
    }

    public static float width(TextRenderer tr, String s, boolean bold) { return tr.getWidth(t(s, bold)); }

    public static void text(DrawContext g, TextRenderer tr, String s, float x, float y, int color, float scale, boolean bold) {
        if (s == null || s.isEmpty() || ((color >>> 24) & 0xFF) < 8) return;
        MatrixStack m = g.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(scale, scale, 1f);
        g.drawText(tr, t(s, bold), 0, 0, color, false);
        m.pop();
    }

    public static void textOrdered(DrawContext g, TextRenderer tr, OrderedText s, float x, float y, int color, float scale) {
        if (((color >>> 24) & 0xFF) < 8) return;
        MatrixStack m = g.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(scale, scale, 1f);
        g.drawText(tr, s, 0, 0, color, false);
        m.pop();
    }
}
