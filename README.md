# 2048 DLV Solver

2048 in Java Swing il cui suggerimento (tasto `S`) e' calcolato da **DLV2**, un
solver di Answer Set Programming invocato come processo esterno.

## Requisiti

- JDK 25
- DLV2 2.1.2 (scaricato a parte, vedi sotto)

## Preparazione

```bash
./scripts/fetch-dlv.sh   # scarica DLV2 e ne verifica lo SHA-256
```

Il binario DLV2 non e' incluso nel repository. Lo script lo scarica da
`mat.unical.it`, il cui certificato TLS e' scaduto il 10/12/2025: il download
avviene senza verifica TLS (`curl -k`), quindi il controllo del checksum
SHA-256 nello script e' l'unica garanzia di integrita' del binario e non va
rimosso ne' aggirato.

A runtime il binario viene cercato in `$DLV2_HOME/dlv2`, poi in `./dlv2`, poi
nel `PATH` (vedi `DlvBinary.locate`). Se `fetch-dlv.sh` e' stato lanciato dalla
radice del repository non serve impostare nulla; altrimenti esporta
`DLV2_HOME` con la directory che contiene `dlv2`.

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw clean package
```

`openjdk@25` e' installato "keg-only" da Homebrew su macOS (non viene linkato
in `/opt/homebrew/bin`), quindi `JAVA_HOME` va impostato esplicitamente: senza,
Maven usa il JDK di sistema e la build fallisce con `release version 25 not
supported`.

## Esecuzione

```bash
java -jar target/dlv2048-2.0.0-SNAPSHOT.jar
```

Frecce per muoversi, `S` per il suggerimento di DLV.

## Test

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DLV2_HOME=/percorso/con/dlv2 ./mvnw test
```

I test che richiedono DLV2 si auto-disabilitano (skip, non fallimento) se il
binario non e' raggiungibile tramite `DlvBinary.locate`.

## Banco di prova

`src/test/java/it/mac7/dlv2048/bench/Autoplay.java` fa giocare partite complete
pilotate dal solver e riporta punteggio, mosse e tessera massima aggregati. Non
e' un test JUnit e non gira con la suite: una partita costa minuti.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -q test-compile
DLV2_HOME=/percorso/con/dlv2 /opt/homebrew/opt/openjdk@25/bin/java \
    -cp target/classes:target/test-classes \
    it.mac7.dlv2048.bench.Autoplay --partite=24 --orizzonte=2
```

Opzioni: `--asp=<file>` per usare un altro programma ASP (per confrontare una
modifica con il riferimento estratto da `git show`), `--avversario` per il
modello di M3, `--seme`, `--thread`.

Serve a rispondere all'unica domanda che conta su un cambiamento di strategia —
il solver gioca meglio o no — e la risposta richiede **almeno una ventina di
partite**. Il seme fissa la sequenza casuale ma non la partita: DLV2 sceglie
arbitrariamente fra piani di costo uguale, quindi due programmi che differiscono
per una riga irrilevante divergono alla prima parita'. Con una deviazione
standard sui punteggi intorno al 40% della media, tre partite appaiate non
distinguono un modello migliore dal rumore.

## Come funziona il solver

La board viene tradotta in fatti ASP (`at(0,R,C,E)`, esponenti: valore = 2^E) e
concatenata al programma `src/main/resources/asp/plan.dlv2`, che modella la
meccanica di 2048 come problema di pianificazione: indovina una sequenza di
mosse, ne deriva gli stati e li ottimizza con weak constraint su angolo,
riempimento, monotonia e numero di merge.

Il solver **non** usa un orizzonte fisso: usa l'**approfondimento iterativo**.
Parte da orizzonte 1 e sale di uno alla volta, conservando sempre l'ultimo
piano completato con successo; si ferma quando il budget di tempo residuo non
basta piu' per un altro livello e restituisce la prima mossa del piano piu'
profondo raggiunto. Il budget di default e' di 3 secondi.

Un orizzonte fisso era stato provato per primo (vedi storia del branch) e
scartato: a orizzonte 6 falliva (timeout secco) circa il 45% delle volte in
partita reale, perche' il costo di DLV2 dipende da quanto e' piena la board e
non e' governabile dall'esterno. Con l'approfondimento iterativo il parametro
governato e' il budget totale, non l'orizzonte: misurato su 30 chiamate in
partita reale, 0 fallimenti, orizzonte tipicamente raggiunto fra 4 e 7, tempo
medio di risposta ~2,2 s.

Se a orizzonte 1 non esiste alcuna mossa legale il solver risponde "nessuna
soluzione". Se DLV2 termina in modo anomalo (codice di uscita diverso da
quello atteso, errore di I/O) il solver restituisce un errore distinto,
separato dal caso "nessuna soluzione": i due casi non vanno confusi, uno e'
fine partita e l'altro e' un guasto del solver.

### Come il modello vede il rischio

Il programma non genera le tessere casuali, quindi nella sua simulazione
l'occupazione della board e' **monotona non crescente**: le mosse possono solo
far calare il numero di tessere. Su una board quasi piena il piano risultava
identico da orizzonte 1 a orizzonte 10, perche' il modello non vedeva alcun
pericolo a nessuna profondita'.

La cura non e' guardare piu' stati ma **contare le tessere che il gioco
aggiungera'**: il carico al passo T e' `piene(T) + T`, cioe' le occupate piu'
una tessera per mossa gia' giocata. Su quel numero il programma applica una
penalita' quadratica sommata su tutti i passi e un vincolo rigido "mai sotto K
caselle libere" (`minLibere`, oggi 2), che si attiva solo se la board di
partenza ha gia' quel margine — imporlo su una board stretta renderebbe il
programma incoerente proprio quando serve una mossa.

Il vincolo dice una cosa che nessun altro termine diceva: siccome
`piene(T) = piene(0) - merge fatti finora`, richiedere un carico basso equivale
a richiedere **almeno tanti merge quanti lo spawn riempie**.

### Modello avversariale (opzionale)

`src/main/resources/asp/adversary.dlv2` si concatena al programma base e
trasforma la tessera casuale in un avversario che sceglie la casella peggiore.
Non e' attivo di default: si abilita con `new AspSolver(h, budget, true)` o con
`--avversario` nel banco di prova.

Il tempo smette di essere una catena e diventa un albero di gioco: ogni mossa
apre fino a 16 rami, uno per casella libera, e il giocatore ha una mossa **per
ramo**, cosi' la sua reazione dipende dalla tessera come nel gioco vero. Il
costo di un piano e' il peggio sui rami, non la media.

L'avversario e' **enumerato, non indovinato**, e il motivo non e' ovvio: un
guess `spawn | nospawn` verrebbe minimizzato insieme al resto dai weak
constraint, cioe' darebbe un avversario complice. Un "per ogni spawn" in ASP si
ottiene solo per saturazione, che pretende un controllo monotono nelle atomiche
indovinate — e la meccanica di 2048 non lo e', perche' una tessera in piu'
riempie una casella ma puo' anche creare una coppia fondibile. Con 16
alternative conviene aprirle tutte.
