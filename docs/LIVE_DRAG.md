# PenMouse S Live — wersja testowa na telefon

Fork projektu [PenMouse S](https://github.com/jojczak/PenMouseS), autor: jojczak.
Oryginalna licencja GPL-3.0 i informacje o autorze pozostają zachowane.
Zakres tej wersji: ekran telefonu Android z S Pen Air Actions. Bez DeX, komputera,
roota, Shizuku i serwera sieciowego.

## Instalacja i uruchomienie

1. W GitHub Actions wybierz **Build Live test APK**, a następnie udany przebieg
   i artefakt **PenMouseS-Live-test-APK**. Rozpakuj ZIP i zainstaluj APK.
   Jeśli Actions jest wyłączone w świeżym forku, właściciel musi je najpierw włączyć.
2. Aplikacja nazywa się **PenMouse S Live [TEST]**. Ma osobny identyfikator
   `pl.inaccurate21.penmouses.live.debug`; nie zastępuje oryginału ani jego ustawień.
   Wyłącz usługę oryginalnego PenMouse S podczas testów, aby aplikacje nie konkurowały.
3. Włącz usługę ułatwień dostępu dla **tej wersji**, pozwól na powiadomienia
   i wykonaj oryginalne kroki przygotowania S Pena w instrukcji aplikacji.
4. **Settings → Mouse → Naciśnij i przeciągaj na żywo (testowe)**. Opcja jest
   domyślnie wyłączona. Następnie uruchom tryb **Mouse**.
5. Zacznij od **16 ms**. Jeżeli występują przerwania/skoki, spróbuj 24–32 ms.
   8 ms to wariant bardziej agresywny. Ta wartość jest czasem fragmentu gestu,
   **nie pomiarem ani gwarancją całkowitego opóźnienia**.

## Co się zmieniło

- Przycisk DOWN od razu rozpoczyna emulowany dotyk. Ruch kontynuuje ten sam
  dotyk przez `continueStroke`; puszczenie kończy go przez `willContinue=false`.
- Jednocześnie trwa najwyżej jeden fragment. Nowy czeka na `onCompleted`,
  wykorzystując najnowszą pozycję zamiast odtwarzać zaległą kolejkę próbek.
- Nieruchome przytrzymanie nie odpytuje systemu i nie wysyła ciągle gestów.
- Wizualna nakładka aktualizuje się najwyżej raz na klatkę; współrzędne wejściowe
  są aktualizowane od razu. Zachowywane są ułamkowe części ruchu, zamiast je gubić.
- Usunięto logowanie każdej próbki ruchu i mieszanie zegara SDK z zegarem Androida
  przy obliczaniu czasu w trybie klasycznym.
- Zatrzymanie, zgaszenie ekranu i rozłączenie S Pena zlecają zwolnienie dotyku.
  Oczekujące zwolnienie ma własny handler i może się zakończyć po zatrzymaniu trybu.
- Limit przytrzymania: **30 sekund**. Brak callbacku przez **1 sekundę** uruchamia
  próbę awaryjnego zwolnienia istniejącego gestu — nigdy niezależny klik zastępczy.
  Po błędzie należy puścić przycisk przed następną próbą. Jeśli dotyk się zablokuje,
  zatrzymaj aplikację / wyłącz jej usługę ułatwień dostępu.
- Kompilacja debug nie wymaga `google-services.json` ani kluczy autora.
  Bez konfiguracji Firebase opcjonalna telemetria nie jest inicjalizowana.

## Ważne ograniczenia

To nadal **emulacja palca**, nie fizyczna mysz ani prawdziwe LPM. Zwykłe kliknięcie
przycisku w interfejsie Androida zwykle zatwierdza się po UP, mimo natychmiastowego
rozpoczęcia dotyku po DOWN. Długie przytrzymanie może otworzyć menu kontekstowe.
Zachowanie przeciągania zależy od aplikacji docelowej. Nie ma gwarancji obsługi
gier, wielodotyku ani równoczesnego korzystania z ekranu palcem.
Fizyczny dotyk i inne usługi generujące gesty mogą anulować nasz gest.

Próbkowanie rysika, BLE, firmware Samsunga i renderowanie ekranu nadal dodają
opóźnienie. Testy jednostkowe nie zastępują pomiaru na fizycznym telefonie.
Nie twierdzimy, że 120 Hz ekranu oznacza 120 próbek S Pena na sekundę.

## Lista prób na telefonie

- Krótkie kliknięcie oraz dwa kolejne kliknięcia.
- Przeciąganie suwaka przez 5–10 sekund; po puszczeniu suwak natychmiast przestaje się przesuwać.
- Przytrzymanie nieruchomo, potem ruch; nie powinien powstawać drugi dotyk.
- Puszczenie przycisku podczas szybkiego ruchu; dalszy ruch rysika nie przeciąga elementu.
- Wyłączenie ekranu i rozłączenie / schowanie rysika podczas przeciągania.
- Zatrzymanie usługi z powiadomienia podczas przeciągania.
- Przytrzymanie 30 sekund: zwolnienie bezpieczeństwa, potem puszczenie i ponowne naciśnięcie.
- Wyłączenie trybu na żywo: powrót do klasycznego odtwarzania gestu po puszczeniu.
- Porównanie 8/16/24/32 ms na tym samym suwaku i nagranie ekranu z widocznym rysikiem.

W zgłoszeniu testowym podaj model telefonu, wersję Androida/One UI, ustawienie ms,
nazwę aplikacji docelowej i dokładne kroki. Nie przesyłaj prywatnej zawartości ekranu.

## Testy i kompilacja lokalna

JDK 21, Android SDK Platform 37.0 i Build Tools 37.0.0:

```sh
bash ./gradlew :mousemode:mouse:testDebugUnitTest :app:assembleDebug
```

Testy stanów pokrywają kolejność DOWN/MOVE/UP, koaleskowanie próbek, anulowanie,
odrzucony dispatch, wyjątek, spóźniony callback, watchdog i limit przytrzymania.
Testy integracyjne Android/Samsung oraz rzeczywiste opóźnienie wymagają telefonu.
