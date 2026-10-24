package pl.gemshop;

public final class Theme {
    public final int bg, card, cardHover, text, sub, border, track, hudBg, solid, glow;
    public final String name;

    private Theme(String name, int bg, int card, int cardHover, int text, int sub, int border, int track, int hudBg, int solid, int glow) {
        this.name = name; this.bg = bg; this.card = card; this.cardHover = cardHover; this.text = text; this.sub = sub;
        this.border = border; this.track = track; this.hudBg = hudBg; this.solid = solid; this.glow = glow;
    }

    public static final Theme[] ALL = {
        new Theme("Midnight", 0xF20B0C12, 0xFF15161E, 0xFF1F202B, 0xFFF3F4F8, 0xFF8B8E9C, 0x26FFFFFF, 0xFF262734, 0xE60B0C12, 0xFF15161E, 0xFFFFFF),
        new Theme("Glass",    0xB82B1B2B, 0x40FFFFFF, 0x58FFFFFF, 0xFFFFFFFF, 0xFFD9C9D5, 0x30FFFFFF, 0x40FFFFFF, 0xB0281C26, 0xFF3A2A3A, 0xFFFFFF),
        new Theme("Frost",    0xF2F1F4FA, 0xFFFFFFFF, 0xFFE9ECF4, 0xFF15161E, 0xFF6A6F80, 0x26000000, 0xFFDDE1EB, 0xF5F4F6FA, 0xFFFFFFFF, 0x6A6F80)
    };

    public static final int[] ACCENTS = {0xFF4AA8FF, 0xFFFFB938, 0xFF34D399, 0xFFFF5B7F, 0xFF8B7CFF};

    public static Theme get(int i) { return ALL[Math.floorMod(i, ALL.length)]; }
    public static int accent(int i) { return ACCENTS[Math.floorMod(i, ACCENTS.length)]; }
}
