# 🌙 Time Saver (Pora snu)

Aplikacja na Androida, która w nocy przypomina, że **to nie jest pora korzystania z telefonu**.

Po odblokowaniu telefonu w godzinach ciszy nocnej pojawia się pełnoekranowy komunikat. Możesz go obejść, ale tylko podając powód. Każde obejście trafia do historii, więc widać, ile razy to się zdarzyło i dlaczego.

To narzędzie do samodyscypliny, nie kontrola rodzicielska. Ma dodać chwilę namysłu, a nie zamknąć telefon na amen.

---

## ✨ Funkcje

- **Pełnoekranowa blokada** w ustawionych godzinach (domyślnie 22:00–07:00). Przycisk Wstecz nie działa, a Home i „Ostatnie aplikacje” nie pozwalają uciec: ekran wraca po ok. 2 s. Zmniejszenie blokady do pływającego okna, obrazu w obrazie (PiP) albo podzielonego ekranu jest wykrywane: blokada wraca na pełny ekran, a próba trafia do historii.
- **Obejście z powodem.** Wpisujesz, dlaczego musisz teraz użyć telefonu (min. 15 znaków i 3 słowa), wybierasz 5, 15 albo 30 minut i czekasz 10 sekund, zanim przycisk się odblokuje.
- **Dozwolone aplikacje.** Sam wybierasz aplikacje, z których możesz korzystać w nocy bez podawania powodu (np. budzik, kalendarz, plan zajęć), i otwierasz je prosto z ekranu blokady. Domyślnie lista jest pusta, a telefon jest dozwolony zawsze. Dostęp obowiązuje tylko wtedy, gdy jesteś w tej aplikacji: wyjście do ekranu głównego albo przełączenie na inną aplikację przywraca blokadę. Listę możesz zmienić.
- **Monitorowanie.** Aplikacja zapisuje próby odblokowania, obejścia (z powodem), telefony, wyłączenia ochrony i zmiany ustawień. Statystyki „tej nocy” i „ostatnie 7 dni” widać na ekranie blokady i w aplikacji.
- **Wyłączenie tylko z powodem.** Wyłączenie ochrony, zmiana godzin i dodanie nowej dozwolonej aplikacji (przy włączonej ochronie) wymagają uzasadnienia, które trafia do historii.
- **Telefon zawsze działa.** Przycisk „📞 Muszę zadzwonić” otwiera aplikację telefonu. Działa ona jak pozostałe dozwolone aplikacje: po wyjściu z niej blokada wraca. Blokada nie pokazuje się też w trakcie rozmowy.
- **Działa po restarcie** telefonu i po aktualizacji aplikacji.
- **Bez internetu i bez reklam.** Wszystkie dane zostają na telefonie.

---

## 📲 Instalacja (bez komputera)

1. Wejdź w zakładkę **[Releases](../../releases/latest)** i pobierz plik `PoraSnu.apk`.
2. Otwórz pobrany plik. Android zapyta o zgodę na „instalowanie nieznanych aplikacji” z przeglądarki. Zezwól na nią.
3. Zainstaluj aplikację i otwórz **Pora snu**.

APK jest budowany automatycznie przez GitHub Actions z kodu w tym repozytorium. Jest podpisany kluczem debug, więc Google Play Protect może pokazać ostrzeżenie. Wybierz wtedy „Zainstaluj mimo to”.

## ⚙️ Pierwsze uruchomienie

W aplikacji nadaj po kolei uprawnienia (przyciski są na ekranie):

| Uprawnienie | Po co |
|---|---|
| Powiadomienia | Stałe powiadomienie „Pora snu czuwa” (wymóg Androida dla usług działających w tle) |
| **Wyświetlanie nad innymi aplikacjami** | Bez tego Android nie pozwoli pokazać blokady w tle |
| **Dostęp do danych o użyciu** | Aplikacja wie, co jest na ekranie, i nie zasłania dozwolonych aplikacji |
| Bez optymalizacji baterii | System nie wyłącza ochrony w nocy |

Potem ustaw godziny ciszy, dodaj aplikacje dozwolone w nocy (**+ Dodaj aplikację**, np. budzik) i włącz przełącznik **„Ochrona włączona”**.

**Producenci z agresywnym oszczędzaniem baterii:**
- **Xiaomi / Redmi / POCO:** włącz „Autostart” i „Wyświetlaj wyskakujące okna w tle”.
- **Samsung:** Bateria → „Bez ograniczeń”.
- **Huawei / Honor:** Uruchamianie aplikacji → zarządzaj ręcznie i włącz wszystko.

Więcej porad dla konkretnych telefonów znajdziesz na [dontkillmyapp.com](https://dontkillmyapp.com).

---

## 🛠️ Budowanie ze źródeł

Wymagania: Android Studio (Ladybug lub nowszy) albo JDK 17 + Android SDK 35 + Gradle 8.9.

```bash
git clone https://github.com/<twoj-login>/<repo>.git
cd <repo>
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

W Android Studio wystarczy otworzyć folder, podłączyć telefon i kliknąć **Run**.

### Własne APK przez GitHub Actions
Zrób fork repozytorium. Każdy push na `main` zbuduje APK i opublikuje go w zakładce Releases twojego forka. Możesz też ręcznie uruchomić workflow **Build APK** w zakładce Actions.

---

## 🧩 Jak to działa (dla programistów)

Kotlin, czyste Android SDK, zero zewnętrznych bibliotek. minSdk 26 (Android 8.0), targetSdk 34.

| Plik | Rola |
|---|---|
| `GuardService.kt` | Usługa pierwszoplanowa. Nasłuchuje odblokowania (`ACTION_USER_PRESENT`) i co 2 s sprawdza, czy trwa cisza nocna, czy ekran jest odblokowany, czy nie trwa rozmowa i czy na ekranie nie ma dozwolonej aplikacji (`UsageStatsManager`). Jeśli trzeba, uruchamia blokadę. |
| `BlockActivity.kt` | Pełnoekranowy ekran blokady: zegar, statystyki, dozwolone aplikacje i formularz obejścia z powodem. |
| `MainActivity.kt` | Ustawienia, uprawnienia, lista dozwolonych aplikacji, statystyki i historia. |
| `Store.kt` | Ustawienia i dziennik zdarzeń w `SharedPreferences` (JSON, do 1000 ostatnich wpisów). |
| `Apps.kt` | Lista zainstalowanych aplikacji i sprawdzanie uprawnień. |
| `BootReceiver.kt` | Wznawia ochronę po restarcie i po aktualizacji aplikacji. |

Uruchamianie aktywności z tła jest możliwe dzięki uprawnieniu `SYSTEM_ALERT_WINDOW`, które jest jednym z wyjątków od ograniczeń Androida 10+.

### Pomysły na rozwój
- Różne godziny dla dni tygodnia (np. dłużej w weekend)
- Eksport historii do CSV
- Tryb „twardy” przez Device Admin, który blokuje odinstalowanie
- Wykres obejść w czasie

Pull requesty są mile widziane.

---

## ⚠️ Ograniczenia

- Ochronę da się obejść „siłowo”: wymuszonym zatrzymaniem w ustawieniach, odebraniem uprawnień albo odinstalowaniem aplikacji. To świadoma decyzja, bo narzędzie ma pomagać, a nie więzić.
- Bez „dostępu do danych o użyciu” blokada zasłoni też dozwolone aplikacje.
- Niektóre nakładki producentów mogą zabijać usługę w tle (patrz sekcja o bateriach).

## 📄 Licencja

[MIT](LICENSE). Używaj, zmieniaj i udostępniaj dalej.
