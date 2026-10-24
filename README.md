# Gem Shop (Fabric 1.21.4)

Mod kliencki Fabric z GUI w stylu "Gem Shop".

## Klawisze
- **N** - otwiera/zamyka GUI (animowane)
- **M** - patrzysz na gracza -> otwiera jego profil

## Funkcje
- Lista graczy z TAB-a serwera (czytana w tle, TAB nie jest otwierany), graczy ze swiata i druzyn scoreboardu
- Filtr (zawęża wyniki), przycisk Search -> profil gracza
- "Brak wyciekow" u wszystkich; tylko `_L3kcj4_` ma wpisy (IP **fikcyjne i stale**, zakresy RFC 5737) - edycja w `Fake.java`
- Przycisk Bomba: 60 s cooldown, wszystkie Bomba szare z timerem, czerwona kreska (tracer) do celu, etykieta, HUD z timerem
- ESP: bialy box, czerwony gdy patrzysz na gracza
- Visuals: kolor akcentu, motyw (Midnight/Glass/Frost), zamykanie (Rozpad/Fade), pozycja HUD (rog / reczne przeciaganie), styl Pelny/Kompakt
- Ustawienia: licencja "Wlasciciel / na zawsze", zmiana klawiszy, animacje, ruch 3D + poswiata, rozmycie tla

Konfiguracja: `config/gemshop.json`.

## Budowanie
Java 21 + Gradle 8.12+:

    gradle build        # jar w build/libs/gemshop-1.0.0.jar
    gradle runClient    # test w grze

Albo po prostu wrzuc repo na GitHuba i uzyj workflow `build.yml` (Actions -> Artifacts).
