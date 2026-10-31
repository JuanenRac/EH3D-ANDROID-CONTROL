<p align="center">
  <img src="images/EH3D_BANNER.svg" alt="EH3D-ANDROID-CONTROL banner" width="100%">
</p>

# 🤖 EH3D-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | 🇩🇪 <b>Deutsch</b> | <a href="README_zho.md">🇨🇳 中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### Die Android-App der EH3D-Programme: Sie verfolgt die Bungard-Maschinen der Werkstatt in Echtzeit über EH3D-SERVER, hält sie an, zeigt den Auftrag in 2D und 3D und die Kamera des Bohrkopfes

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%2B%20Jetpack%20Compose-7F52FF.svg" alt="Kotlin, Jetpack Compose">
  <img src="https://img.shields.io/badge/Languages-8-00E5FF.svg" alt="Languages">
  <img src="https://img.shields.io/badge/Version-0.0.1-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-scaffolding-FFB020.svg" alt="Maturity">
</p>

---

**Ehrlichkeits-Check - was heute wirklich läuft:** Die Regeln der App (lesen, was EH3D-SERVER über Maschinen, Aufträge und Bohrkopf sagt, die Position des Kopfes aus den erledigten Schritten, die 2D- und isometrische Geometrie, die Sprachen, die Adressprüfungen und das Behalten der Sitzung) sind geschrieben und auf einem Computer getestet. Die Bildschirme sind für Android im Forge-Design der EH3D-Programme geschrieben, aber **die App wurde auf einem Telefon nie gegen einen echten Server ausgeführt**, der Not-Halt hat nie eine echte Maschine angehalten und die Kamera nie eine echte Platine gezeigt.

---

> **Sicherheitsstatus:** Diese App ist kein Sicherheitssystem und ersetzt nicht den Not-Halt der Maschine. Ihr Stopp bricht den laufenden Auftrag über den Server ab, der die Maschine anhält und die Antriebe abschaltet; Netz und Server müssen funktionieren. Nur Bediener und Administratoren können stoppen oder starten; das Passwort wird nur an eine Adresse im lokalen Netz oder über https gesendet, und die Sitzung wird verschlüsselt auf dem Telefon gehalten.


## 📋 Auf einen Blick

| | |
|---|---|
| Version | `0.0.1` |
| Reifegrad | Gerüst |
| Tests | Unit-Tests auf der JVM |
| Technologie-Stack | Kotlin, Jetpack Compose |
| Auf echter Hardware | Noch nicht: Die App wurde nicht auf einem Telefon, gegen einen echten Server oder mit einer Maschine ausgeführt; als Erstes ist die Anmeldung am EH3D-SERVER der Werkstatt zu versuchen. |

**Springe zu:** [Überblick](#-überblick) · [Architektur](#-architektur) · [Schnellstart](#-schnellstart) · [Tests](#-tests) · [Struktur](#-struktur-des-repositorys) · [Verwandte](#-verwandte-projekte)

## 🚦 Projektstatus

Jede Zeile ist ein Teil dieses Repositorys, und die Ampel sagt, wie weit er heute ist. Sie beruht auf dem, was das Repository zeigen kann (Tests, Builds, Läufe), nicht auf dem, was geplant ist.

🟢 Fertig und überprüft · 🟡 Funktioniert, mit bekannten Grenzen · 🟠 Teilweise fertig oder noch nicht überprüft · 🔴 Nicht gemacht oder funktioniert nicht

| Bereich | Stand | Was das hier bedeutet |
|---|---|---|
| Software | 🟠 | Die Bildschirme und die Regeln der App sind für Android geschrieben; die Regeln werden ohne Telefon gebaut und getestet. |
| Tests | 🟡 | Unit-Tests auf der JVM: was der Server über Flotte, Aufträge und Kopf sagt, die Position des Kopfes, die 2D- und isometrische Geometrie, die Sprachen und die Adress- und Versionsprüfungen. |
| Überwachung | 🟠 | Jede Maschine der Werkstatt mit Zustand, Auftrag, Fortschritt, erledigten Schritten und Restzeit, alle anderthalb Sekunden vom Server gelesen; eine Meldung, wenn eine Maschine auf den Bediener wartet oder stoppt. Geschrieben, nicht gegen einen echten Server ausgeführt. |
| Not-Halt | 🟠 | Ein roter Knopf, der zweimal fragt und den laufenden Auftrag über den Server abbricht (eine Maschine oder alle); die Bestätigung der Schritte, auf die ein Auftrag wartet. Nur Bediener und Administratoren. Er hat nie eine echte Maschine angehalten und ersetzt nicht deren physischen Stopp. |
| 2D- und 3D-Ansicht | 🟠 | Platine, Löcher (ein Ring bis zum Bohren), Bahnen (blass bis zum Fräsen) und Kopf, in 2D und in einer isometrischen Ansicht, die sich mit dem Finger dreht; die Position des Kopfes ist eine Schätzung aus den erledigten Schritten. Als Geometrie getestet, nie auf einem Telefon gesehen. |
| Kamera des Kopfes | 🟠 | Die Kamera des Bohrkopfes (EH3D-NODE-BUNGARD-AUX) als MJPEG über den Server, ihre Drehzahl, Temperatur und der Schutzausgang sowie eine Tischmessung mit dem Laser. Geschrieben, nie mit einem echten Knoten ausgeführt. |
| Updates | 🟠 | Sie sucht die neueste Release dieses Projekts auf GitHub, lädt die APK, prüft Paket, Version und Signatur und übergibt sie dem Android-Installer; sie installiert nie von selbst. Geschrieben und getestet; nie von einem Telefon gegen GitHub ausgeführt. |
| Echter Einsatz | 🔴 | Noch nicht: Die App wurde nicht auf einem Telefon, gegen einen echten Server oder mit einer Maschine ausgeführt; als Erstes ist die Anmeldung am EH3D-SERVER der Werkstatt zu versuchen. |
| Dokumentation | 🟡 | README in acht Sprachen, die Hilfe der App in acht Sprachen und die Hinweise zur verwendeten Server-API. |

## 🎯 Überblick

* Jede Maschine der Werkstatt mit Zustand, Auftrag, Fortschritt, erledigten Schritten und Restzeit, alle anderthalb Sekunden vom Server gelesen; eine Meldung, wenn eine Maschine auf den Bediener wartet oder stoppt. Geschrieben, nicht gegen einen echten Server ausgeführt.
* Ein roter Knopf, der zweimal fragt und den laufenden Auftrag über den Server abbricht (eine Maschine oder alle); die Bestätigung der Schritte, auf die ein Auftrag wartet. Nur Bediener und Administratoren. Er hat nie eine echte Maschine angehalten und ersetzt nicht deren physischen Stopp.
* Platine, Löcher (ein Ring bis zum Bohren), Bahnen (blass bis zum Fräsen) und Kopf, in 2D und in einer isometrischen Ansicht, die sich mit dem Finger dreht; die Position des Kopfes ist eine Schätzung aus den erledigten Schritten. Als Geometrie getestet, nie auf einem Telefon gesehen.
* Die Kamera des Bohrkopfes (EH3D-NODE-BUNGARD-AUX) als MJPEG über den Server, ihre Drehzahl, Temperatur und der Schutzausgang sowie eine Tischmessung mit dem Laser. Geschrieben, nie mit einem echten Knoten ausgeführt.
* Sie sucht die neueste Release dieses Projekts auf GitHub, lädt die APK, prüft Paket, Version und Signatur und übergibt sie dem Android-Installer; sie installiert nie von selbst. Geschrieben und getestet; nie von einem Telefon gegen GitHub ausgeführt.

## 🧩 Rolle in EH3D

EH3D-ANDROID-CONTROL ist der Taschenbildschirm der EH3D-Werkstatt: Sie spricht nur mit EH3D-SERVER, der die Aufträge auf den Maschinen ausführt (über EH3D-NODE-BUNGARD) und die Kamera des Bohrkopfes (EH3D-NODE-BUNGARD-AUX) weiterreicht. Aufträge werden in EH3D-STUDIO oder BUNGARD-GUI vorbereitet; diese App verfolgt sie, bestätigt die Schritte, auf die sie warten, und hält sie an.

## 🏛️ Architektur

<p align="center">
  <img src="images/architecture.svg" alt="Architektur von EH3D-ANDROID-CONTROL: wer es nutzt, was es enthält und wovon es abhängt" width="100%">
</p>

Wie die Teile dieses Repositorys zusammenpassen: wer es nutzt, was es enthält und wovon es abhängt. Das Bild wird aus `docs/architecture.json` gezeichnet und gegen den Code geprüft.

## 🖼️ Screenshots

<p align="center">
  <img src="images/screenshots/app-home.png" alt="app-home" width="30%">
</p>

<p align="center"><em>Die Maschinen der Werkstatt: frei, arbeitend und auf den Bediener wartend, mit dem Stopp aller.</em></p>

<p align="center">
  <img src="images/screenshots/app-machine.png" alt="app-machine" width="30%">
</p>

<p align="center"><em>Eine Maschine: der Stopp, der laufende Auftrag und die 2D-Live-Ansicht mit der Position des Kopfes.</em></p>

<p align="center">
  <img src="images/screenshots/app-viewer3d.png" alt="app-viewer3d" width="30%">
</p>

<p align="center"><em>Die isometrische Ansicht desselben Auftrags, mit dem Finger gedreht, und seine Werkzeuge.</em></p>

<p align="center">
  <img src="images/screenshots/app-head.png" alt="app-head" width="30%">
</p>

<p align="center"><em>Der Bohrkopf: die Kamera über den Server, Drehzahl, Temperatur, der Schutzausgang und die Tischmessung.</em></p>

<p align="center">
  <img src="images/screenshots/app-jobs.png" alt="app-jobs" width="30%">
</p>

<p align="center"><em>Die Aufträge des Servers mit ihren Zuständen.</em></p>

<p align="center">
  <img src="images/screenshots/app-more.png" alt="app-more" width="30%">
</p>

<p align="center"><em>Mehr: die Sitzung, das Update von GitHub, die Sprache und die Hilfe.</em></p>

<p align="center"><em>Eine Zeichnung der Bildschirme mit denselben Regeln und einem Beispielauftrag, keine Aufnahme von einem Telefon: Die App wurde auf keinem ausgeführt.</em></p>

## 🚀 Schnellstart

Sie brauchen JDK 21 und das Android-SDK (der Gradle-Wrapper erledigt den Rest) und einen EH3D-SERVER im Netz.

```bash
build.bat          # tests and debug APK (Windows)  -  ./build.sh on Linux
test.bat           # only the tests
python -I tools/ci_validate.py
```

App öffnen, die Adresse des Servers (und Port 18200) eingeben, Verbinden drücken und anmelden. Ein Server ohne Benutzer verlangt den Einrichtungscode von seiner Konsole und legt den ersten Administrator an.

## 🧪 Tests

`./gradlew test` führt die Unit-Tests auf der JVM aus: die Leser des Server-JSON (Flotte, Aufträge, Kopf, Messung), die Position des Kopfes und die Geometrie der beiden Ansichten, die Adressprüfung, die verschlüsselte Sitzungsspeicherung, die Version und das Release-Tor des Updates sowie die Sprachen (jeder Satz des Quelltexts in den sieben Tabellen, mit denselben Platzhaltern).

## 🏗️ Build und Veröffentlichung

Die Version steht in `eh3d.project.json` und in `app/build.gradle.kts` (der Build bricht ab, wenn sie sich unterscheiden), und jede Version hat ihren Eintrag in [CHANGELOG.md](CHANGELOG.md). Die Release-APK wird mit R8 verkleinert und danach mit dem eigenen Schlüssel des Maintainers signiert, der außerhalb des Repositorys liegt; die Datei einer Release ist `EH3D-ANDROID-CONTROL-release.apk`, die das Update von GitHub sucht.

## 📂 Struktur des Repositorys

<p align="center">
  <img src="images/repository-structure.svg" alt="Struktur des Repositorys EH3D-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Textversion des Baums</summary>

```text
EH3D-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts proguard-rules.pro      der Build und die Verkleinerungsregeln
│   ├── src/main/AndroidManifest.xml             Berechtigungen und die eine Activity
│   ├── src/main/assets/i18n/                    die Texte in acht Sprachen
│   ├── src/main/java/es/electrohobby3d/eh3d/    der Code der App
│   └── src/test/java/es/electrohobby3d/eh3d/    die Tests auf der JVM
├── tools/
│   ├── ci_validate.py                           Manifest, Versionen, README-Parität
│   └── make_architecture.py make_structure.py   zeichnen die zwei Bilder des README
├── docs/                                        die Quellen der zwei Bilder
├── gradle/                                      die Dateien des Gradle-Wrappers
├── gradlew                                      der Gradle-Wrapper
├── gradlew.bat                                  der Wrapper für Windows
├── build.gradle.kts                             die Plugins des Builds
├── settings.gradle.kts                          der Name des Projekts
├── gradle.properties                            der Speicher des Builds
├── images/                                      Banner und Bilder
├── .github/                                     CI-Workflow
├── build.bat build.sh                           bauen und Tests ausführen
├── eh3d.project.json                            Version, Rolle, Reife
├── CHANGELOG.md                                 was sich in jeder Version geändert hat
├── SECURITY.md SUPPORT.md                       Schwachstelle melden, fragen
├── CONTRIBUTING.md                              wie man beiträgt
├── CODE_OF_CONDUCT.md                           der Verhaltenskodex
└── LICENSE                                      die Lizenz
```

</details>

## 🗺️ Roadmap und bekannte Grenzen

**Bekannte Grenzen heute**

* Die App wurde nicht auf einem Telefon, gegen einen echten Server oder mit einer Maschine ausgeführt.
* Die Position des Kopfes ist eine Schätzung aus den erledigten Schritten; der Server meldet nicht die echte Position der Maschine.
* Der Not-Halt läuft über Server und Netz: Er ist kein physischer Stopp.
* Die Kamera hat nie ein echtes Bild gezeigt, und die Tischmessung wurde nie mit einem echten Laser genommen.

**Als Nächstes**

* Sich am EH3D-SERVER der Werkstatt anmelden und einen echten Auftrag verfolgen.
* Die wahre Position des Kopfes zeigen, sobald der Server sie meldet.
* Meldungen bei geschlossener App (ein Push-Kanal des Servers).
* Eine Seite, um Aufträge vom Telefon aus vorzubereiten und zu starten.

## 🌍 Sprachen

README und Texte gibt es auf Englisch, Spanisch, Französisch, Italienisch, Deutsch, Chinesisch, Japanisch und europäischem Portugiesisch.

## 🔗 Verwandte Projekte

* [EH3D-SERVER](https://github.com/JuanenRac/EH3D-SERVER): das Zentrum der EH3D-Familie: Es führt die Aufträge auf den Maschinen aus und liefert dieser App alles, was sie zeigt
* [EH3D-STUDIO](https://github.com/JuanenRac/EH3D-STUDIO): die Seiten, auf denen Maschinen hinzugefügt und Aufträge vorbereitet werden
* [EH3D-BUNGARD-GUI](https://github.com/JuanenRac/EH3D-BUNGARD-GUI): das Programm am Computer, das Bohr- und Fräsaufträge vorbereitet und ausführt
* [EH3D-NODE-BUNGARD-AUX](https://github.com/JuanenRac/EH3D-NODE-BUNGARD-AUX): der Knoten des Bohrkopfes: Kamera, Laser und Sensoren, die diese App zeigt
* [EH3D-IOS-CONTROL](https://github.com/JuanenRac/EH3D-IOS-CONTROL): die Android- und die iPhone-App sind Zwillinge: dieselben Regeln und dieselben Bildschirme

## 📚 Dokumentation

* [Die API des Servers](https://github.com/JuanenRac/EH3D-SERVER/blob/main/docs/API.md): was diese App liest und anfordert.
* [Änderungsprotokoll](CHANGELOG.md)

## 🤝 Mitwirken und Community

* Vor einem Pull-Request `./gradlew test` und `python -I tools/ci_validate.py` ausführen: beide müssen bestehen. Siehe [CONTRIBUTING.md](CONTRIBUTING.md) und den [Verhaltenskodex](CODE_OF_CONDUCT.md).
* Jeder Text der App steht in den Tabellen unter `app/src/main/assets/i18n` in allen acht Sprachen; ein Test hält sie vollständig.
* Eine Sicherheitslücke privat melden: [SECURITY.md](SECURITY.md). Fragen, Ideen und Meldungen: electrohobby3d@gmail.com ([SUPPORT.md](SUPPORT.md)).

## 👤 AUTOR

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LIZENZ

Software: GPL-3.0-or-later. Siehe [LICENSE](LICENSE). Die Hardware-Entwürfe der Suite verwenden die CERN OHL-S.
