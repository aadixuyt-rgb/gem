package pl.gemshop;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/**
 * Gem Shop GUI. Rysowanie w trybie "immediate": uklad w wspolrzednych logicznych panelu
 * (390x290), mapowanych recznie na ekran (bez macierzy - scissor dziala poprawnie).
 *
 * Czysto informacyjne GUI listy graczy - bez zadnego sledzenia, "wyciekow" danych
 * ani funkcji celujacych w konkretnych graczy.
 */
public class GemShopScreen extends Screen {
    static final int W = 390, H = 290;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    record Row(UUID id, String name, boolean inRange, double dist, SkinTextures skin) {}
    record Hit(float x0, float y0, float x1, float y1, Runnable run) {}

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ModConfig cfg = GemShopMod.cfg;
    private DrawContext g;
    private TextRenderer tr;
    private Theme th;
    private int accent;
    private float ox, oy, s = 1f, A = 1f, lmx, lmy, bgP = 1f, closeP;
    private long openStart = Util.getMeasuringTimeMs(), closeStart = -1;
    public int tab = 0;
    private UUID profileId;
    private String profileName = "";
    private String filter = "";
    private boolean filterFocus;
    private final float[] scroll = new float[4], maxScroll = new float[4];
    private List<Row> all = new ArrayList<>();
    private KeyBinding listen;
    private final List<Hit> hits = new ArrayList<>();
    private float[] clip;

    public GemShopScreen() { super(Text.literal("Gem Shop")); }

    public GemShopScreen(UUID id, String name) {
        this();
        this.profileId = id;
        this.profileName = name;
    }

    @Override protected void init() { rebuild(); }
    @Override public void tick() { rebuild(); }
    @Override public boolean shouldPause() { return false; }

    /** Nicki z listy graczy serwera (TAB - bez otwierania), swiata i druzyn scoreboardu. */
    private void rebuild() {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        Map<UUID, Row> map = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        String self = mc.player.getNameForScoreboard().toLowerCase(Locale.ROOT);
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            map.put(p.getUuid(), new Row(p.getUuid(), p.getNameForScoreboard(), true, p.distanceTo(mc.player), p.getSkinTextures()));
            names.add(p.getNameForScoreboard().toLowerCase(Locale.ROOT));
        }
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            UUID id = e.getProfile().getId();
            String n = e.getProfile().getName();
            if (id.equals(mc.player.getUuid()) || map.containsKey(id) || n == null) continue;
            if (n.equalsIgnoreCase(self)) continue;
            map.put(id, new Row(id, n, false, -1, e.getSkinTextures()));
            names.add(n.toLowerCase(Locale.ROOT));
        }
        try {
            for (Team t : mc.world.getScoreboard().getTeams()) {
                for (String n : t.getPlayerList()) {
                    if (n == null || !NAME.matcher(n).matches()) continue;
                    String low = n.toLowerCase(Locale.ROOT);
                    if (low.equals(self) || !names.add(low)) continue;
                    UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8));
                    map.put(id, new Row(id, n, false, -1, DefaultSkinHelper.getSkinTextures(id)));
                }
            }
        } catch (Exception ignored) { }
        List<Row> l = new ArrayList<>(map.values());
        l.sort(Comparator.comparing((Row r) -> !r.inRange()).thenComparingDouble(Row::dist).thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        all = l;
    }

    // ------------------------------------------------------------------ wspolne

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float easeOutBack(float t) {
        double c1 = 1.70158, c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    private int c(int col) { return Gfx.alpha(col, A); }
    private int X(float x) { return Math.round(ox + x * s); }
    private int Y(float y) { return Math.round(oy + y * s); }
    private int S(float v) { return Math.round(v * s); }

    private void fill(float x, float y, float w, float h, int col) { g.fill(X(x), Y(y), X(x + w), Y(y + h), c(col)); }
    private void rr(float x, float y, float w, float h, float r, int col) {
        Gfx.rr(g, X(x), Y(y), X(x + w) - X(x), Y(y + h) - Y(y), Math.max(1, S(r)), c(col));
    }
    private void circle(float cx, float cy, float r, int col) { Gfx.circle(g, X(cx), Y(cy), Math.max(1, S(r)), c(col)); }
    private void line(float x1, float y1, float x2, float y2, float w, int col) {
        Gfx.line(g, ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, Math.max(1f, w * s), c(col));
    }
    private void outlineRR(float x, float y, float w, float h, float r, int col) {
        int iw = (int) Math.ceil(w), ih = (int) Math.ceil(h);
        for (int[] ru : Gfx.outline(iw, ih, (int) r)) fill(x + ru[0], y + ru[1], ru[2] - ru[0], ru[3] - ru[1], col);
    }
    private void text(String str, float x, float y, int col, float sc, boolean bold) {
        Gfx.text(g, tr, str, X(x), Y(y), c(col), sc * s, bold);
    }
    private float tw(String str, float sc, boolean bold) { return Gfx.width(tr, str, bold) * sc; }
    private void ctext(String str, float cx, float y, int col, float sc, boolean bold) {
        text(str, cx - tw(str, sc, bold) / 2f, y, col, sc, bold);
    }

    private boolean hit(float x, float y, float w, float h, Runnable r) {
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (clip != null) {
            x0 = Math.max(x0, clip[0]); y0 = Math.max(y0, clip[1]);
            x1 = Math.min(x1, clip[2]); y1 = Math.min(y1, clip[3]);
            if (x0 >= x1 || y0 >= y1) return false;
        }
        if (r != null) hits.add(new Hit(x0, y0, x1, y1, r));
        return closeStart < 0 && lmx >= x0 && lmx < x1 && lmy >= y0 && lmy < y1;
    }

    private void card(float x, float y, float w, float h) { rr(x, y, w, h, 9, th.card); }
    private void title(String t, float x, float y) { text(t, x + 10, y + 9, th.text, 0.85f, true); }

    private void scissor(float x0, float y0, float x1, float y1) {
        g.enableScissor(X(x0), Y(y0), X(x1), Y(y1));
        clip = new float[]{x0, y0, x1, y1};
    }
    private void endScissor() { g.disableScissor(); clip = null; }

    private void magnifier(float cx, float cy, int col, int hole) {
        circle(cx - 0.5f, cy - 0.5f, 3.4f, col);
        circle(cx - 0.5f, cy - 0.5f, 2.1f, hole);
        line(cx + 2f, cy + 2f, cx + 4.6f, cy + 4.6f, 1.3f, col);
    }

    private void gem(float x, float y, float sz, int col) {
        int rows = (int) sz;
        int light = Gfx.mix(col, 0xFFFFFFFF, 0.38f), dark = Gfx.mix(col, 0xFF000000, 0.12f);
        for (int r = 0; r < rows; r++) {
            float t = r / sz, half;
            if (t < 0.33f) half = sz * (0.30f + 0.20f * (t / 0.33f));
            else half = sz * 0.5f * (1f - (t - 0.33f) / 0.67f);
            fill(x + sz / 2f - half, y + r, half * 2f, 1f, t < 0.33f ? light : (t < 0.6f ? col : dark));
        }
    }

    private void toggle(float x, float y, boolean on, Runnable r) {
        hit(x, y, 26, 14, r);
        rr(x, y, 26, 14, 7, on ? accent : th.track);
        circle(x + (on ? 19 : 7), y + 7, 5, 0xFFFFFFFF);
    }

    private void seg(float x, float y, String[] labels, int sel, float iw, IntConsumer on) {
        rr(x, y, iw * labels.length + 4, 16, 6, th.track);
        for (int i = 0; i < labels.length; i++) {
            final int ii = i;
            float ix = x + 2 + i * iw;
            hit(ix, y + 2, iw, 12, () -> on.accept(ii));
            if (i == sel) rr(ix, y + 2, iw, 12, 5, accent);
            ctext(labels[i], ix + iw / 2f, y + 4.2f, i == sel ? 0xFFFFFFFF : th.sub, 0.7f, true);
        }
    }

    private void head(Row r, float x, float y, float size) {
        int sx = X(x), sy = Y(y), sz = Math.max(4, S(size));
        PlayerSkinDrawer.draw(g, r.skin(), sx, sy, sz);
        if (A < 0.98f) g.fill(sx, sy, sx + sz, sy + sz, Gfx.alpha(th.solid, 1f - A));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        if (cfg.blur) super.renderBackground(ctx, mx, my, delta);
        else ctx.fill(0, 0, width, height, ((int) (0x78 * bgP)) << 24);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        g = ctx; tr = textRenderer; th = Theme.get(cfg.theme); accent = Theme.accent(cfg.accent);
        long now = Util.getMeasuringTimeMs();
        boolean closing = closeStart >= 0, fade = cfg.closeFade;
        float p;
        if (!closing) { p = cfg.animations ? clamp((now - openStart) / 280f) : 1f; closeP = 0; }
        else {
            float q = cfg.animations ? (now - closeStart) / (fade ? 200f : 520f) : 1f;
            if (q >= 1f) { super.close(); return; }
            closeP = q; p = 1f - q;
        }
        bgP = p;
        super.render(ctx, mx, my, delta);

        boolean shatter = closing && !fade;
        float ease = closing ? p : easeOutBack(p);
        float anim = !cfg.animations ? 1f : closing ? (fade ? 0.95f + 0.05f * p : 1f) : 0.86f + 0.14f * ease;
        A = shatter ? Math.max(0f, 1f - closeP * 2.5f) : closing ? p : Math.min(1f, p * 1.6f);

        float fit = Math.min(1f, Math.min((width - 12f) / W, (height - 12f) / H));
        s = fit * anim;
        float cx = width / 2f, cy = height / 2f, px = 0, py = 0;
        if (cfg.parallax && !closing) { px = -(mx - cx) / cx * 3f; py = -(my - cy) / cy * 3f; }
        ox = cx + px - W * s / 2f;
        oy = cy + py - H * s / 2f;
        lmx = (mx - ox) / s;
        lmy = (my - oy) / s;
        hits.clear();
        clip = null;

        if (shatter) drawShatter();
        else {
            rr(-3, -3, W + 6, H + 6, 17, 0x26000000);
            rr(-1, -1, W + 2, H + 2, 15, 0x30000000);
            rr(0, 0, W, H, 14, th.bg);
        }
        if (A > 0.02f) {
            glint();
            drawHeader();
            if (tab == 0) drawPlayers();
            else if (tab == 1) drawVisuals();
            else drawSettings();
            drawNav();
        }
        if (!shatter) glowBorder();
    }

    private void drawShatter() {
        int cols = 10, rows = 8;
        float tw_ = W / (float) cols, th_ = H / (float) rows;
        float saved = A;
        A = 1f;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                Random rn = new Random(i * 131L + j * 71L);
                float vx = (rn.nextFloat() - 0.5f) * 2f, vy = (rn.nextFloat() - 0.65f) * 1.4f;
                float delay = rn.nextFloat() * 0.35f;
                float t = Math.max(0f, Math.min(1f, (closeP - delay) / (1f - delay)));
                float x = i * tw_ + vx * 160f * t, y = j * th_ + vy * 110f * t + 120f * t * t;
                float sz = 1f - 0.6f * t;
                rr(x + tw_ * (1 - sz) / 2f, y + th_ * (1 - sz) / 2f, tw_ * sz, th_ * sz, 2, Gfx.alpha(th.solid, (1f - t) * 0.96f));
            }
        }
        A = saved;
    }

    private void glint() {
        if (!cfg.parallax || closeStart >= 0) return;
        float gx = Math.max(0, Math.min(W, lmx)), gy = Math.max(0, Math.min(H, lmy));
        g.enableScissor(X(0), Y(0), X(W), Y(H));
        for (int r = 100; r >= 20; r -= 16) circle(gx, gy, r, 0x07FFFFFF);
        g.disableScissor();
    }

    private void glowBorder() {
        boolean glow = cfg.parallax && closeStart < 0;
        int gc = th.glow & 0xFFFFFF;
        for (int[] r : Gfx.outline(W, H, 14)) {
            boolean horiz = (r[2] - r[0]) >= (r[3] - r[1]);
            int len = horiz ? r[2] - r[0] : r[3] - r[1];
            for (int o = 0; o < len; o += 8) {
                int e = Math.min(len, o + 8);
                int x0 = horiz ? r[0] + o : r[0], x1 = horiz ? r[0] + e : r[2];
                int y0 = horiz ? r[1] : r[1] + o, y1 = horiz ? r[3] : r[1] + e;
                float f = glow ? Math.max(0f, 1f - (float) Math.hypot(lmx - (x0 + x1) / 2f, lmy - (y0 + y1) / 2f) / 120f) : 0f;
                f *= f;
                int a = (int) (0x2E + f * 0xB0);
                fill(x0, y0, x1 - x0, y1 - y0, (a << 24) | gc);
            }
        }
        if (!glow) return;
        for (int k = 1; k <= 3; k++) {
            for (int[] r : Gfx.outline(W + 2 * k, H + 2 * k, 14 + k)) {
                boolean horiz = (r[2] - r[0]) >= (r[3] - r[1]);
                int len = horiz ? r[2] - r[0] : r[3] - r[1];
                for (int o = 0; o < len; o += 12) {
                    int e = Math.min(len, o + 12);
                    int x0 = horiz ? r[0] + o : r[0], x1 = horiz ? r[0] + e : r[2];
                    int y0 = horiz ? r[1] : r[1] + o, y1 = horiz ? r[3] : r[1] + e;
                    float f = Math.max(0f, 1f - (float) Math.hypot(lmx - (x0 + x1 - 2f * k) / 2f, lmy - (y0 + y1 - 2f * k) / 2f) / 120f);
                    int a = (int) (f * f * 0x70 / k);
                    if (a < 3) continue;
                    fill(x0 - k, y0 - k, x1 - x0, y1 - y0, (a << 24) | gc);
                }
            }
        }
    }

    // ------------------------------------------------------------------ naglowek / nawigacja

    private void drawHeader() {
        gem(12, 8, 18, accent);
        text("Gem Shop", 36, 8, th.text, 1f, true);
        String sub = tab == 0 ? (profileId != null ? "Gracz · profil" : "Gracz") : tab == 1 ? "Visuals" : "Ustawienia";
        text(sub, 36, 20, th.sub, 0.72f, false);
        float cx = W - 30, cy = 8;
        boolean hv = hit(cx, cy, 20, 20, this::close);
        rr(cx, cy, 20, 20, 7, hv ? th.cardHover : th.card);
        line(cx + 7, cy + 7, cx + 13, cy + 13, 1.2f, th.sub);
        line(cx + 13, cy + 7, cx + 7, cy + 13, 1.2f, th.sub);
        fill(0, 35, W, 1, th.border);
    }

    private void drawNav() {
        float nx = (W - 122) / 2f, ny = H - 40;
        rr(nx, ny, 122, 34, 10, th.card);
        String[] names = {"Gracz", "Visuals", "Ustawienia"};
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            float x = nx + 3 + i * 39.3f;
            boolean sel = tab == i;
            boolean hv = hit(x, ny + 3, 39, 28, () -> { if (ti == 0 && tab == 0) { profileId = null; scroll[3] = 0; } tab = ti; });
            if (sel) rr(x, ny + 3, 39, 28, 8, Gfx.alpha(accent, 0.22f));
            else if (hv) rr(x, ny + 3, 39, 28, 8, th.cardHover);
            int col = sel ? accent : th.sub;
            float icx = x + 19.5f, icy = ny + 12;
            if (i == 0) {
                circle(icx, icy - 2.5f, 3.2f, col);
                rr(icx - 5, icy + 1.5f, 10, 5, 2.5f, col);
            } else if (i == 1) {
                line(icx - 6, icy, icx, icy - 4, 1.2f, col); line(icx, icy - 4, icx + 6, icy, 1.2f, col);
                line(icx + 6, icy, icx, icy + 4, 1.2f, col); line(icx, icy + 4, icx - 6, icy, 1.2f, col);
                circle(icx, icy, 1.8f, col);
            } else {
                line(icx - 6, icy - 3, icx + 6, icy - 3, 1.2f, col); line(icx - 6, icy + 3, icx + 6, icy + 3, 1.2f, col);
                circle(icx - 2, icy - 3, 2, col); circle(icx + 2, icy + 3, 2, col);
            }
            ctext(names[i], icx, ny + 20.5f, col, 0.6f, sel);
        }
    }

    // ------------------------------------------------------------------ zakladka: Gracz

    private void openProfile(Row r) { profileId = r.id(); profileName = r.name(); scroll[3] = 0; }

    private String distText(Row r) { return r.inRange() ? Math.round(r.dist()) + " m · w zasięgu" : "nie w zasięgu"; }

    private void drawPlayers() {
        if (profileId != null) { drawProfile(); return; }
        hit(12, 42, 300, 20, () -> filterFocus = true);
        rr(12, 42, 300, 20, 8, th.card);
        if (filterFocus) outlineRR(12, 42, 300, 20, 8, accent);
        magnifier(23, 52, th.sub, th.solid);
        if (filter.isEmpty() && !filterFocus) text("Filtruj graczy w zasięgu…", 34, 48.2f, th.sub, 0.85f, false);
        else {
            String shown = tr.trimToWidth(filter, 230);
            text(shown, 34, 48.2f, th.text, 0.85f, false);
            if (filterFocus && (Util.getMeasuringTimeMs() / 500) % 2 == 0)
                fill(34 + tw(shown, 0.85f, false) + 1, 47, 1, 9, th.text);
        }
        String f = filter.toLowerCase(Locale.ROOT).trim();
        List<Row> list = new ArrayList<>();
        for (Row r : all) if (f.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(f)) list.add(r);
        String cnt = list.size() + " / " + all.size() + " graczy";
        text(cnt, W - 12 - tw(cnt, 0.75f, false), 48.5f, th.sub, 0.75f, false);

        float y0 = 68, y1 = H - 44, viewH = y1 - y0;
        int n = list.size(), rowsN = (n + 1) / 2;
        float contentH = rowsN * 42f - 6f;
        maxScroll[0] = Math.max(0, contentH - viewH);
        scroll[0] = Math.max(0, Math.min(maxScroll[0], scroll[0]));
        scissor(0, y0, W, y1);
        float cw = (W - 24 - 8) / 2f;
        if (n == 0) ctext("Brak graczy", W / 2f, y0 + 40, th.sub, 0.9f, false);
        for (int i = 0; i < n; i++) {
            float x = 12 + (i % 2) * (cw + 8), y = y0 - scroll[0] + (i / 2) * 42f;
            if (y + 36 < y0 || y > y1) continue;
            playerCard(list.get(i), x, y, cw);
        }
        endScissor();
        if (maxScroll[0] > 0) {
            float th_ = Math.max(16, viewH * viewH / contentH);
            rr(W - 7, y0, 2, viewH, 1, th.track);
            rr(W - 7, y0 + (viewH - th_) * scroll[0] / maxScroll[0], 2, th_, 1, th.sub);
        }
    }

    private void playerCard(Row r, float x, float y, float cw) {
        boolean hv = hit(x, y, cw, 36, null);
        rr(x, y, cw, 36, 8, hv ? th.cardHover : th.card);
        head(r, x + 8, y + 7, 22);
        text(tr.trimToWidth(r.name(), 78), x + 36, y + 9, th.text, 0.9f, true);
        text(distText(r), x + 36, y + 21, th.sub, 0.7f, false);
        float bw = 48, bh = 18, bx = x + cw - bw - 8, by = y + 9;
        boolean hb = hit(bx, by, bw, bh, () -> openProfile(r));
        int col = hb ? Gfx.mix(accent, 0xFFFFFFFF, 0.15f) : accent;
        rr(bx, by, bw, bh, 6, col);
        magnifier(bx + 10, by + 9, 0xFFFFFFFF, col);
        text("Search", bx + 17, by + 5.5f, 0xFFFFFFFF, 0.75f, true);
    }

    /** Ping gracza (ms) z listy TAB, jesli serwer go podaje. -1 = nieznany. */
    private int pingFor(UUID id) {
        if (mc.getNetworkHandler() == null) return -1;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(id);
        return e != null ? e.getLatency() : -1;
    }

    private void drawProfile() {
        Row r = null;
        for (Row x : all) if (x.id().equals(profileId)) { r = x; break; }
        if (r == null) r = new Row(profileId, profileName, false, -1, DefaultSkinHelper.getSkinTextures(profileId));
        final Row row = r;
        int ping = pingFor(row.id());

        float contentH = 42 + 106 + 56 + 8 - 36;
        float viewH = H - 44 - 36;
        maxScroll[3] = Math.max(0, contentH - viewH);
        scroll[3] = Math.max(0, Math.min(maxScroll[3], scroll[3]));
        float top = 42 - scroll[3];
        scissor(0, 36, W, H - 44);

        boolean hb = hit(12, top, 60, 18, () -> { profileId = null; scroll[3] = 0; });
        rr(12, top, 60, 18, 7, hb ? th.cardHover : th.card);
        text("‹ Gracze", 20, top + 5.5f, th.text, 0.8f, true);

        head(row, W / 2f - 20, top + 26, 40);
        ctext(row.name(), W / 2f, top + 70, th.text, 1.15f, true);
        text(distText(row), W / 2f - tw(distText(row), 0.78f, false) / 2f, top + 86, th.sub, 0.78f, false);

        float cy = top + 106;
        card(12, cy, W - 24, 56);
        text("Status", 22, cy + 9, th.text, 0.85f, true);
        boolean online = row.inRange() || all.stream().anyMatch(x -> x.id().equals(row.id()));
        circle(26, cy + 29, 3, online ? 0xFF34D399 : 0xFF8B8E9C);
        text(online ? "Na serwerze" : "Poza serwerem", 32, cy + 25.5f, th.text, 0.8f, false);
        String pingText = ping >= 0 ? ping + " ms ping" : "Ping nieznany";
        text(pingText, W - 22 - tw(pingText, 0.75f, false), cy + 26, th.sub, 0.75f, false);
        text("Gra kliencka tylko odczytuje publiczna liste graczy serwera.", 22, cy + 40, th.sub, 0.6f, false);
        endScissor();
    }

    // ------------------------------------------------------------------ zakladka: Visuals

    private void drawVisuals() {
        float top = 42 - scroll[1];
        maxScroll[1] = 0;
        scissor(0, 36, W, H - 44);
        float lx = 12, rx = 199, cw = 179, y = top;

        card(lx, y, cw, 50);
        title("Kolor akcentu", lx, y);
        for (int i = 0; i < 5; i++) {
            final int ii = i;
            float sx = lx + 10 + i * 25, sy = y + 24;
            hit(sx, sy, 18, 18, () -> { cfg.accent = ii; cfg.save(); });
            rr(sx, sy, 18, 18, 6, Theme.accent(i));
            if (cfg.accent == i) outlineRR(sx - 2, sy - 2, 22, 22, 8, th.text);
        }

        y += 56;
        card(lx, y, cw, 76);
        title("Motyw GUI", lx, y);
        int[] prev = {0xFF0B0C12, 0xFF3A2A3A, 0xFFF1F4FA};
        float t3 = (cw - 20 - 12) / 3f;
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            float tx = lx + 10 + i * (t3 + 6), ty = y + 24;
            hit(tx, ty, t3, 44, () -> { cfg.theme = ii; cfg.save(); });
            rr(tx, ty, t3, 44, 7, prev[i]);
            rr(tx + 6, ty + 8, 10, 4, 2, accent);
            rr(tx + 6, ty + 16, t3 - 12, 3, 1.5f, i == 2 ? 0x22000000 : 0x33FFFFFF);
            ctext(Theme.ALL[i].name, tx + t3 / 2f, ty + 31, i == 2 ? 0xFF15161E : 0xFFFFFFFF, 0.62f, true);
            if (cfg.theme == i) { outlineRR(tx - 1, ty - 1, t3 + 2, 46, 8, accent); outlineRR(tx, ty, t3, 44, 7, accent); }
        }

        y += 82;
        card(lx, y, cw, 32);
        text("Zamykanie", lx + 10, y + 11.5f, th.text, 0.8f, true);
        seg(lx + cw - 10 - 84, y + 8, new String[]{"Rozpad", "Fade"}, cfg.closeFade ? 1 : 0, 40, i -> { cfg.closeFade = i == 1; cfg.save(); });

        // prawa kolumna
        y = top;
        card(rx, y, cw, 148);
        title("O modzie", rx, y);
        List<OrderedText> lines = tr.wrapLines(Text.literal(
                "Gem Shop pokazuje liste graczy na serwerze i ich dystans. Nie zbiera ani nie wyswietla zadnych danych o graczach poza tym, co podaje sama gra."),
                (int) ((cw - 20) / 0.65f));
        for (int i = 0; i < lines.size(); i++)
            Gfx.textOrdered(g, tr, lines.get(i), X(rx + 10), Y(y + 26 + i * 8.5f), c(th.sub), 0.65f * s);
        endScissor();
    }

    // ------------------------------------------------------------------ zakladka: Ustawienia

    private String keyName(KeyBinding kb) {
        if (listen == kb) return "…";
        String n = kb.getBoundKeyLocalizedText().getString();
        return n.length() == 1 ? n.toUpperCase(Locale.ROOT) : n;
    }

    private void drawSettings() {
        float top = 42 - scroll[2];
        maxScroll[2] = 0;
        scissor(0, 36, W, H - 44);
        float cw = 179;
        card(12, top, cw, 64);
        text("Licencja", 22, top + 9, th.sub, 0.7f, false);
        text("Właściciel", 22, top + 19, th.text, 1.5f, true);
        rr(22, top + 44, cw - 20, 3, 1.5f, th.track);
        rr(22, top + 44, cw - 20, 3, 1.5f, accent);
        text("Czas: na zawsze", 22, top + 52, th.sub, 0.65f, false);

        card(199, top, cw, 64);
        text("Status API", 209, top + 9, th.sub, 0.7f, false);
        circle(212.5f, top + 25, 2.5f, 0xFF34D399);
        text("Połączono", 219, top + 21, th.text, 0.9f, true);
        List<OrderedText> lines = tr.wrapLines(Text.literal("Dane konta i licencji przyjdą z API po autoryzacji przez Discord."), (int) ((cw - 20) / 0.65f));
        for (int i = 0; i < lines.size(); i++)
            Gfx.textOrdered(g, tr, lines.get(i), X(209), Y(top + 34 + i * 7.2f), c(th.sub), 0.65f * s);

        float cy = top + 72;
        card(12, cy, W - 24, 130);
        String[] labels = {"Klawisz otwierania", "Szybkie szukanie (celownik)", "Animacje otwierania i zamykania", "Ruch 3D i błysk za myszką", "Rozmycie tła (shader)"};
        for (int i = 0; i < 5; i++) {
            float ry = cy + i * 26;
            if (i > 0) fill(22, ry, W - 44, 1, th.border);
            text(labels[i], 22, ry + 9.5f, th.text, 0.8f, true);
            if (i < 2) {
                final KeyBinding kb = i == 0 ? GemShopMod.openKey : GemShopMod.lookKey;
                String kn = keyName(kb);
                float bw = tw(kn, 0.75f, true) + 14, bx = W - 22 - bw;
                hit(bx, ry + 6, bw, 14, () -> listen = kb);
                rr(bx, ry + 6, bw, 14, 5, listen == kb ? Gfx.alpha(accent, 0.3f) : th.track);
                ctext(kn, bx + bw / 2f, ry + 9.5f, listen == kb ? accent : th.text, 0.75f, true);
            } else {
                final int ii = i;
                boolean on = i == 2 ? cfg.animations : i == 3 ? cfg.parallax : cfg.blur;
                toggle(W - 22 - 26, ry + 6, on, () -> {
                    if (ii == 2) cfg.animations = !cfg.animations;
                    else if (ii == 3) cfg.parallax = !cfg.parallax;
                    else cfg.blur = !cfg.blur;
                    cfg.save();
                });
            }
        }
        endScissor();
    }

    // ------------------------------------------------------------------ input

    @Override
    public void close() {
        if (closeStart >= 0) return;
        if (!cfg.animations) { super.close(); return; }
        closeStart = Util.getMeasuringTimeMs();
    }

    private void updateMouse(double mx, double my) { lmx = (float) ((mx - ox) / s); lmy = (float) ((my - oy) / s); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closeStart >= 0) return true;
        if (button != 0) return super.mouseClicked(mx, my, button);
        updateMouse(mx, my);
        filterFocus = false;
        listen = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (lmx >= h.x0() && lmx < h.x1() && lmy >= h.y0() && lmy < h.y1()) { h.run().run(); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int idx = tab == 0 ? (profileId != null ? 3 : 0) : tab == 1 ? 1 : 2;
        scroll[idx] = Math.max(0, Math.min(maxScroll[idx], scroll[idx] - (float) vAmount * 14f));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listen != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) listen.setBoundKey(InputUtil.UNKNOWN_KEY);
            else listen.setBoundKey(InputUtil.fromKeyCode(key, scan));
            KeyBinding.updateKeysByCode();
            mc.options.write();
            listen = null;
            return true;
        }
        if (filterFocus && tab == 0 && profileId == null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!filter.isEmpty()) filter = filter.substring(0, filter.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { filterFocus = false; return true; }
            if (Screen.isPaste(key)) {
                String cl = mc.keyboard.getClipboard();
                if (cl != null) { StringBuilder sb = new StringBuilder(filter); for (char ch : cl.toCharArray()) if (SharedConstants.isValidChar(ch) && sb.length() < 24) sb.append(ch); filter = sb.toString(); }
                return true;
            }
            return true;
        }
        if (GemShopMod.openKey.matchesKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (filterFocus && tab == 0 && profileId == null && SharedConstants.isValidChar(chr)) {
            if (filter.length() < 24) filter += chr;
            return true;
        }
        return super.charTyped(chr, mods);
    }
}
