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

---

## Cosa e' emerso davvero (aggiornato in corso d'opera)

### La metodologia va difesa prima dei risultati

Il seme fissa la sequenza casuale, non la partita: **DLV2 sceglie arbitrariamente
fra piani di costo uguale**, quindi due programmi che differiscono per una riga
irrilevante possono divergere alla prima parita' e da li' in poi giocare due
partite diverse. Su una distribuzione con deviazione standard di ~2200 punti, due
o tre partite non dicono nulla. Tutti i confronti qui sotto sono su 24 partite a
semi appaiati; le conclusioni prese su campioni piccoli, durante il lavoro, si
sono rivelate sbagliate almeno due volte.

### Errori misurati, non ipotizzati

1. **Angolo a ogni passo.** `:~ ... not inangolo(T). [1@4, T]` somma un punto per
   ogni passo in cui il massimo non e' nell'angolo. A livello 4 quel termine
   schiaccia lessicograficamente tutto il resto, e la sua risoluzione cresce con
   l'orizzonte. Un criterio binario a priorita' massima va tenuto binario.
2. **Penalita' di riempimento troncata.** `max(0,M-4)^2` vale zero fino a quattro
   caselle occupate: su board larga il livello 3 diventa piatto, sparisce la
   pressione a fondere e il solver si limita a tenere le righe in ordine. 2780
   punti contro 5207 del riferimento, otto partite appaiate. La penalita' e'
   diventata `M^2`, che cresce ovunque.
3. **Pesi negativi.** `:~ p(N), C=8-N. [C@1]` con `N` su un dominio che arriva a
   32 produce pesi negativi, e DLV2 riporta un costo che non e' quello scritto
   (305 invece di 5, misurato). Ogni `C=K-N` vuole un dominio di `N` che si ferma
   a `K`.

### Perche' l'avversario non e' un guess disgiuntivo

Era il punto di partenza previsto e non funziona: i weak constraint minimizzano su
tutto l'answer set, mosse e spawn insieme, quindi un `spawn | nospawn` darebbe un
avversario **complice**, non ostile — un modello ottimista, peggio che nessun
modello. Il "per ogni spawn" richiede saturazione, che pretende un controllo
monotono nelle atomiche indovinate; la meccanica di 2048 non lo e' in nessuna
direzione, perche' una tessera in piu' riempie una casella ma puo' anche creare
una coppia fondibile. Le mosse dell'avversario sono pero' al massimo 16: si aprono
tutte come rami, si lascia al giocatore una mossa per ramo e si prende il `#max`.
E' min-max alternato esatto, e resta in NP — la vetrina Σ²p annunciata nel design
non c'era.
