# 2048 DLV Solver — Design di rinascita

Data: 2026-07-25
Stato: approvato

## Obiettivo

Rimettere in vita il progetto sfruttando DLV: salto grafico, qualità del codice
allineata agli standard attuali, prestazioni, e un solver molto più forte con
DLV come cervello che ragiona su un orizzonte profondo.

## Vincoli decisi

| Ambito | Decisione |
|---|---|
| Ruolo di DLV | Cervello del solver, orizzonte profondo (non ibrido, non solo valutatore) |
| Profondità | Approfondimento iterativo governato dal budget, non orizzonte fisso |
| Budget per mossa | 2-3 s, modalità suggerimento (tasto `S`) |
| Grafica | Swing modernizzato (FlatLaf), non riscrittura JavaFX |
| Runtime ASP | DLV2 nativo arm64 |
| Toolchain | Maven, JDK 25 LTS, nessun jar non mantenuto |
| Percorso solver | Progressivo: profondità → rischio → avversario |

## Risultati dello spike (misurati, non stimati)

È stata scritta e validata un'encoding ASP che simula la meccanica reale di 2048
(scivolamento, merge, 4 direzioni) e pianifica una sequenza di N mosse.
Correttezza verificata a mano su H=1 e H=2 contro il calcolo manuale della board.

DLV2 nativo arm64, configurazione di produzione (`--printonlyoptimum`):

| H | 1 | 3 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|
| board ricca di merge | 34 ms | 139 ms | 376 ms | **1031 ms** | 3659 ms | 11.1 s |
| board di pericolo (15/16) | 31 ms | 159 ms | 493 ms | **797 ms** | 2425 ms | 5.4 s |

Confronto con DLV1 (build 2012, emulato x86-64 su arm64): più lento in
superficie (75 ms a H=1) ma **più veloce in profondità** (1467 ms a H=7 contro
3659 ms). Il sorpasso avviene intorno a H=4. La scelta di DLV2 resta motivata da
manutenzione, esecuzione nativa senza Docker e sintassi ASP-Core-2 standard,
non dalla velocità pura.

**Orizzonte di progetto: approfondimento iterativo**, contro l'orizzonte 1
dell'implementazione di partenza.

> **Corretto in corso d'opera.** Questo documento fissava H=6 sulla base dei
> tempi qui sopra, misurati su **due sole board**. Non era un campione: in
> partita reale H=6 con budget 3 s fallisce il **45%** delle volte (9 timeout su
> 20 pressioni, media 2239 ms). Il solver parte quindi da orizzonte 1 e sale
> finché il budget regge, conservando l'ultimo piano riuscito. Misurato dopo la
> modifica: **0 fallimenti su 30** chiamate, orizzonti raggiunti tipicamente 4-7.
> Partire da 1 chiude anche un difetto latente: il programma ASP pretende una
> mossa legale a ogni passo, quindi vicino al game over un piano lungo può non
> esistere pur esistendo una mossa.

### Il limite strutturale scoperto

Sulla board di pericolo (15 caselle su 16 piene) il solver produce
`nmerge(0)` e `piene(15)` **per ogni orizzonte da 1 a 10**. Dieci mosse di
lookahead non guadagnano nulla.

Causa: il modello non genera le tessere casuali, quindi la board non si riempie
mai e il solver non percepisce alcun pericolo. Dopo 7 mosse reali ci sarebbero 7
tessere in più e quella posizione sarebbe game over, ma il modello dice che è
tutto a posto.

**Conseguenza di design:** la profondità da sola non produce forza. Il lavoro ASP
di valore è la modellazione del rischio (Milestone 2 e 3), non l'aumento di H.

### Vincoli DLV1 vs DLV2 emersi

Hanno forma diversa e vanno ricordati quando si tocca l'encoding:

- DLV1 vieta aggregati ricorsivi con la testa. Poiché `at(T+1)` dipende da
  `at(T)` attraverso i ranghi, i ranghi sono calcolati con **conteggio
  ricorsivo** e non con `#count`. La formulazione è stata mantenuta anche su
  DLV2 perché è già validata.
- DLV1 rifiuta assegnamenti di aggregato con variabile non limitata quando i
  predicati dipendono dal guess disgiuntivo: da qui i predicati di dominio
  (`dom16`, `dom24`, `domE`, `domM`).
- DLV1 non accetta `!=` come guard di aggregato.
- Porting DLV1 → DLV2: `v` → `|`, `[w:l]` → `[w@l, termini discriminanti]`,
  via `#maxint`.
- `-n=1` **non** limita l'enumerazione degli answer set ottimi su DLV2. Il flag
  corretto per ottenerne uno solo è `--printonlyoptimum`. Senza di esso DLV2
  enumera tutti gli ottimi simmetrici e i tempi crollano.

## Architettura

### Motore di gioco unificato

Oggi `Game` contiene due implementazioni quasi identiche della mossa
(`move` che muta la board reale, `moveDLV` che simula su cloni), ciascuna con il
proprio flag di rientranza. Vanno tenute in sincrono a mano: qualunque modifica
alla semantica di merge applicata a una sola delle due fa ragionare l'AI su un
gioco diverso da quello giocato.

Sostituite da un `Board` immutabile con `move(Direction) -> MoveResult`.
La board reale e la simulazione usano lo stesso codice per costruzione.

### Solver in tre pezzi separabili

- `AspEncoder` — board → fatti ASP (`at(0,R,C,E)` con E esponente, valore = 2^E)
- `DlvRunner` — `ProcessBuilder`, timeout, terminazione del processo
- `AnswerSetParser` — output DLV → `Direction`

L'encoding ASP vive in `src/main/resources` come file di testo versionato:
leggibile e diffabile, non più generato da un plugin Xtext morto.

Sparisce l'intera toolchain JDLV: `src/dlv/Solver.jdlv`, `src/dlv/Solver.java`
generato, `lib/DLVWrapper4.jar`, `lib/jdlv_executor.jar`, `jdlv.properties`,
la natura Xtext in `.project`.

### Gestione degli errori

`Game.solve()` oggi interpreta il ritorno `0` sia come "nessuna soluzione" sia
come "muovi in su": quando DLV fallisce il gioco muove in su e sembra funzionare.
Va sostituito con un tipo esplicito: assenza di risposta ⇒ nessuna mossa
eseguita e stato mostrato in UI (solver assente, timeout, errore di parsing).

Il binario DLV2 non è ridistribuibile alla leggera e non è verificabile via TLS
(il certificato di `mat.unical.it` è scaduto il 10/12/2025). Il progetto quindi:

- non committa il binario;
- documenta URL e **SHA-256 atteso**:
  `b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7`
  per `dlv-2.1.2-arm64`;
- verifica il checksum all'avvio e degrada in modo esplicito se manca o non
  corrisponde, invece di fallire in silenzio.

## Milestone

### M0 — Fondamenta
Maven, JDK 25 LTS, `bin/` fuori dal versionamento, rimozione toolchain JDLV,
branch `main`.

### M1 — Solver profondo
Encoding di pianificazione a H configurabile (default 6). `Board` unificato.
Gestione errori esplicita. Il salto da orizzonte 1 a 6.

### M2 — Rischio
Valutazione a **ogni** passo invece che solo sullo stato finale; penalità di
riempimento progressiva; vincolo rigido "mai scendere sotto K caselle libere".
È la milestone che rimuove la cecità dimostrata dallo spike.

> **Corretto in corso d'opera.** La formulazione qui sopra è incompleta e da sola
> non produce nulla: senza spawn l'occupazione lungo il piano è **monotona non
> crescente**, quindi guardare ogni passo non aggiunge informazione e un vincolo
> su `piene(T)` è o sempre soddisfatto o già violato a T=0. Ciò che rende
> entrambi sensati è contare le tessere che il gioco *aggiungerà*: il carico a
> T è `piene(T) + T`. Su quello il vincolo rigido dice una cosa nuova — per non
> affogare bisogna fondere almeno quanto lo spawn riempie — e la penalità
> progressiva discrimina, cosa che sullo stato finale da solo **non** farebbe:
> a orizzonte fisso il costo convesso del riempimento finale è una funzione
> monotona di `piene(TH)`, quindi ordina i piani esattamente come il costo
> lineare che sostituisce. La convessità conta solo perché i passi si sommano.

### M3 — Spawn avversariale
La tessera casuale modellata come avversario che sceglie la casella peggiore.
ASP disgiuntivo vero, orizzonte corto, attivabile separatamente. È ciò in cui
DLV è unico rispetto ad altri solver.

> **Corretto in corso d'opera.** "ASP disgiuntivo vero, Σ²p" era sbagliato. Un
> guess `spawn | nospawn` non dà un avversario: i weak constraint minimizzano su
> tutto l'answer set, mosse e spawn insieme, quindi il solver sceglie il
> piazzamento più comodo e il modello diventa **ottimista** — peggio che non
> avere modello. Il "per ogni spawn" richiede saturazione, che pretende un
> controllo monotono nelle atomiche indovinate; la meccanica di 2048 non lo è in
> nessuna direzione, perché una tessera in più riempie una casella ma può anche
> creare una coppia fondibile. Le mosse dell'avversario sono però al massimo 16:
> si aprono tutte come rami di un albero di gioco, si lascia al giocatore una
> mossa per ramo e si prende il massimo sui rami. È min-max alternato, e resta in
> NP.

### M4 — Grafica
FlatLaf; layout calcolato al posto delle coordinate fisse `215 + c*121`;
animazioni di slide e merge; pannello punteggio; evidenza della mossa scelta e
dello stato del solver.

Il thread di repaint a 60 fps oggi gira a vuoto in busy-loop
(`while(goal){...}` senza attesa) consumando una CPU intera: sostituito da un
timer che si sveglia solo quando c'è qualcosa da disegnare. È il vero guadagno
di performance lato applicazione — il costo del solver è nel processo DLV, non
in Java.

## Testing

**Il test portante:** esistono due implementazioni indipendenti della meccanica
di 2048, quella Java e quella ASP. Un property test genera board casuali e
verifica che producano **la stessa board** per ogni direzione. È ciò che
impedisce alla derivazione ASP di divergere in silenzio dal gioco giocato.

Inoltre:

- `AnswerSetParser` su output reali di DLV, inclusi output malformati e vuoti;
- comportamento con binario DLV assente, con checksum errato e con timeout
  superato;
- casi noti di merge: `[2,2,4,8]` a sinistra dà `[4,4,8]`, il doppio merge
  `[2,2,2,2]` dà `[4,4]` e non `[8]`;
- regressione sui tempi: il budget totale va rispettato a ogni chiamata, e
  20 suggerimenti consecutivi su una partita reale non devono produrre
  fallimenti quando una mossa legale esiste.

## Quirk del codice attuale da correggere

Rilevati leggendo il sorgente, tutti reali:

- `generateDLVMatrix()` applica `moveDLV` **due volte** per su, giù e sinistra
  ma **una sola** per destra: le quattro candidate non nascono in condizioni pari.
- `Game.solve()` caso `0`: `if(!moveUp()) moveDown(); else moveUp();` può
  eseguire la mossa verso l'alto due volte.
- `moveDLV` muta il campo `static highest` mentre simula: valutare mosse
  ipotetiche può portare il gioco in stato `won`.
- `score` e `highest` sono `static` su `Game`.
- `Tile.mergeWith` restituisce `-1` in caso di fallimento e il valore in caso di
  successo: valore sentinella mai controllato dal chiamante.

## Fuori ambito

- Riscrittura JavaFX.
- Autoplay a partite complete per misurare la forza su larga scala (interessante
  ma non richiesto: il budget scelto è la modalità suggerimento).
- Ridistribuzione del binario DLV.
