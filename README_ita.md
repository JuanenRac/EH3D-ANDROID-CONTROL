<p align="center">
  <img src="images/EH3D_BANNER.svg" alt="EH3D-ANDROID-CONTROL banner" width="100%">
</p>

# 🤖 EH3D-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | 🇮🇹 <b>Italiano</b> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### L'app Android dei programmi EH3D: segue in tempo reale le macchine Bungard dell'officina tramite EH3D-SERVER, le ferma, mostra il lavoro in 2D e 3D e la telecamera della testa di foratura

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%2B%20Jetpack%20Compose-7F52FF.svg" alt="Kotlin, Jetpack Compose">
  <img src="https://img.shields.io/badge/Languages-8-00E5FF.svg" alt="Languages">
  <img src="https://img.shields.io/badge/Version-0.0.5-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-scaffolding-FFB020.svg" alt="Maturity">
</p>

---

**Verifica di onestà - cosa funziona oggi:** le regole dell'app (leggere ciò che EH3D-SERVER dice di macchine, lavori e testa, la posizione della testa dai passi fatti, la geometria 2D e isometrica, le lingue, i controlli dell'indirizzo e la conservazione della sessione) sono scritte e provate su un computer. Le schermate sono scritte per Android nel tema Forge dei programmi EH3D, ma **l'app non è stata eseguita su un telefono contro un server reale**, l'arresto di emergenza non ha mai fermato una macchina reale e la telecamera non ha mai mostrato una scheda reale.

---

> **Stato di sicurezza:** questa app non è un sistema di sicurezza e non sostituisce l'arresto di emergenza della macchina. Il suo arresto annulla il lavoro in corso tramite il server, che ferma la macchina e spegne i motori; servono la rete e il server funzionanti. Solo operatori e amministratori possono fermare o avviare; la password viene inviata solo a un indirizzo della rete locale o via https, e la sessione è conservata cifrata sul telefono.


## 📋 In breve

| | |
|---|---|
| Versione | `0.0.5` |
| Maturità | Impalcatura |
| Test | test unitari sulla JVM |
| Stack tecnologico | Kotlin, Jetpack Compose |
| Su hardware reale | Non ancora: l'app non è stata eseguita su un telefono, contro un server reale né con una macchina; la prima cosa da provare è accedere all'EH3D-SERVER dell'officina. |

**Vai a:** [Panoramica](#-panoramica) · [Architettura](#-architettura) · [Avvio rapido](#-avvio-rapido) · [Test](#-test) · [Struttura](#-struttura-del-repository) · [Correlati](#-progetti-correlati)

## 🚦 Stato del progetto

Ogni riga è una parte di questo repository e il semaforo dice a che punto è oggi. È scritta in base a ciò che il repository può mostrare (test, compilazioni, esecuzioni), non a ciò che è previsto.

🟢 Fatto e verificato · 🟡 Funziona, con limiti noti · 🟠 Fatto a metà o non ancora verificato · 🔴 Non fatto o non funzionante

| Area | Stato | Cosa significa qui |
|---|---|---|
| Software | 🟠 | Le schermate e le regole dell'app sono scritte per Android; le regole si compilano e si provano senza telefono. |
| Test | 🟡 | test unitari sulla JVM: ciò che dice il server di flotta, lavori e testa, la posizione della testa, la geometria 2D e isometrica, le lingue e i controlli di indirizzo e versione. |
| Monitoraggio | 🟠 | Ogni macchina dell'officina con il suo stato, il lavoro, l'avanzamento, i passi fatti e il tempo restante, letti dal server ogni secondo e mezzo; un avviso quando una macchina attende l'operatore o si ferma. Scritto, non eseguito contro un server reale. |
| Arresto di emergenza | 🟠 | Un pulsante rosso che chiede due volte e annulla il lavoro in corso tramite il server (una macchina o tutte); la conferma dei passi che un lavoro attende. Solo operatori e amministratori. Non ha mai fermato una macchina reale e non sostituisce il suo arresto fisico. |
| Preparare lavori | 🟠 | Scegliere foratura, fresatura, entrambe o un programma G-code e il file del telefono; gli utensili del file con il loro conteggio (e il diametro quando il file lo dichiara), disattivare utensili, iniziare da uno; mettere in pausa, riprendere, annullare e riprendere un lavoro fermato (di qualsiasi tipo) da dove si è fermato, con il file conservato dal server; leggere l'interruttore delle righe `$` di GRBL della macchina. Il server non ha un elenco di file, quindi il file viene inviato dal telefono. Scritto, mai eseguito contro un server o una macchina reali. |
| Visore 2D e 3D | 🟠 | La scheda, i fori (un anello finché non sono forati), i percorsi (tenui finché non sono tagliati) e la testa, in 2D e in una vista isometrica che ruota con il dito; la posizione della testa è una stima dai passi fatti. Provato come geometria, mai visto su un telefono. |
| Telecamera della testa | 🟠 | La telecamera della testa di foratura (EH3D-NODE-BUNGARD-AUX) in MJPEG tramite il server, i suoi giri, la temperatura e l'uscita di protezione, e una misura del tavolo con il laser. Scritto, mai eseguito con un nodo reale. |
| Aggiornamenti | 🟠 | Guarda l'ultima release di questo progetto su GitHub, scarica l'APK, verifica pacchetto, versione e firma e lo consegna all'installer di Android; non installa mai da sola. Scritto e provato; mai eseguito contro GitHub da un telefono. |
| Uso reale | 🔴 | Non ancora: l'app non è stata eseguita su un telefono, contro un server reale né con una macchina; la prima cosa da provare è accedere all'EH3D-SERVER dell'officina. |
| Documentazione | 🟡 | README in otto lingue, la guida dell'app in otto lingue e le note dell'API del server che usa. |

## 🎯 Panoramica

* Ogni macchina dell'officina con il suo stato, il lavoro, l'avanzamento, i passi fatti e il tempo restante, letti dal server ogni secondo e mezzo; un avviso quando una macchina attende l'operatore o si ferma. Scritto, non eseguito contro un server reale.
* Un pulsante rosso che chiede due volte e annulla il lavoro in corso tramite il server (una macchina o tutte); la conferma dei passi che un lavoro attende. Solo operatori e amministratori. Non ha mai fermato una macchina reale e non sostituisce il suo arresto fisico.
* Scegliere foratura, fresatura, entrambe o un programma G-code e il file del telefono; gli utensili del file con il loro conteggio (e il diametro quando il file lo dichiara), disattivare utensili, iniziare da uno; mettere in pausa, riprendere, annullare e riprendere un lavoro fermato (di qualsiasi tipo) da dove si è fermato, con il file conservato dal server; leggere l'interruttore delle righe `$` di GRBL della macchina. Il server non ha un elenco di file, quindi il file viene inviato dal telefono. Scritto, mai eseguito contro un server o una macchina reali.
* La scheda, i fori (un anello finché non sono forati), i percorsi (tenui finché non sono tagliati) e la testa, in 2D e in una vista isometrica che ruota con il dito; la posizione della testa è una stima dai passi fatti. Provato come geometria, mai visto su un telefono.
* La telecamera della testa di foratura (EH3D-NODE-BUNGARD-AUX) in MJPEG tramite il server, i suoi giri, la temperatura e l'uscita di protezione, e una misura del tavolo con il laser. Scritto, mai eseguito con un nodo reale.
* Guarda l'ultima release di questo progetto su GitHub, scarica l'APK, verifica pacchetto, versione e firma e lo consegna all'installer di Android; non installa mai da sola. Scritto e provato; mai eseguito contro GitHub da un telefono.

La pagina di una macchina ha anche l'anello di LED della testa (spento e tre livelli) e una scheda Controllo qualità: chiede al server di guardare la scheda con la telecamera della testa di foratura, giudica i fori e i canali fresati e mostra il verdetto grande come un cartello (CONFORME, NON CONFORME o non valutabile) con ciò che è stato trovato, il profilo (standard, passo fine, prototipo) e la calibrazione della scala. Le prime schermate (quella iniziale con il marchio, l'attesa mentre si ripristina la sessione e quella di accesso con le sue icone, l'occhio per la password e la nota su ciò che resta sul telefono) seguono l'aspetto delle altre app della suite.

La pagina di una macchina mostra anche il mandrino della macchina (l’originale o quello con inverter), con i suoi limiti e il numero di giri massimo a cui impostare il nodo; un amministratore lo sceglie e sceglie cosa fa il server con un lavoro che chiede di più (rifiutarlo, oppure lasciarlo passare ridotto al massimo del mandrino). Un lavoro elenca gli eccessi con cui è stato lasciato passare, gli utensili disattivati per lui e i fori o i percorsi già fatti che lascia fuori. La pagina di una macchina libera ha anche Prepara lavoro: si sceglie cosa si fa (foratura, fresatura, entrambe o un programma G-code libero) e il file del telefono; il server lo pianifica, e l’app elenca gli utensili del file con il loro conteggio, permette di disattivare utensili o di iniziare da uno (i precedenti nell’ordine del lavoro vengono omessi) e avvia il lavoro dopo una conferma. Con entrambe si creano due lavori e il secondo resta pronto. Un programma G-code si può mettere in pausa, riprendere, annullare e continuare dalla riga in cui si è fermato; mandrino e refrigerante vengono riaccesi solo se si attiva quell’opzione (disattivata per impostazione predefinita).

La scheda della testa di foratura mostra anche cosa dicono i suoi sensori: com’è andata l’ultima lettura del laser (valida, segnale debole, troppa dispersione, fuori intervallo, guasto del sensore) con la distanza, quanto tempo fa è stata presa e l’offset con cui il laser è stato calibrato; se gli impulsi del sensore di giri sono puliti o disturbati e quanti sono stati scartati come rumore; quanti sensori di temperatura il nodo ha trovato sul filo (e un avviso quando indovina soltanto quale leggere); e la versione del nodo. Un nodo che non lo dice non mostra nulla di questo. Le impostazioni dell’immagine della telecamera, la calibrazione del laser e la scelta del sensore di temperatura si fanno nel pannello del nodo: il server non le inoltra ancora.

## 🧩 Ruolo in EH3D

EH3D-ANDROID-CONTROL è lo schermo tascabile dell'officina EH3D: parla solo con EH3D-SERVER, che esegue i lavori sulle macchine (tramite EH3D-NODE-BUNGARD) e inoltra la telecamera della testa di foratura (EH3D-NODE-BUNGARD-AUX). I lavori si preparano in EH3D-STUDIO, BUNGARD-GUI o in questa app con Prepara lavoro; questa app li segue, conferma i passi che attendono e li ferma.

## 🏛️ Architettura

<p align="center">
  <img src="images/architecture.svg" alt="Architettura di EH3D-ANDROID-CONTROL: chi lo usa, che cosa contiene e da che cosa dipende" width="100%">
</p>

Come si incastrano le parti di questo repository: chi lo usa, che cosa contiene e da che cosa dipende. L'immagine è disegnata da `docs/architecture.json` e verificata rispetto al codice.

## 🖼️ Schermate

<p align="center">
  <img src="images/screenshots/app-home.png" alt="app-home" width="30%">
</p>

<p align="center"><em>Le macchine dell'officina: libere, in lavoro e in attesa dell'operatore, con l'arresto di tutte.</em></p>

<p align="center">
  <img src="images/screenshots/app-machine.png" alt="app-machine" width="30%">
</p>

<p align="center"><em>Una macchina: l'arresto, il lavoro in corso e la vista 2D in diretta con la posizione della testa.</em></p>

<p align="center">
  <img src="images/screenshots/app-viewer3d.png" alt="app-viewer3d" width="30%">
</p>

<p align="center"><em>La vista isometrica dello stesso lavoro, ruotata con il dito, e i suoi utensili.</em></p>

<p align="center">
  <img src="images/screenshots/app-head.png" alt="app-head" width="30%">
</p>

<p align="center"><em>La testa di foratura: la telecamera tramite il server, i giri, la temperatura, l'uscita di protezione e la misura del tavolo.</em></p>

<p align="center">
  <img src="images/screenshots/app-jobs.png" alt="app-jobs" width="30%">
</p>

<p align="center"><em>I lavori del server con i loro stati.</em></p>

<p align="center">
  <img src="images/screenshots/app-more.png" alt="app-more" width="30%">
</p>

<p align="center"><em>Altro: la sessione, l'aggiornamento da GitHub, la lingua e l'aiuto.</em></p>

<p align="center"><em>Un disegno delle schermate fatto con le stesse regole e un lavoro di esempio, non una cattura da un telefono: l'app non è stata eseguita su uno.</em></p>

## 🚀 Avvio rapido

Servono il JDK 21 e l'SDK Android (il wrapper Gradle fa il resto) e un EH3D-SERVER in rete.

```bash
build.bat          # tests and debug APK (Windows)  -  ./build.sh on Linux
test.bat           # only the tests
python -I tools/ci_validate.py
```

Aprire l'app, scrivere l'indirizzo del server (e la porta 18200), premere Connetti e accedere. Un server senza utenti chiede il codice mostrato sulla sua console e crea il primo amministratore.

## 🧪 Test

`./gradlew test` esegue i test unitari sulla JVM: i lettori del JSON del server (flotta, lavori, testa, misura), la posizione della testa e la geometria delle due viste, il controllo dell'indirizzo, la conservazione cifrata della sessione, la versione e il cancello della release dell'aggiornamento, e le lingue (ogni frase del sorgente nelle sette tabelle, con gli stessi segnaposto).

## 🏗️ Compilazione e rilascio

La versione vive in `eh3d.project.json` e in `app/build.gradle.kts` (la compilazione si ferma se differiscono) e ogni versione ha la sua voce in [CHANGELOG.md](CHANGELOG.md). L'APK di release è ridotto con R8 e poi firmato con la chiave propria del manutentore, tenuta fuori dal repository; il file di una release è `EH3D-ANDROID-CONTROL-release.apk`, quello che cerca l'aggiornamento da GitHub.

## 📂 Struttura del repository

<p align="center">
  <img src="images/repository-structure.svg" alt="Struttura del repository EH3D-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Versione testuale dell'albero</summary>

```text
EH3D-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts proguard-rules.pro      la compilazione e le regole di riduzione
│   ├── src/main/AndroidManifest.xml             permessi e l'unica attività
│   ├── src/main/assets/i18n/                    i testi in otto lingue
│   ├── src/main/java/es/electrohobby3d/eh3d/    il codice dell'app
│   └── src/test/java/es/electrohobby3d/eh3d/    i test sulla JVM
├── tools/
│   ├── ci_validate.py                           manifesto, versioni, parità dei README
│   └── make_architecture.py make_structure.py   disegnano le due immagini del README
├── docs/                                        le sorgenti delle due immagini
├── gradle/                                      i file del wrapper Gradle
├── gradlew                                      il wrapper Gradle
├── gradlew.bat                                  il wrapper per Windows
├── build.gradle.kts                             i plugin della compilazione
├── settings.gradle.kts                          il nome del progetto
├── gradle.properties                            la memoria della compilazione
├── images/                                      banner e immagini
├── .github/                                     workflow di CI
├── build.bat build.sh                           compilare ed eseguire i test
├── eh3d.project.json                            versione, ruolo, maturità
├── CHANGELOG.md                                 cosa è cambiato in ogni versione
├── SECURITY.md SUPPORT.md                       segnalare una vulnerabilità, chiedere
├── CONTRIBUTING.md                              come contribuire
├── CODE_OF_CONDUCT.md                           il codice di condotta
└── LICENSE                                      la licenza
```

</details>

## 🗺️ Roadmap e limiti noti

**Limiti noti oggi**

* L'app non è stata eseguita su un telefono, contro un server reale né con una macchina.
* La posizione della testa è una stima dai passi fatti; il server non riporta la posizione reale della macchina.
* L'arresto di emergenza passa per il server e la rete: non è un arresto fisico.
* La telecamera non ha mai mostrato un'immagine reale e la misura del tavolo non è mai stata presa con un laser reale.

**Prossimi passi**

* Accedere all'EH3D-SERVER dell'officina e seguire un lavoro reale.
* Mostrare la posizione vera della testa quando il server la riporterà.
* Gli avvisi con l'app chiusa arrivano da un'attività periodica (ogni 15 minuti al meglio, il minimo che Android consente), non in tempo reale; un canale push del server li renderebbe immediati e non esiste ancora.
* Scegliere il file tra quelli conservati sul server invece che dal telefono (il server non ha ancora un elenco di file).

## 🌍 Lingue

Il README e i testi esistono in inglese, spagnolo, francese, italiano, tedesco, cinese, giapponese e portoghese europeo.

## 🔗 Progetti correlati

* [EH3D-SERVER](https://github.com/JuanenRac/EH3D-SERVER): il centro della famiglia EH3D: esegue i lavori sulle macchine e dà a questa app tutto ciò che mostra
* [EH3D-STUDIO](https://github.com/JuanenRac/EH3D-STUDIO): le pagine dove si aggiungono le macchine e si preparano i lavori
* [EH3D-BUNGARD-GUI](https://github.com/JuanenRac/EH3D-BUNGARD-GUI): il programma del computer che prepara i lavori di foratura e fresatura e li esegue
* [EH3D-NODE-BUNGARD-AUX](https://github.com/JuanenRac/EH3D-NODE-BUNGARD-AUX): il nodo della testa di foratura: la telecamera, il laser e i sensori che mostra questa app
* [EH3D-IOS-CONTROL](https://github.com/JuanenRac/EH3D-IOS-CONTROL): le app Android e iPhone sono gemelle: stesse regole e stesse schermate

## 📚 Documentazione

* [L'API del server](https://github.com/JuanenRac/EH3D-SERVER/blob/main/docs/API.md): ciò che questa app legge e chiede.
* [Registro delle modifiche](CHANGELOG.md)

## 🤝 Contribuire e comunità

* Prima di una pull request eseguire `./gradlew test` e `python -I tools/ci_validate.py`: entrambi devono passare. Vedere [CONTRIBUTING.md](CONTRIBUTING.md) e il [codice di condotta](CODE_OF_CONDUCT.md).
* Ogni testo mostrato dall'app è nelle tabelle di `app/src/main/assets/i18n` in tutte le otto lingue; un test le mantiene complete.
* Segnalare una vulnerabilità in privato: [SECURITY.md](SECURITY.md). Domande, idee e segnalazioni: electrohobby3d@gmail.com ([SUPPORT.md](SUPPORT.md)).

## 👤 AUTORE

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LICENZA

Software: GPL-3.0-or-later. Vedi [LICENSE](LICENSE). I progetti hardware della suite usano la CERN OHL-S.
