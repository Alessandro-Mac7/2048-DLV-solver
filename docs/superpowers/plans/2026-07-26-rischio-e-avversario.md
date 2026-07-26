# Rischio e avversario — Implementation Plan

Data: 2026-07-26

**Obiettivo:** togliere al programma ASP la cecità dimostrata dallo spike — su board
quasi piena il piano è identico da H=1 a H=10 — modellando il *rischio* invece di
aumentare l'orizzonte. Poi modellare la tessera casuale come avversario.

**Riferimento da battere:** 400 mosse guidate dal solver arrivano a tessera 512 e
~6100 punti.

## Vincolo di correttezza

`MeccanicaCoerenteTest` deve restare verde. `lat`, `locc`, `cnt`, `lrank`, `comp`,
`mrg`, `cons`, `surv`, `scnt`, `srank`, `at` sono **meccanica**: si toccano solo se
il cambiamento è dimostrabilmente un'identità. Tutto il resto è strategia.

## Diagnosi che guida il piano

Nel modello attuale l'occupazione della board è **monotona non crescente** lungo il
piano: senza spawn le mosse possono solo far calare il numero di tessere. Quindi:

- valutare a ogni passo, da solo, non produce alcun segnale di rischio;
- un vincolo rigido "mai sotto K libere" su `piene(T)` sarebbe o sempre soddisfatto
  o già violato a T=0, cioè inutile in entrambi i casi.

La cecità non si cura guardando più stati: si cura **contando le tessere che il
gioco vero aggiungerà**. Da qui l'ordine dei passi qui sotto.

---

### Passo 1 — Banco di prova

Senza misura non si distingue un miglioramento da un rumore. Il 2048 ha varianza
alta: servono decine di partite complete, non due.

- `src/test/java/it/mac7/dlv2048/bench/Autoplay.java`, con `main`, **non** un test
  JUnit: la suite normale non deve rallentare.
- Gioca partite intere pilotate da DLV2, a orizzonte fisso (deterministico: niente
  rumore da budget temporale), con fallback a orizzonte inferiore se il piano non
  esiste.
- Parametri: file ASP, numero di partite, orizzonte, seme, thread.
- Statistiche: tessera massima, punteggio, mosse, distribuzione delle tessere
  massime, ms per mossa.
- Confronto sullo **stesso seme** fra programma di riferimento e programma nuovo.

### Passo 2 — M2, rischio

1. `esito(T,R,C,E)` come stato post-mossa e `at(T,...) :- esito(T,...)`: pura
   identità (la garantisce `MeccanicaCoerenteTest`), serve da aggancio al Passo 3.
2. `carico(T,M)` = celle occupate a T **più le tessere che lo spawn avrà aggiunto**
   (una per mossa). È questa somma, non `piene`, a crescere lungo il piano.
3. Penalità di riempimento **progressiva**: una tabella `costoCarico(M,W)` convessa,
   così che passare da 13 a 14 occupate costi molto più che da 5 a 6.
4. Vincolo rigido `minLibere(K)`: mai scendere sotto K libere lungo il piano,
   **attivo solo se la board di partenza ha già quel margine** — imporlo su una
   board già stretta renderebbe il programma incoerente proprio quando serve una
   mossa.
5. Valutazione a ogni passo: la somma dei costi di riempimento su tutti i T, non
   solo su `horizon(TH)`.

Misura: Autoplay, stesso seme, riferimento contro nuovo.

### Passo 3 — M3, spawn avversariale

Sovrapposizione additiva `src/main/resources/asp/adversary.dlv2`, concatenata al
programma base solo quando la modalità è attiva: costo zero quando è spenta.

La tessera casuale diventa una scelta di un avversario che sceglie la casella
peggiore. Il punto aperto da risolvere con la misura, non con l'assunzione, è
**come** ottenere il "peggiore": un guess disgiuntivo semplice viene *minimizzato*
insieme al resto dai weak constraint, quindi darebbe un avversario **complice**,
non ostile. Le due strade da valutare:

- enumerare le alternative dell'avversario come rami dell'albero di gioco e
  prendere il massimo con un aggregato (`#max`), con la mossa del giocatore
  indovinata per ramo: è min-max esatto e alternato;
- saturazione (∃ piano ∀ spawn): l'unica tecnica che dà davvero Σ²p, ma richiede
  che il controllo sia monotono nelle atomiche indovinate, e la meccanica di 2048
  usa negazione ovunque.

Misurare l'orizzonte che regge e la forza di gioco. Se non migliora, dirlo con i
numeri.

### Passo 4 — Verifica finale

Suite completa con `DLV2_HOME` impostato, e aggiornamento di README e design con i
numeri realmente misurati.
