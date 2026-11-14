# Elaeste Structures 1.0.0 (projekt źródłowy)

Plugin dla Spigot 1.21.11 / Java 21.

## Co robi ta wersja
- Działa w świecie wskazanym przez `world-name` (domyślnie `World`).
- Traktuje prostokąt X/Z od -3800 do 3800 jako obszar Elaeste.
- W tym obszarze ma limity: 5 średnich zamków, 3 duże zamki, 8 wież, 10 ruin, 10 osad i 10 obozów.
- Poza tym obszarem może generować kolejne budowle bez limitu.
- Próbuje generować struktury, gdy gracz wchodzi do nowego chunka.
- Zapisuje pozycje w `plugins/ElaesteStructures/placements.yml`.
- Komendy: `/elaeste status`, `/elaeste reload`, `/elaeste generate`.

## Ważne ograniczenia pierwszej wersji
To jest pierwsza wersja kodu do testów, a nie zweryfikowany gotowy plugin produkcyjny:
- 46 to maksymalna liczba struktur na obszarze Elaeste, nie gwarancja, że wszystkie pojawią się bez eksplorowania odpowiedniej liczby chunków.
- Budowle są proceduralne i różnią się losowymi detalami; nie są 46 ręcznie zaprojektowanymi, unikalnymi schematami.
- Plugin nie chroni budowli graczy, zgodnie z ustaleniem.
- Generowanie odbywa się w istniejącym terenie, więc przed użyciem na głównym świecie trzeba wykonać kopię zapasową i test na kopii mapy.
- Pierwsza wersja wymaga testów i ewentualnych poprawek pod konkretną kompilację Spigot 1.21.11.

## Budowanie
Wymagane: JDK 21 i Maven 3.9+.

W katalogu projektu uruchom:
```bash
mvn clean package
```
Plik wynikowy powinien znaleźć się w `target/ElaesteStructures-1.0.0.jar`.

Skopiuj JAR do `plugins/`, uruchom ponownie serwer i sprawdź konsolę.
