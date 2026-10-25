# Gem Shop (Fabric 1.21.4)

Mod kliencki Fabric z animowanym GUI listy graczy.

## Klawisze
- **N** - otwiera/zamyka GUI (animowane)
- **M** - patrzysz bezposrednio na gracza (zwykly raycast, blokowany przez sciany) -> otwiera jego profil

## Funkcje
- Lista graczy z TAB-a serwera (czytana w tle, TAB nie jest otwierany), graczy ze swiata i druzyn scoreboardu
- Filtr (zawęża wyniki), przycisk Search -> profil gracza
- Profil gracza: glowka, nick, dystans ("w zasiegu" / "nie w zasiegu"), status online i ping z listy TAB
- Visuals: kolor akcentu, motyw (Midnight/Glass/Frost), styl zamykania (Rozpad/Fade)
- Ustawienia: zmiana klawiszy, animacje, ruch 3D + poswiata, rozmycie tla

Mod nie zbiera, nie generuje ani nie wyswietla zadnych danych o graczach poza tym,
co podaje sama gra (nick, odleglosc, ping) - bez "wyciekow", sledzenia ani akcji
wymierzonych w konkretnych graczy.

Konfiguracja: `config/gemshop.json`.

## Budowanie
Java 21 + Gradle 8.12+:

    gradle build        # jar w build/libs/gemshop-1.0.0.jar
    gradle runClient    # test w grze

Albo po prostu wrzuc repo na GitHuba i uzyj workflow `build.yml` (Actions -> Artifacts).
