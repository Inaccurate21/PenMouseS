# PenMouse S Live — stały podpis i aktualizacje Android APK

Od wersji **2.5-live.5** aplikacja powinna używać jednego certyfikatu dla każdej kolejnej aktualizacji.
Repozytorium zawiera **wyłącznie zaszyfrowany** plik `app/signing/live-debug-keystore.enc.b64`.
Hasło musi być przechowywane jako prywatny sekret GitHub Actions: `LIVE_SIGNING_SECRET`.
Klucz i hasło są zawarte także w **prywatnej kopii właściciela** poza GitHubem.

## Jednorazowa konfiguracja GitHub — wymagana

1. Wejdź do swojego forka: Settings → Secrets and variables → Actions.
2. Dodaj **New repository secret** o nazwie `LIVE_SIGNING_SECRET`.
3. Ustaw wartość hasła z pliku `PRYWATNE-DANE-PODPISU.txt` z prywatnej kopii podpisu, **bez cudzysłowów i spacji**.
4. Zapisz. Przejdź do Actions → Build Live test APK → Run workflow. Wybierz gałąź `realtime-drag`.

Workflow odszyfrowuje klucz **tylko wewnątrz maszyny GitHub Actions**, uruchamia testy,
kompiluje APK i wymaga, aby certyfikat gotowej aplikacji miał SHA-256:
`dd4c7dc6d94e293a64d007d93dd139ad3d062cb840cef3da68de75aa87b7ab2e`.
Jeżeli sekret nie został skonfigurowany, workflow kończy się błędem i **nie publikuje APK**.
Nigdy nie zapisuj niezabezpieczonej kopii `.p12`, hasła ani prywatnej kopii ZIP w repozytorium.

## Instalowanie aktualizacji

Identyfikator aplikacji pozostaje niezmieniony: `pl.inaccurate21.penmouses.live.debug`.
Każda nowa wersja musi mieć **większy** `versionCode` w `gradle/libs.versions.toml`.
Następnie APK ze zweryfikowanym certyfikatem można instalować na istniejącej wersji,
bez kasowania aplikacji, ustawień ani ponownego nadawania uprawnień.

**Uwaga:** wydania 2.2, 2.3 i 2.4 miały trzy różne certyfikaty Android Debug.
Żaden z nich nie jest zgodny z nowym stałym kluczem. Instalacja pierwszego wydania
ze stałym podpisem na ich miejsce wymaga jednorazowego usunięcia poprzedniej aplikacji.
Nie da się obejść tego ograniczenia przez zmianę `versionCode` ani nazwę pliku.
Przed reinstalacją można rozważyć kopię danych przez ADB/debug `run-as`,
ale same uprawnienia dostępności Android może wymagać włączyć ponownie.

Późniejsze wersje 2.6, 2.7… muszą używać **tego samego sekretu i klucza**.
Jeśli zgubisz klucz i hasło, nie będzie można zaktualizować podpisanej nim instalacji
z zachowaniem danych przy użyciu innego certyfikatu.
