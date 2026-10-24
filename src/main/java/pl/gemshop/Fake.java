package pl.gemshop;

/**
 * Dane "wyciekow" sa W CALOSCI FIKCYJNE.
 * Tylko gracz TARGET ma wpisy - wszyscy inni dostaja "Brak wyciekow".
 * IP sa STALE (po restarcie Minecrafta te same) i pochodza z zakresow zarezerwowanych
 * do dokumentacji (RFC 5737), wiec nie sa prawdziwymi adresami.
 */
public final class Fake {
    public static final String TARGET = "_L3kcj4_";

    /** nick == null -> pierwszy wyraz jest ZMIENNY (losowy nick gracza z serwera, losowany przy kazdym otwarciu profilu). */
    public record Entry(String nick, String when, String ip) {}

    // 2 wiersze ze zmiennym nickiem + 1 wiersz calkowicie niezmienny. Edytuj wedle uznania.
    public static final Entry[] ROWS = {
        new Entry(null,         "dzis",         "203.0.113.47"),
        new Entry(null,         "3 dni temu",   "198.51.100.162"),
        new Entry("L3kcj4_old", "miesiac temu", "192.0.2.88"),
    };

    private Fake() {}
}
