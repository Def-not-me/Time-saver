# Pora snu – blokada telefonu w nocy

## Jak działa
- W godzinach ciszy (domyślnie 22:00–07:00) po odblokowaniu telefonu pojawia się pełnoekranowy komunikat **„To nie jest pora korzystania z telefonu”**.
- Przycisk Wstecz nic nie robi. Po wciśnięciu Home lub „Ostatnie” blokada wraca po ok. 2 s.
- **Obejście:** „Muszę skorzystać…” → wpisujesz powód (min. 15 znaków, 3 słowa), wybierasz 5/15/30 min i czekasz 10 s, zanim przycisk się odblokuje.
- **Wyłączenie ochrony** albo zmiana godzin (gdy ochrona jest włączona) też wymaga podania powodu.
- **Monitorowanie:** każda próba odblokowania, obejście, telefon i wyłączenie trafiają do historii (razem z powodem). Statystyki widać na ekranie blokady i w aplikacji.
- **Dozwolone aplikacje:** budzik, Kalendarz Google, Claude i USOS (wyszukiwany po nazwie) możesz otworzyć prosto z ekranu blokady, bez podawania powodu. Dopóki są na ekranie, blokada się nie pokazuje. Listę zmienisz w aplikacji; dodanie nowej aplikacji przy włączonej ochronie wymaga podania powodu.
- Przycisk „📞 Muszę zadzwonić” otwiera telefon na 5 min (też zapisywane w historii). Rozmowy przychodzące nie są blokowane.

## Budowanie
1. Otwórz folder `PoraSnu` w Android Studio (Ladybug lub nowszym) i poczekaj na synchronizację Gradle.
2. Podłącz telefon z włączonym debugowaniem USB → **Run** (albo Build → Build APK).

## Pierwsze uruchomienie
1. Zezwól na powiadomienia.
2. Nadaj „Wyświetlanie nad innymi aplikacjami” – bez tego Android nie pozwoli pokazać blokady w tle.
3. Nadaj „Dostęp do danych o użyciu” – dzięki temu aplikacja wie, że masz otwarty np. kalendarz, i go nie zasłania.
4. Wyłącz optymalizację baterii dla aplikacji.
5. Włącz przełącznik „Ochrona włączona”.

Na Xiaomi/MIUI włącz dodatkowo „Wyświetlaj wyskakujące okna w tle” i „Autostart”. Na Samsungu ustaw baterię na „Bez ograniczeń”.

## Ograniczenia
To narzędzie samodyscypliny, a nie zabezpieczenie nie do obejścia. Wymuszone zatrzymanie aplikacji, jej odinstalowanie albo zmiana uprawnień w ustawieniach systemu wyłączy ochronę. Żeby utrudnić te obejścia, trzeba by dodać tryb administratora urządzenia albo Device Owner.
