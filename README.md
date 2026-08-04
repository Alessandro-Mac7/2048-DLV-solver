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
mosse, ne deriva gli stati e li ottimizza con weak constraint su riempimento,
posizione delle tessere e merge (vedi "Come il modello valuta una posizione").

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

#### Quanto serve davvero: misurato, e non conclusivo

Partite complete giocate dal solver con `bench/Autoplay`, confronto appaiato
sugli stessi semi contro il modello precedente (che valutava solo lo stato
finale):

| orizzonte fisso | coppie | differenza media | t |
|---|---|---|---|
| 2 | 48 | +74 punti | 0,14 |
| 4 | 12 | +2825 punti | 2,31 |

**A orizzonte 2 il modello di rischio non serve**, e questo e' stabilito: 48
partite appaiate, intervallo di confidenza `[-973, +1121]`, vittorie 25 su 48
contro le 24 attese dal caso. Ha senso: un termine che conta le tessere
aggiunte lungo il piano non ha nulla da contare se il piano e' di due mosse.

**A orizzonte 4 l'evidenza punta in positivo ma non e' stabilita.** La soglia di
significativita' al 5% con 11 gradi di liberta' e' 2,20 e il valore misurato e'
2,31: passa per un pelo. Soprattutto, i due lotti che lo compongono sono
disomogenei — il primo (8 coppie) da' +4457, il secondo (4 coppie) da' +60. Piu'
disomogeneita' e significativita' marginale insieme sono il profilo tipico di un
risultato che non si replica. Servono piu' partite prima di considerarlo
acquisito.

Il costo non e' un problema: 364 ms per chiamata contro 360 ms del modello
precedente, cioe' invariato.

Quello che si puo' affermare senza riserve e' che il modello e' piu' **corretto**
— vede il riempimento futuro, che prima era invisibile a qualunque profondita' —
non che sia piu' **forte**.

### Come il modello valuta una posizione

Tre livelli lessicografici, dal piu' importante:

| livello | termine | peso |
|---|---|---|
| 3 | **carico**: riempimento reale piu' le tessere che il gioco aggiungera' | `M^2` per passo |
| 2 | **monotonia**: coppie adiacenti che crescono allontanandosi da (0,0) | 4 per coppia |
| 2 | **levigatezza**: somma dei salti di esponente fra caselle adiacenti | 1 per unita' di salto |
| 2 | **ancoraggio**: distanza di Manhattan del massimo da (0,0) | 3 per casella |
| 1 | **merge**: somma degli esponenti fusi | complemento a 576 |

I livelli lessicografici sono uno strumento brutale — il livello sotto conta
solo a parita' esatta di quello sopra — e con quattro livelli un guadagno di un
punto di riempimento batteva qualunque miglioramento di monotonia. Riempimento e
merge restano su livelli propri perche' dicono cose di natura diversa: riempirsi
e' fatale, i merge sono il guadagno e devono decidere solo a parita' di
posizione. I criteri **posizionali** invece descrivono tutti la stessa cosa da
angoli diversi, quanto la board e' pettinata verso l'angolo, e ordinarli fra loro
sarebbe arbitrario: stanno in un solo livello con pesi relativi, come fanno gli
AI forti di 2048, che valutano una somma pesata e non una gerarchia.

Due dettagli costati misure e non ovvi dal codice:

- Il **premio dei merge** e' scritto come `18*(32-N)` piu' la somma di `(18-E)`
  su ogni merge. E' algebricamente `576 - somma degli esponenti fusi`, ma la
  versione diretta con `#sum{E,T,D,L,K : mrg(...), comp(...,E)}` costa **17,5 s
  contro 5,4 s** sulla stessa board a orizzonte 6, perche' l'aggregato costringe
  il grounder a istanziare anche l'esponente. Il fattore 18 sul conteggio non e'
  una taratura: senza, ogni merge in piu' aggiungerebbe `18-E > 0` al costo e il
  modello preferirebbe non fondere affatto.
- L'**ancoraggio** e' graduato e valutato a ogni passo. Il criterio binario
  precedente, valutato solo a fine piano, non costava nulla ai piani che
  scacciavano il massimo dall'angolo a meta' strada; metterlo a ogni passo al
  livello piu' alto invece schiacciava tutto il resto (misurato in precedenza: a
  H=3 la media crollava da ~5300 a ~3200). Serviva graduarlo **e** abbassarlo.

### La restrizione del ventaglio

Il programma non gioca mai "giu'" — la direzione che stacca il massimo
dall'angolo di ancoraggio — quando esiste un'alternativa legale. Lo spazio di
ricerca passa da `4^H` a `3^H`.

Il tempo di una singola chiamata a DLV2, su due board di meta' partita:

| board | riferimento | valutazione nuova | + restrizione | + serpente |
|---|---|---|---|---|
| A, orizzonte 6 | 2,6 s | 5,4 s | **1,9 s** | 2,2 s |
| B, orizzonte 6 | 3,1 s | 12,6 s | **2,8 s** | 3,5 s |
| A, orizzonte 7 | 7,9 s | 15,9 s | **3,4 s** | 4,0 s |
| A, orizzonte 8 | 23,9 s | - | **5,5 s** | 6,5 s |
| A, orizzonte 9 | 39,8 s | - | **9,1 s** | - |
| A, orizzonte 10 | 113,8 s | - | **12,7 s** | - |

I quattro criteri posizionali in piu' costano da soli un fattore 2-4; la
restrizione lo restituisce tutto e lascia il programma **piu' veloce** del
riferimento, e il margine cresce con l'orizzonte: 1,4x a 6, 2,3x a 7, 4,3x a 8,
8,9x a 10. E' il comportamento atteso da `4^H` contro `3^H`, dove il rapporto e'
`(4/3)^H`.

Conta piu' di quanto sembri: il solver in esercizio non usa un orizzonte fisso
ma un budget di 3 secondi, quindi un modello piu' caro non gioca peggio a parita'
di profondita', gioca **meno profondo**. Su questa board, entro 3 secondi, il
riferimento chiude l'orizzonte 6 e non il 7; il modello nuovo chiude il 7.

La proprieta' che non e' negoziabile: dove una mossa legale esiste, il programma
deve restare coerente. Due cose la garantiscono, e nessuna delle due e' ovvia.

- `legale/2` **non e' utilizzabile** per sapere se un'altra direzione e'
  giocabile: passa da `lat/5`, che ha `move(T,D)` nel corpo, quindi esiste solo
  per la direzione indovinata. Serve un test indipendente dal guess, ed e'
  `altraLegale/1`.
- Quel test deve sotto-approssimare o essere esatto. Sotto-approssimare fa solo
  perdere potatura; **sovra-approssimare** vieterebbe "giu'" credendo che esista
  un'alternativa inesistente, e renderebbe il programma incoerente proprio dove
  serve muovere. `altraLegale/1` e' esatto e vale "sinistra oppure su sono
  legali": se una coppia fondibile e' piu' lontana di una casella c'e' per forza
  un buco fra le due, e allora la regola sullo scorrimento ha gia' concluso.

E' lo stesso errore in cui era caduto l'albero avversariale, dove `step/1` era
diventato derivato dentro una componente con negazione ricorsiva e DLV2
rispondeva "nessuna mossa" su board con quattro mosse legali. Qui `step/1` resta
un fatto del chiamante, e `VentaglioRistrettoTest` verifica su oltre cento board
vive che una mossa applicabile arrivi sempre.

#### Quanto e' servito: il costo si', la forza no

Partite complete a orizzonte **fisso 4**, appaiate sugli stessi semi contro il
programma di `main`. Due lotti da 12, su semi indipendenti.

| lotto | coppie | riferimento | nuovo | differenza | t appaiato |
|---|---|---|---|---|---|
| semi 4001-4012 | 12 | 5362 | 8733 | **+3371** | **3,05** |
| semi 7001-7012 | 12 | 6885 | 6977 | +92 | 0,09 |
| i due insieme | 24 | 6123 | 7855 | +1732 | 2,09 |

Il primo lotto sembra una vittoria netta: t=3,05 contro una soglia di 2,20, nove
vittorie su dodici, quattro partite arrivate a 1024 contro zero. **Sul secondo
lotto non si riproduce nulla**: +92 punti, t=0,09, sei vittorie su dodici. I due
insieme danno t=2,09 contro una soglia di 2,07 e un intervallo di confidenza al
95% di `[+15, +3448]`, cioe' un estremo inferiore indistinguibile da zero.

Tessera massima sulle 24 coppie: riferimento `{128:1, 256:5, 512:16, 1024:2}`,
nuovo `{256:4, 512:14, 1024:6}`. Le coppie discordanti sul 1024 sono 5 a 1 in
favore del nuovo, che a una binomiale esatta bilaterale da' p=0,22. Nemmeno
questo e' stabilito. **Il 2048 non e' mai stato raggiunto da nessuno dei due.**

La conclusione onesta e' che **il guadagno di punteggio non e' dimostrato**. Un
lotto da 12 partite ha una deviazione standard delle differenze intorno a 4000
punti: con quel rumore, un lotto che da' +3371 e uno che da' +92 sono
perfettamente compatibili con un miglioramento vero di poche centinaia di punti,
e altrettanto compatibili con nessun miglioramento. Servirebbero dell'ordine di
un centinaio di partite appaiate per separare le due ipotesi.

Quello che invece e' stabilito, perche' non e' una statistica di partita ma una
misura deterministica, e' il **costo**: il programma nuovo risponde da 1,4 a 8,9
volte piu' in fretta a seconda dell'orizzonte. In esercizio, dove il parametro
governato e' il budget di 3 secondi e non la profondita', quel margine si spende
in un livello di ricerca in piu'.

Due note operative. `senzaPiano` e' rimasto **0 su 48 partite** e circa 26 000
chiamate: la restrizione del ventaglio non ha mai lasciato il solver senza mossa.
I `errori` (timeout secco a 30 s del banco di prova, che usa orizzonte fisso e
non l'approfondimento iterativo) passano da 0 a 3-4 per lotto, ed e' una
conseguenza del fatto che il modello nuovo arriva a board con 1024 e 2048 in
costruzione, che sono piu' care da risolvere: il riferimento non ci arriva mai.

`MeccanicaCoerenteTest` inchioda a una a una tutte e quattro le direzioni per
confrontare la board Java con quella ASP: la restrizione rifiuterebbe proprio la
direzione inchiodata. Il fatto `meccanicaNuda`, che nessuno asserisce in
esercizio, la scavalca — quel test riguarda scivolamento e fusione, non
strategia.

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
