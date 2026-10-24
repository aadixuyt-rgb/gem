package pl.gemshop;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.UUID;

/**
 * ESP / tracer / etykieta / HUD cooldownu. Wszystko rysowane w 2D (HUD) na podstawie
 * macierzy przechwyconych z renderu swiata - dzieki temu dziala przez sciany.
 */
public final class Esp {
    private static final float NEAR = 0.05f;
    private static final Matrix4f VP = new Matrix4f();
    private static Vec3d cam = Vec3d.ZERO;
    private static boolean valid;
    private static float partial;

    private Esp() {}

    public static void capture(WorldRenderContext ctx) {
        Camera c = ctx.camera();
        cam = c.getPos();
        Matrix4f view = new Matrix4f().rotation(c.getRotation().conjugate(new Quaternionf()));
        new Matrix4f(ctx.projectionMatrix()).mul(view, VP);
        partial = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        valid = true;
    }

    public static AbstractClientPlayerEntity lookedAt(float pt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        Vec3d start = mc.player.getCameraPosVec(pt);
        Vec3d end = start.add(mc.player.getRotationVec(pt).multiply(160));
        AbstractClientPlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            Optional<Vec3d> hit = p.getBoundingBox().expand(0.15).raycast(start, end);
            if (hit.isPresent()) {
                double d = start.squaredDistanceTo(hit.get());
                if (d < bd) { bd = d; best = p; }
            }
        }
        return best;
    }

    public static int[] hudRect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig cfg = GemShopMod.cfg;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        boolean cp = cfg.hudCompact;
        int w = cp ? 92 : 132, h = cp ? 16 : 28, x, y;
        if (cfg.hudManual) { x = cfg.hudX; y = cfg.hudY; }
        else switch (cfg.hudCorner) {
            case 0 -> { x = 16; y = 16; }
            case 1 -> { x = sw - w - 16; y = 16; }
            case 2 -> { x = 16; y = sh - h - 16; }
            default -> { x = sw - w - 16; y = sh - h - 16; }
        }
        return new int[]{x, y, w, h};
    }

    public static void renderHud(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        ModConfig cfg = GemShopMod.cfg;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        if (valid && (cfg.esp || Mark.active())) {
            AbstractClientPlayerEntity looked = lookedAt(partial);
            Vector4f[] c = new Vector4f[8];
            for (int i = 0; i < 8; i++) c[i] = new Vector4f();
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || p.isSpectator()) continue;
                boolean marked = Mark.active() && p.getUuid().equals(Mark.id);
                if (!cfg.esp && !marked) continue;

                double dx = MathHelper.lerp(partial, p.prevX, p.getX()) - p.getX();
                double dy = MathHelper.lerp(partial, p.prevY, p.getY()) - p.getY();
                double dz = MathHelper.lerp(partial, p.prevZ, p.getZ()) - p.getZ();
                Box b = p.getBoundingBox().offset(dx, dy, dz);
                double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
                for (int i = 0; i < 8; i++) {
                    c[i].set((float) (xs[i & 1] - cam.x), (float) (ys[(i >> 1) & 1] - cam.y), (float) (zs[(i >> 2) & 1] - cam.z), 1f);
                    VP.transform(c[i]);
                }
                boolean isLooked = p == looked;
                int col = isLooked ? 0xFFFF3B3B : marked ? 0xFFFF5B7F : 0xB8FFFFFF;
                float lw = (isLooked || marked) ? 1.1f : 0.8f;
                for (int i = 0; i < 8; i++)
                    for (int bit : new int[]{1, 2, 4})
                        if ((i & bit) == 0) clipLine(g, c[i], c[i | bit], sw, sh, lw, col);

                if (marked) {
                    boolean all = true;
                    float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
                    for (int i = 0; i < 8; i++) {
                        if (c[i].w < NEAR) { all = false; break; }
                        float x = (c[i].x / c[i].w * 0.5f + 0.5f) * sw;
                        float y = (1f - (c[i].y / c[i].w * 0.5f + 0.5f)) * sh;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                    if (all) {
                        g.fill((int) minX, (int) minY, (int) maxX, (int) maxY, 0x28FF5B7F);
                        float len = Math.max(3f, Math.min(8f, (maxX - minX) / 3f));
                        int bc = 0xFFFF5B7F;
                        Gfx.line(g, minX, minY, minX + len, minY, 1.4f, bc); Gfx.line(g, minX, minY, minX, minY + len, 1.4f, bc);
                        Gfx.line(g, maxX, minY, maxX - len, minY, 1.4f, bc); Gfx.line(g, maxX, minY, maxX, minY + len, 1.4f, bc);
                        Gfx.line(g, minX, maxY, minX + len, maxY, 1.4f, bc); Gfx.line(g, minX, maxY, minX, maxY - len, 1.4f, bc);
                        Gfx.line(g, maxX, maxY, maxX - len, maxY, 1.4f, bc); Gfx.line(g, maxX, maxY, maxX, maxY - len, 1.4f, bc);
                        drawLabel(g, mc, p, (minX + maxX) / 2f, minY, sw, sh);
                    }
                    if (cfg.tracer) {
                        Vector4f ctr = new Vector4f((float) ((b.minX + b.maxX) / 2 - cam.x), (float) ((b.minY + b.maxY) / 2 - cam.y), (float) ((b.minZ + b.maxZ) / 2 - cam.z), 1f);
                        VP.transform(ctr);
                        float x1 = sw / 2f, y1 = sh / 2f, x2, y2;
                        if (ctr.w >= NEAR) {
                            x2 = (ctr.x / ctr.w * 0.5f + 0.5f) * sw;
                            y2 = (1f - (ctr.y / ctr.w * 0.5f + 0.5f)) * sh;
                        } else {
                            float ddx = ctr.x, ddy = -ctr.y, l = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                            if (l < 1e-4f) { ddx = 0; ddy = 1; l = 1; }
                            x2 = x1 + ddx / l * Math.max(sw, sh);
                            y2 = y1 + ddy / l * Math.max(sw, sh);
                        }
                        Gfx.line(g, x1, y1, x2, y2, 1.2f, 0xFFFF3B4E);
                    }
                }
            }
        }

        Screen cur = mc.currentScreen;
        boolean skip = cur instanceof GemShopScreen gs && gs.tab == 1;
        if (Mark.active() && !skip) drawHud(g, mc, Mark.id, Mark.name, Mark.remaining(), 1f);
    }

    private static void clipLine(DrawContext g, Vector4f a, Vector4f b, int sw, int sh, float w, int col) {
        float ax = a.x, ay = a.y, aw = a.w, bx = b.x, by = b.y, bw = b.w;
        if (aw < NEAR && bw < NEAR) return;
        if (aw < NEAR) {
            float t = (NEAR - aw) / (bw - aw);
            ax += (bx - ax) * t; ay += (by - ay) * t; aw = NEAR;
        } else if (bw < NEAR) {
            float t = (NEAR - bw) / (aw - bw);
            bx += (ax - bx) * t; by += (ay - by) * t; bw = NEAR;
        }
        Gfx.line(g,
            (ax / aw * 0.5f + 0.5f) * sw, (1f - (ay / aw * 0.5f + 0.5f)) * sh,
            (bx / bw * 0.5f + 0.5f) * sw, (1f - (by / bw * 0.5f + 0.5f)) * sh, w, col);
    }

    private static void drawLabel(DrawContext g, MinecraftClient mc, AbstractClientPlayerEntity p, float cx, float top, int sw, int sh) {
        int lw = 124, lh = 30;
        int x = (int) MathHelper.clamp(cx - lw / 2f, 2, sw - lw - 2);
        int y = (int) MathHelper.clamp(top - lh - 4, 2, sh - lh - 2);
        Gfx.rr(g, x, y, lw, lh, 5, 0xE60B0C12);
        Gfx.outlineRR(g, x, y, lw, lh, 5, 0xFFFF3B4E);
        Gfx.text(g, mc.textRenderer, p.getNameForScoreboard(), x + 6, y + 4, 0xFFFFFFFF, 0.9f, true);
        String hp = "\u2665 " + Math.round(p.getHealth());
        Gfx.text(g, mc.textRenderer, hp, x + lw - 6 - Gfx.width(mc.textRenderer, hp, true) * 0.85f, y + 4, 0xFFFF4D5E, 0.85f, true);
        Gfx.text(g, mc.textRenderer, "Oznaczony \u00b7 " + Mark.fmt(Mark.remaining()), x + 6, y + 15, 0xFFFF5B7F, 0.8f, false);
        g.fill(x + 6, y + 25, x + lw - 6, y + 27, 0x33FFFFFF);
        g.fill(x + 6, y + 25, x + 6 + (int) ((lw - 12) * Mark.remaining() / (float) Mark.DURATION), y + 27, 0xFFFF3B4E);
    }

    /** Powiadomienie cooldownu (rog ekranu). a = mnoznik przezroczystosci. */
    public static void drawHud(DrawContext g, MinecraftClient mc, UUID id, String name, long remMs, float a) {
        ModConfig cfg = GemShopMod.cfg;
        Theme th = Theme.get(cfg.theme);
        int accent = Theme.accent(cfg.accent);
        int[] r = hudRect();
        int x = r[0], y = r[1], w = r[2], h = r[3];
        float frac = Math.max(0f, Math.min(1f, remMs / (float) Mark.DURATION));
        Gfx.rr(g, x, y, w, h, cfg.hudCompact ? 6 : 9, Gfx.alpha(th.hudBg, a));
        Gfx.outlineRR(g, x, y, w, h, cfg.hudCompact ? 6 : 9, Gfx.alpha(th.border, a));
        String time = Mark.fmt(remMs);
        if (!cfg.hudCompact) {
            PlayerListEntry e = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(id) : null;
            SkinTextures sk = e != null ? e.getSkinTextures() : DefaultSkinHelper.getSkinTextures(id);
            PlayerSkinDrawer.draw(g, sk, x + 6, y + 5, 18);
            Gfx.text(g, mc.textRenderer, mc.textRenderer.trimToWidth(name, 60), x + 30, y + 5, Gfx.alpha(th.text, a), 0.9f, true);
            Gfx.text(g, mc.textRenderer, time, x + w - 6 - Gfx.width(mc.textRenderer, time, true) * 0.85f, y + 5.5f, Gfx.alpha(0xFFFF4D5E, a), 0.85f, true);
            g.fill(x + 30, y + h - 8, x + w - 6, y + h - 6, Gfx.alpha(th.track, a));
            g.fill(x + 30, y + h - 8, x + 30 + (int) ((w - 36) * frac), y + h - 6, Gfx.alpha(accent, a));
        } else {
            Gfx.text(g, mc.textRenderer, mc.textRenderer.trimToWidth(name, 52), x + 6, y + 4, Gfx.alpha(th.text, a), 0.75f, true);
            Gfx.text(g, mc.textRenderer, time, x + w - 6 - Gfx.width(mc.textRenderer, time, true) * 0.75f, y + 4, Gfx.alpha(0xFFFF4D5E, a), 0.75f, true);
            g.fill(x + 4, y + h - 3, x + w - 4, y + h - 2, Gfx.alpha(th.track, a));
            g.fill(x + 4, y + h - 3, x + 4 + (int) ((w - 8) * frac), y + h - 2, Gfx.alpha(accent, a));
        }
    }
}
