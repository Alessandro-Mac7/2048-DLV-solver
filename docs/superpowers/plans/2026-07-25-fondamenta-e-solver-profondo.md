# Fondamenta e Solver Profondo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sostituire la toolchain JDLV morta con una pipeline DLV2 controllata dal codice, e portare il solver da orizzonte 1 a orizzonte 6.

**Architecture:** Un `Board` immutabile con la meccanica di 2048 espressa una volta sola (oggi duplicata in `move`/`moveDLV`). Il solver diventa tre pezzi separabili — `AspEncoder` (board → fatti ASP), `DlvRunner` (processo esterno con timeout), `AnswerSetParser` (output → `Direction`) — con il programma ASP come risorsa di testo versionata invece che generato da un plugin Xtext. Un property test confronta la meccanica Java con quella ASP su board casuali: è ciò che impedisce alle due di divergere.

**Tech Stack:** Java 25 LTS, Maven (via wrapper), JUnit 5, DLV2 2.1.2 (I-DLV 1.1.7) come processo esterno.

## Global Constraints

- Java release target: **25** (LTS). Nessun uso di API preview.
- Build: **Maven Wrapper** (`./mvnw`) pinnato nel repo. Nessun Maven di sistema richiesto.
- Package base: `it.mac7.dlv2048`. groupId `it.mac7`, artifactId `dlv2048`.
- **Nessun jar non mantenuto.** Vietato reintrodurre `DLVWrapper4.jar`, `jdlv_executor.jar`, EmbASP.
- Il binario DLV2 **non va committato**. URL: `https://www.mat.unical.it/DLV2/releases/2.1.2/dlv-2.1.2-arm64`. SHA-256 atteso: `b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7`. Il certificato TLS di `mat.unical.it` è scaduto il 10/12/2025, quindi la verifica del checksum è obbligatoria, non opzionale.
- Rappresentazione delle tessere: **esponenti** (`e`), valore reale = `1 << e`, `0` = vuoto. Vale sia in Java sia in ASP.
- Flag DLV2 di produzione: `--silent --printonlyoptimum`. **`-n=1` non limita l'enumerazione degli ottimi** e va evitato.
- Orizzonte di default: **6**. Budget: 2-3 s per mossa.
- Lingua di commenti e stringhe utente: italiano (coerente con l'esistente).

---

### Task 1: Fondamenta Maven e JDK 25

**Files:**
- Create: `pom.xml`, `.gitignore`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Delete: `.classpath`, `.project`, `jdlv.properties`
- Delete (da git, non da disco): `bin/`

**Interfaces:**
- Consumes: niente (primo task)
- Produces: build funzionante con `./mvnw test`; layout sorgenti Maven standard `src/main/java`, `src/test/java`, `src/main/resources`

- [ ] **Step 1: Installare JDK 25 e Maven**

```bash
brew install openjdk@25 maven
/opt/homebrew/opt/openjdk@25/bin/java -version
```

Expected: `openjdk version "25...`

- [ ] **Step 2: Creare `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>it.mac7</groupId>
  <artifactId>dlv2048</artifactId>
  <version>2.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>
  <name>2048 DLV Solver</name>

  <properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.11.4</junit.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.4.2</version>
        <configuration>
          <archive>
            <manifest>
              <mainClass>it.mac7.dlv2048.gui.Launcher</mainClass>
            </manifest>
          </archive>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Creare `.gitignore`**

```
target/
bin/
*.class
.idea/
*.iml
.DS_Store
dlv2
```

- [ ] **Step 4: Generare il Maven Wrapper e togliere i file Eclipse**

```bash
cd /Users/alessandro.macri/Desktop/Workspace/workspace_code/2048-DLV-solver
mvn wrapper:wrapper -Dmaven=3.9.9
git rm -r --cached bin
git rm .classpath .project jdlv.properties
```

- [ ] **Step 5: Spostare i sorgenti nel layout Maven**

```bash
mkdir -p src/main/java/it/mac7/dlv2048 src/main/resources/asp src/test/java/it/mac7/dlv2048
git mv src/core src/main/java/it/mac7/dlv2048/core
git mv src/gui  src/main/java/it/mac7/dlv2048/gui
git rm -r src/dlv
```

Nota: `src/dlv` sparisce del tutto — sia il `.jdlv` sia il `.java` generato. Il solver viene riscritto nel Task 5-7.

- [ ] **Step 6: Aggiornare i `package` e verificare che la build fallisca solo per il solver mancante**

Aggiungere `package it.mac7.dlv2048.core;` / `...gui;` in testa a ogni file spostato e correggere gli `import`. Rimuovere da `Game.java` il campo `solver`, il metodo `solve()`, `generateDLVMatrix()`, `convert()`, `moveDLV()`, `movesAvailiableDLV()`, `cloneTile()`, `countMerged()` e l'import `dlv.Solver` — verranno rimpiazzati. Rimuovere da `GamePanel.keyPressed` il ramo `VK_S`.

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "build: passa a Maven e JDK 25, rimuove la toolchain JDLV

I jar JDLV sono del 2013 e Solver.java era generato da un plugin Xtext:
senza Eclipse non era rigenerabile. Il solver viene riscritto come
pipeline verso DLV2 nei task successivi."
```

---

### Task 2: Direction e Board immutabile

**Files:**
- Create: `src/main/java/it/mac7/dlv2048/core/Direction.java`
- Create: `src/main/java/it/mac7/dlv2048/core/MoveResult.java`
- Create: `src/main/java/it/mac7/dlv2048/core/Board.java`
- Test: `src/test/java/it/mac7/dlv2048/core/BoardTest.java`

**Interfaces:**
- Consumes: layout Maven dal Task 1
- Produces:
  - `enum Direction { UP, DOWN, LEFT, RIGHT }`
  - `record MoveResult(Board board, int gainedScore, boolean moved)`
  - `Board.of(int[] exponents)` — 16 esponenti in row-major
  - `Board.empty()`, `int exponentAt(int r, int c)`, `int valueAt(int r, int c)`
  - `MoveResult move(Direction d)`
  - `List<int[]> emptyCells()`, `boolean hasMoves()`, `int maxExponent()`
  - `Board withTile(int r, int c, int exponent)`

- [ ] **Step 1: Scrivere i test che falliscono**

```java
package it.mac7.dlv2048.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private static Board board(int... exponents) {
        return Board.of(exponents);
    }

    @Test
    void merge_semplice_a_sinistra() {
        // 2 2 4 8  ->  4 4 8 .
        Board b = board(1,1,2,3, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertTrue(r.moved());
        assertArrayEquals(new int[]{2,2,3,0}, riga(r.board(), 0));
        assertEquals(4, r.gainedScore());
    }

    @Test
    void quattro_uguali_danno_due_merge_non_uno() {
        // 2 2 2 2 -> 4 4  (NON 8)
        Board b = board(1,1,1,1, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertArrayEquals(new int[]{2,2,0,0}, riga(r.board(), 0));
        assertEquals(8, r.gainedScore()); // 4 + 4
    }

    @Test
    void una_tessera_non_si_fonde_due_volte() {
        // 4 2 2 . -> 4 4 . .
        Board b = board(2,1,1,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertArrayEquals(new int[]{2,2,0,0}, riga(r.board(), 0));
    }

    @Test
    void mossa_senza_effetto_non_e_una_mossa() {
        Board b = board(1,2,3,4, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        assertFalse(b.move(Direction.LEFT).moved());
    }

    @Test
    void destra_e_speculare_a_sinistra() {
        Board b = board(1,1,2,3, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        assertArrayEquals(new int[]{0,2,2,3}, riga(b.move(Direction.RIGHT).board(), 0));
    }

    @Test
    void la_board_di_partenza_non_viene_modificata() {
        Board b = board(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        b.move(Direction.LEFT);
        assertEquals(1, b.exponentAt(0, 0));
        assertEquals(1, b.exponentAt(0, 1));
    }

    private static int[] riga(Board b, int r) {
        return new int[]{b.exponentAt(r,0), b.exponentAt(r,1),
                         b.exponentAt(r,2), b.exponentAt(r,3)};
    }
}
```

- [ ] **Step 2: Verificare che i test falliscano**

Run: `./mvnw -q test -Dtest=BoardTest`
Expected: FAIL — `Board` non esiste (errore di compilazione)

- [ ] **Step 3: Implementare `Direction` e `MoveResult`**

```java
package it.mac7.dlv2048.core;

/** Le quattro direzioni di gioco. */
public enum Direction {
    UP, DOWN, LEFT, RIGHT;

    /** Sigla usata nei fatti ASP: deve combaciare con dir(u;d;l;r). */
    public char aspCode() {
        return switch (this) {
            case UP -> 'u';
            case DOWN -> 'd';
            case LEFT -> 'l';
            case RIGHT -> 'r';
        };
    }

    public static Direction fromAspCode(char c) {
        return switch (c) {
            case 'u' -> UP;
            case 'd' -> DOWN;
            case 'l' -> LEFT;
            case 'r' -> RIGHT;
            default -> throw new IllegalArgumentException("direzione ASP ignota: " + c);
        };
    }
}
```

```java
package it.mac7.dlv2048.core;

/** Esito di una mossa: board risultante, punti guadagnati, se qualcosa si e' mosso. */
public record MoveResult(Board board, int gainedScore, boolean moved) {}
```

- [ ] **Step 4: Implementare `Board`**

```java
package it.mac7.dlv2048.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Board 4x4 immutabile. Le celle contengono ESPONENTI: 0 = vuota,
 * e > 0 = tessera di valore 1 &lt;&lt; e. Gli esponenti tengono i numeri
 * piccoli e combaciano con la rappresentazione usata nel programma ASP.
 */
public final class Board {

    public static final int SIZE = 4;
    private static final int CELLS = SIZE * SIZE;

    private final int[] cells;

    private Board(int[] cells) {
        this.cells = cells;
    }

    public static Board of(int... exponents) {
        if (exponents.length != CELLS) {
            throw new IllegalArgumentException("attese " + CELLS + " celle, ricevute " + exponents.length);
        }
        return new Board(exponents.clone());
    }

    public static Board empty() {
        return new Board(new int[CELLS]);
    }

    public int exponentAt(int row, int col) {
        return cells[row * SIZE + col];
    }

    public int valueAt(int row, int col) {
        int e = exponentAt(row, col);
        return e == 0 ? 0 : 1 << e;
    }

    public Board withTile(int row, int col, int exponent) {
        int[] copy = cells.clone();
        copy[row * SIZE + col] = exponent;
        return new Board(copy);
    }

    public List<int[]> emptyCells() {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (exponentAt(r, c) == 0) out.add(new int[]{r, c});
            }
        }
        return out;
    }

    public int maxExponent() {
        int max = 0;
        for (int e : cells) max = Math.max(max, e);
        return max;
    }

    public boolean hasMoves() {
        for (Direction d : Direction.values()) {
            if (move(d).moved()) return true;
        }
        return false;
    }

    /**
     * Applica una mossa. La meccanica e' definita QUI e in nessun altro punto:
     * il programma ASP la replica e un property test verifica che coincidano.
     */
    public MoveResult move(Direction d) {
        int[] next = new int[CELLS];
        int gained = 0;

        for (int line = 0; line < SIZE; line++) {
            int[] input = new int[SIZE];
            for (int p = 0; p < SIZE; p++) {
                int[] rc = project(d, line, p);
                input[p] = exponentAt(rc[0], rc[1]);
            }

            int[] slid = new int[SIZE];
            int write = 0;
            int read = 0;
            // compattazione + merge greedy dall'estremo di arrivo
            int[] compact = new int[SIZE];
            int n = 0;
            for (int p = 0; p < SIZE; p++) {
                if (input[p] != 0) compact[n++] = input[p];
            }
            while (read < n) {
                if (read + 1 < n && compact[read] == compact[read + 1]) {
                    int merged = compact[read] + 1;
                    slid[write++] = merged;
                    gained += 1 << merged;
                    read += 2;               // la tessera fusa non si rifonde
                } else {
                    slid[write++] = compact[read];
                    read++;
                }
            }

            for (int p = 0; p < SIZE; p++) {
                int[] rc = project(d, line, p);
                next[rc[0] * SIZE + rc[1]] = slid[p];
            }
        }

        boolean moved = !Arrays.equals(cells, next);
        return new MoveResult(new Board(next), gained, moved);
    }

    /**
     * Mappa (linea, posizione) -> (riga, colonna) per la direzione data.
     * Posizione 0 e' sempre l'estremo verso cui le tessere scivolano.
     * Identica alla relazione lin/5 del programma ASP.
     */
    private static int[] project(Direction d, int line, int pos) {
        return switch (d) {
            case LEFT  -> new int[]{line, pos};
            case RIGHT -> new int[]{line, SIZE - 1 - pos};
            case UP    -> new int[]{pos, line};
            case DOWN  -> new int[]{SIZE - 1 - pos, line};
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Board b && Arrays.equals(cells, b.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = valueAt(r, c);
                sb.append(v == 0 ? "    ." : String.format("%5d", v));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Verificare che i test passino**

Run: `./mvnw -q test -Dtest=BoardTest`
Expected: PASS, 6 test

- [ ] **Step 6: Commit**

```bash
git add src/main/java/it/mac7/dlv2048/core src/test/java/it/mac7/dlv2048/core
git commit -m "feat(core): Board immutabile con la meccanica di 2048 in un solo posto

Sostituisce la coppia move/moveDLV che andava tenuta in sincrono a mano."
```

---

### Task 3: Il programma ASP come risorsa

**Files:**
- Create: `src/main/resources/asp/plan.dlv2`
- Test: `src/test/java/it/mac7/dlv2048/solver/AspResourceTest.java`

**Interfaces:**
- Consumes: niente
- Produces: risorsa classpath `/asp/plan.dlv2`. Attende in input i fatti `at(0,R,C,E).`, `horizon(H).`, `time(0..H).`, `step(0..H-1).`; produce `move(T,D)` con `D` in `{u,d,l,r}`.

- [ ] **Step 1: Creare la risorsa ASP**

Questo encoding è già stato validato: la board risultante combacia col calcolo manuale a H=1 e H=2, e i tempi sono misurati (H=6 ≈ 1 s).

```prolog
% ============================================================
% 2048 come problema di PIANIFICAZIONE in ASP (DLV2 / ASP-Core-2)
% Valori = ESPONENTI: valore reale = 2^E
%
% In ingresso:  at(0,R,C,E).  horizon(H).  time(0..H).  step(0..H-1).
% In uscita:    move(T,D) con D in {u,d,l,r}
%
% NB: i ranghi usano conteggio RICORSIVO e non #count, perche' at(T+1)
% dipende da at(T) attraverso i ranghi e gli aggregati ricorsivi con la
% testa sono vietati. La formulazione e' gia' validata: non "semplificarla"
% rimettendo #count senza rimisurare.
% ============================================================

idx(0..3).
dir(u). dir(d). dir(l). dir(r).

% ---- proiezione cella -> (linea, posizione); P=0 = estremo di arrivo
lin(l,R,C,R,C) :- idx(R), idx(C).
lin(r,R,C,R,P) :- idx(R), idx(C), P=3-C.
lin(u,R,C,C,R) :- idx(R), idx(C).
lin(d,R,C,C,P) :- idx(R), idx(C), P=3-R.

% ---- GUESS: esattamente una mossa per passo
move(T,D) | nomove(T,D) :- step(T), dir(D).
qualche(T) :- move(T,_).
:- step(T), not qualche(T).
:- move(T,D), move(T,D2), D < D2.

% ---- proiezione dello stato sulla linea (solo direzione scelta)
lat(T,D,L,P,E) :- at(T,R,C,E), lin(D,R,C,L,P), move(T,D).
locc(T,D,L,P)  :- lat(T,D,L,P,_).
lline(T,D,L)   :- move(T,D), idx(L).

% ---- rango fra le occupate: conteggio progressivo (no aggregati)
cnt(T,D,L,0,0)   :- lline(T,D,L).
cnt(T,D,L,P1,N1) :- cnt(T,D,L,P,N), locc(T,D,L,P), P1=P+1, P1<=3, N1=N+1.
cnt(T,D,L,P1,N)  :- cnt(T,D,L,P,N), not locc(T,D,L,P), P1=P+1, P1<=3.
lrank(T,D,L,P,N) :- locc(T,D,L,P), cnt(T,D,L,P,N).
comp(T,D,L,K,E)  :- lat(T,D,L,P,E), lrank(T,D,L,P,K).

% ---- merge greedy lungo la linea (stratificato localmente su K)
mrg(T,D,L,K)   :- comp(T,D,L,K,E), K1=K+1, comp(T,D,L,K1,E), not cons(T,D,L,K).
cons(T,D,L,K1) :- mrg(T,D,L,K), K1=K+1.

% ---- sopravvissute dopo il merge
surv(T,D,L,K,E1) :- mrg(T,D,L,K), comp(T,D,L,K,E), E1=E+1.
surv(T,D,L,K,E)  :- comp(T,D,L,K,E), not cons(T,D,L,K), not mrg(T,D,L,K).
soc(T,D,L,K)     :- surv(T,D,L,K,_).

% ---- rango dopo il merge
scnt(T,D,L,0,0)   :- lline(T,D,L).
scnt(T,D,L,K1,N1) :- scnt(T,D,L,K,N), soc(T,D,L,K), K1=K+1, K1<=3, N1=N+1.
scnt(T,D,L,K1,N)  :- scnt(T,D,L,K,N), not soc(T,D,L,K), K1=K+1, K1<=3.
srank(T,D,L,K,N)  :- soc(T,D,L,K), scnt(T,D,L,K,N).

% ---- stato successivo
at(T1,R,C,E) :- surv(T,D,L,K,E), srank(T,D,L,K,J), lin(D,R,C,L,J),
                move(T,D), T1=T+1, time(T1).

% ---- legalita': deve esserci un merge o uno scorrimento
legale(T,D) :- comp(T,D,L,K,E), K1=K+1, comp(T,D,L,K1,E).
legale(T,D) :- locc(T,D,L,P), lrank(T,D,L,P,K), K < P.
:- move(T,D), not legale(T,D).

% ============================================================
% VALUTAZIONE DELLO STATO FINALE
% domini limitati: obbligatori perche' questi aggregati dipendono
% dal guess disgiuntivo
% ============================================================
dom16(0..16).
dom24(0..24).
domE(1..17).
domM(0..32).

occfin(R,C) :- at(TH,R,C,_), horizon(TH).
piene(M)    :- dom16(M), #count{R,C : occfin(R,C)} = M.

maxfin(E) :- domE(E), #max{E1 : at(TH,_,_,E1), horizon(TH)} = E.
inangolo  :- at(TH,0,0,E), horizon(TH), maxfin(E).

% monotonia di riga: coppie adiacenti crescenti (euristica "scala")
disordine(N) :- dom24(N),
                #count{R,C : at(TH,R,C,E), horizon(TH), C1=C+1,
                             at(TH,R,C1,E1), E1>E} = N.

% numero di merge nel piano (proxy economico del punteggio)
nmerge(N) :- domM(N), #count{T,D,L,K : mrg(T,D,L,K)} = N.

% ============================================================
% OTTIMIZZAZIONE (livello piu' alto = piu' importante)
% ============================================================
:~ not inangolo.      [1@4]
:~ piene(M).          [M@3, M]
:~ disordine(N).      [N@2, N]
:~ nmerge(N), C=32-N. [C@1, C]
```

- [ ] **Step 2: Scrivere il test che verifica che la risorsa sia caricabile**

```java
package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class AspResourceTest {

    @Test
    void il_programma_asp_e_sul_classpath_ed_e_completo() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/asp/plan.dlv2")) {
            assertNotNull(in, "risorsa /asp/plan.dlv2 assente");
            String src = new String(in.readAllBytes());
            assertTrue(src.contains("move(T,D) | nomove(T,D)"), "manca il guess");
            assertTrue(src.contains(":~"), "mancano i weak constraint");
            assertFalse(src.contains("#maxint"), "#maxint e' sintassi DLV1");
            assertFalse(src.contains(" v nomove"), "disgiunzione in sintassi DLV1");
        }
    }
}
```

- [ ] **Step 3: Eseguire il test**

Run: `./mvnw -q test -Dtest=AspResourceTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/asp/plan.dlv2 src/test/java/it/mac7/dlv2048/solver/AspResourceTest.java
git commit -m "feat(solver): programma ASP di pianificazione come risorsa versionata

Sostituisce Solver.jdlv, che richiedeva Eclipse e un plugin Xtext per
essere rigenerato. Ora e' testo leggibile e diffabile."
```

---

### Task 4: AnswerSetParser

**Files:**
- Create: `src/main/java/it/mac7/dlv2048/solver/AnswerSetParser.java`
- Test: `src/test/java/it/mac7/dlv2048/solver/AnswerSetParserTest.java`

**Interfaces:**
- Consumes: `Direction` dal Task 2
- Produces: `static Optional<Direction> firstMove(String dlvOutput)` — estrae la mossa al passo 0 dall'answer set

- [ ] **Step 1: Scrivere i test che falliscono**

Nota sui casi limite: l'output DLV contiene sia `move(...)` sia `nomove(...)`, quindi una regex ingenua su `move\(` matcha anche dentro `nomove(`. È un errore reale in cui si inciampa facilmente.

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AnswerSetParserTest {

    @Test
    void estrae_la_mossa_del_passo_zero() {
        String out = "{move(0,r), move(1,l), piene(7)}\nCOST 1@4 7@3\nOPTIMUM";
        assertEquals(Optional.of(Direction.RIGHT), AnswerSetParser.firstMove(out));
    }

    @Test
    void non_confonde_nomove_con_move() {
        String out = "{nomove(0,u), nomove(0,d), nomove(0,l), move(0,r)}";
        assertEquals(Optional.of(Direction.RIGHT), AnswerSetParser.firstMove(out));
    }

    @Test
    void ignora_i_passi_successivi_al_primo() {
        String out = "{move(2,u), move(0,d), move(1,l)}";
        assertEquals(Optional.of(Direction.DOWN), AnswerSetParser.firstMove(out));
    }

    @Test
    void output_vuoto_non_da_mossa() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove(""));
    }

    @Test
    void nessun_answer_set_non_da_mossa() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove("INCOHERENT"));
    }

    @Test
    void output_malformato_non_lancia_eccezioni() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove("move(x,y) move( move(0,"));
    }
}
```

- [ ] **Step 2: Verificare che i test falliscano**

Run: `./mvnw -q test -Dtest=AnswerSetParserTest`
Expected: FAIL — `AnswerSetParser` non esiste

- [ ] **Step 3: Implementare**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estrae la mossa scelta dall'answer set stampato da DLV2. */
public final class AnswerSetParser {

    // (?<![a-z]) impedisce di matchare "move" dentro "nomove"
    private static final Pattern MOVE =
            Pattern.compile("(?<![a-z])move\\((\\d+),([udlr])\\)");

    private AnswerSetParser() {}

    public static Optional<Direction> firstMove(String dlvOutput) {
        if (dlvOutput == null || dlvOutput.isBlank()) return Optional.empty();
        Matcher m = MOVE.matcher(dlvOutput);
        while (m.find()) {
            if ("0".equals(m.group(1))) {
                return Optional.of(Direction.fromAspCode(m.group(2).charAt(0)));
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Verificare che i test passino**

Run: `./mvnw -q test -Dtest=AnswerSetParserTest`
Expected: PASS, 6 test

- [ ] **Step 5: Commit**

```bash
git add src/main/java/it/mac7/dlv2048/solver/AnswerSetParser.java src/test/java/it/mac7/dlv2048/solver/AnswerSetParserTest.java
git commit -m "feat(solver): parser dell'answer set DLV2"
```

---

### Task 5: AspEncoder

**Files:**
- Create: `src/main/java/it/mac7/dlv2048/solver/AspEncoder.java`
- Test: `src/test/java/it/mac7/dlv2048/solver/AspEncoderTest.java`

**Interfaces:**
- Consumes: `Board` dal Task 2
- Produces: `static String facts(Board board, int horizon)` — fatti di istanza da concatenare al programma

- [ ] **Step 1: Scrivere i test che falliscono**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AspEncoderTest {

    @Test
    void emette_un_fatto_per_ogni_cella_occupata() {
        Board b = Board.of(1,0,0,0, 0,2,0,0, 0,0,0,0, 0,0,0,3);
        String f = AspEncoder.facts(b, 6);
        assertTrue(f.contains("at(0,0,0,1)."));
        assertTrue(f.contains("at(0,1,1,2)."));
        assertTrue(f.contains("at(0,3,3,3)."));
    }

    @Test
    void non_emette_fatti_per_le_celle_vuote() {
        Board b = Board.of(1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        String f = AspEncoder.facts(b, 6);
        assertFalse(f.contains(",0)."), "le celle vuote non vanno emesse");
    }

    @Test
    void emette_orizzonte_tempo_e_passi_coerenti() {
        String f = AspEncoder.facts(Board.empty(), 6);
        assertTrue(f.contains("horizon(6)."));
        assertTrue(f.contains("time(0..6)."));
        assertTrue(f.contains("step(0..5)."));
    }

    @Test
    void orizzonte_non_positivo_e_rifiutato() {
        assertThrows(IllegalArgumentException.class,
                () -> AspEncoder.facts(Board.empty(), 0));
    }
}
```

- [ ] **Step 2: Verificare che i test falliscano**

Run: `./mvnw -q test -Dtest=AspEncoderTest`
Expected: FAIL — `AspEncoder` non esiste

- [ ] **Step 3: Implementare**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;

/** Traduce una board nei fatti di istanza attesi da /asp/plan.dlv2. */
public final class AspEncoder {

    private AspEncoder() {}

    public static String facts(Board board, int horizon) {
        if (horizon < 1) {
            throw new IllegalArgumentException("orizzonte deve essere >= 1, ricevuto " + horizon);
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("horizon(").append(horizon).append(").\n");
        sb.append("time(0..").append(horizon).append(").\n");
        sb.append("step(0..").append(horizon - 1).append(").\n");
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int e = board.exponentAt(r, c);
                if (e != 0) {
                    sb.append("at(0,").append(r).append(',').append(c)
                      .append(',').append(e).append(").\n");
                }
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Verificare che i test passino**

Run: `./mvnw -q test -Dtest=AspEncoderTest`
Expected: PASS, 4 test

- [ ] **Step 5: Commit**

```bash
git add src/main/java/it/mac7/dlv2048/solver/AspEncoder.java src/test/java/it/mac7/dlv2048/solver/AspEncoderTest.java
git commit -m "feat(solver): encoder da Board a fatti ASP"
```

---

### Task 6: DlvRunner e localizzazione del binario

**Files:**
- Create: `src/main/java/it/mac7/dlv2048/solver/DlvBinary.java`
- Create: `src/main/java/it/mac7/dlv2048/solver/DlvRunner.java`
- Create: `src/main/java/it/mac7/dlv2048/solver/SolverStatus.java`
- Create: `scripts/fetch-dlv.sh`
- Test: `src/test/java/it/mac7/dlv2048/solver/DlvBinaryTest.java`

**Interfaces:**
- Consumes: niente
- Produces:
  - `enum SolverStatus { OK, BINARIO_ASSENTE, CHECKSUM_ERRATO, TIMEOUT, ERRORE }`
  - `DlvBinary.locate()` → `Optional<Path>`; cerca `$DLV2_HOME/dlv2`, poi `./dlv2`, poi `dlv2` nel PATH
  - `DlvBinary.sha256(Path)` → `String`
  - `DlvBinary.EXPECTED_SHA256`
  - `record DlvResult(SolverStatus status, String stdout)`
  - `DlvRunner.run(Path binary, String program, Duration timeout)` → `DlvResult`

- [ ] **Step 1: Creare lo script di download**

```bash
#!/bin/sh
# Scarica DLV2 2.1.2 per macOS arm64 e ne verifica il checksum.
#
# ATTENZIONE: il certificato TLS di mat.unical.it e' scaduto il 10/12/2025,
# quindi il download usa -k e NON e' verificabile via TLS. La verifica del
# checksum qui sotto e' l'unica garanzia di integrita': non rimuoverla.
set -e
URL="https://www.mat.unical.it/DLV2/releases/2.1.2/dlv-2.1.2-arm64"
EXPECTED="b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7"
OUT="dlv2"

echo "Scarico DLV2 da $URL"
curl -skL -o "$OUT" "$URL"
ACTUAL=$(shasum -a 256 "$OUT" | cut -d' ' -f1)
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "CHECKSUM ERRATO"
  echo "  atteso:   $EXPECTED"
  echo "  ottenuto: $ACTUAL"
  rm -f "$OUT"
  exit 1
fi
chmod +x "$OUT"
echo "OK: $OUT verificato"
```

```bash
chmod +x scripts/fetch-dlv.sh
```

- [ ] **Step 2: Scrivere i test che falliscono**

```java
package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DlvBinaryTest {

    @Test
    void il_checksum_atteso_e_quello_della_release_2_1_2() {
        assertEquals(64, DlvBinary.EXPECTED_SHA256.length());
        assertEquals("b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7",
                DlvBinary.EXPECTED_SHA256);
    }

    @Test
    void calcola_lo_sha256_di_un_file(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("vuoto.bin");
        Files.write(f, new byte[0]);
        // SHA-256 della stringa vuota
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                DlvBinary.sha256(f));
    }

    @Test
    void locate_non_lancia_quando_il_binario_manca() {
        assertDoesNotThrow(DlvBinary::locate);
    }
}
```

- [ ] **Step 3: Verificare che i test falliscano**

Run: `./mvnw -q test -Dtest=DlvBinaryTest`
Expected: FAIL — `DlvBinary` non esiste

- [ ] **Step 4: Implementare `SolverStatus` e `DlvBinary`**

```java
package it.mac7.dlv2048.solver;

/** Esito dell'interazione con il solver esterno, mostrabile in UI. */
public enum SolverStatus {
    OK("ok"),
    BINARIO_ASSENTE("DLV non trovato — esegui scripts/fetch-dlv.sh"),
    CHECKSUM_ERRATO("binario DLV con checksum inatteso"),
    TIMEOUT("DLV ha superato il tempo massimo"),
    NESSUNA_SOLUZIONE("nessuna mossa possibile"),
    ERRORE("errore durante l'esecuzione di DLV");

    private final String messaggio;

    SolverStatus(String messaggio) {
        this.messaggio = messaggio;
    }

    public String messaggio() {
        return messaggio;
    }
}
```

```java
package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Optional;

/** Trova e verifica il binario DLV2. */
public final class DlvBinary {

    public static final String EXPECTED_SHA256 =
            "b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7";

    private DlvBinary() {}

    /** Cerca $DLV2_HOME/dlv2, poi ./dlv2, poi dlv2 nel PATH. */
    public static Optional<Path> locate() {
        String home = System.getenv("DLV2_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home, "dlv2");
            if (Files.isExecutable(p)) return Optional.of(p);
        }
        Path local = Path.of("dlv2").toAbsolutePath();
        if (Files.isExecutable(local)) return Optional.of(local);

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                Path p = Path.of(dir, "dlv2");
                if (Files.isExecutable(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    public static boolean checksumValido(Path binary) {
        try {
            return EXPECTED_SHA256.equals(sha256(binary));
        } catch (IOException e) {
            return false;
        }
    }
}
```

- [ ] **Step 5: Implementare `DlvRunner`**

```java
package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Esegue DLV2 come processo esterno su un programma passato via file temporaneo. */
public final class DlvRunner {

    /** Esito grezzo: stato piu' stdout. */
    public record DlvResult(SolverStatus status, String stdout) {}

    private DlvRunner() {}

    public static DlvResult run(Path binary, String program, Duration timeout) {
        Path tmp = null;
        Process proc = null;
        try {
            tmp = Files.createTempFile("dlv2048-", ".asp");
            Files.writeString(tmp, program, StandardCharsets.UTF_8);

            // --printonlyoptimum e' obbligatorio: senza, DLV2 enumera TUTTI gli
            // answer set ottimi simmetrici e i tempi crollano. -n=1 non basta.
            proc = new ProcessBuilder(
                    binary.toString(),
                    "--silent",
                    "--printonlyoptimum",
                    "--filter=move/2",
                    tmp.toString())
                    .redirectErrorStream(true)
                    .start();

            if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                return new DlvResult(SolverStatus.TIMEOUT, "");
            }
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new DlvResult(SolverStatus.OK, out);

        } catch (IOException e) {
            return new DlvResult(SolverStatus.ERRORE, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DlvResult(SolverStatus.ERRORE, "");
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }
}
```

- [ ] **Step 6: Verificare che i test passino**

Run: `./mvnw -q test -Dtest=DlvBinaryTest`
Expected: PASS, 3 test

- [ ] **Step 7: Commit**

```bash
git add src/main/java/it/mac7/dlv2048/solver scripts/fetch-dlv.sh src/test/java/it/mac7/dlv2048/solver/DlvBinaryTest.java
git commit -m "feat(solver): esecuzione di DLV2 come processo esterno con verifica del binario

Il binario non e' committato e il suo checksum e' verificato: il
certificato TLS di mat.unical.it e' scaduto, quindi il download non e'
verificabile e il checksum e' l'unica garanzia."
```

---

### Task 7: AspSolver e reinnesto nel gioco

**Files:**
- Create: `src/main/java/it/mac7/dlv2048/solver/Solver.java`
- Create: `src/main/java/it/mac7/dlv2048/solver/AspSolver.java`
- Create: `src/main/java/it/mac7/dlv2048/solver/SolverOutcome.java`
- Modify: `src/main/java/it/mac7/dlv2048/core/Game.java`
- Test: `src/test/java/it/mac7/dlv2048/solver/AspSolverTest.java`

**Interfaces:**
- Consumes: `Board`, `Direction`, `AspEncoder`, `DlvRunner`, `DlvBinary`, `AnswerSetParser`
- Produces:
  - `record SolverOutcome(Optional<Direction> move, SolverStatus status, long millis)`
  - `interface Solver { SolverOutcome bestMove(Board board); }`
  - `AspSolver(int horizon, Duration timeout)`; costruttore senza argomenti = orizzonte 6, timeout 3 s
  - `Game.suggerisci()` → `SolverOutcome`

- [ ] **Step 1: Scrivere i test che falliscono**

I test che richiedono il binario si auto-disabilitano se manca, così la suite resta verde su una macchina senza DLV.

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AspSolverTest {

    private static boolean dlvDisponibile() {
        return DlvBinary.locate().isPresent();
    }

    @Test
    void senza_binario_riporta_stato_esplicito_e_nessuna_mossa() {
        AspSolver s = new AspSolver(2, Duration.ofSeconds(3), java.util.Optional.empty());
        SolverOutcome o = s.bestMove(Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0));
        assertTrue(o.move().isEmpty());
        assertEquals(SolverStatus.BINARIO_ASSENTE, o.status());
    }

    @Test
    void trova_la_mossa_su_una_board_con_un_solo_merge_possibile() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // solo la riga 0 ha due tessere uguali affiancate
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        SolverOutcome o = new AspSolver(1, Duration.ofSeconds(5)).bestMove(b);
        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.move().isPresent());
    }

    @Test
    void su_board_bloccata_non_propone_mosse() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // scacchiera alternata: nessuna mossa legale
        Board b = Board.of(1,2,1,2, 2,1,2,1, 1,2,1,2, 2,1,2,1);
        SolverOutcome o = new AspSolver(1, Duration.ofSeconds(5)).bestMove(b);
        assertTrue(o.move().isEmpty());
    }

    @Test
    void rispetta_il_budget_a_orizzonte_sei() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Board b = Board.of(1,1,2,3, 2,2,3,4, 3,3,4,5, 0,0,0,1);
        SolverOutcome o = new AspSolver(6, Duration.ofSeconds(10)).bestMove(b);
        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.millis() < 3000, "H=6 fuori budget: " + o.millis() + " ms");
    }
}
```

- [ ] **Step 2: Verificare che i test falliscano**

Run: `./mvnw -q test -Dtest=AspSolverTest`
Expected: FAIL — `AspSolver` non esiste

- [ ] **Step 3: Implementare `SolverOutcome` e `Solver`**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;

/** Esito di una richiesta al solver: mossa (se c'e'), stato, tempo impiegato. */
public record SolverOutcome(Optional<Direction> move, SolverStatus status, long millis) {

    public static SolverOutcome fallito(SolverStatus status, long millis) {
        return new SolverOutcome(Optional.empty(), status, millis);
    }
}
```

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;

public interface Solver {
    SolverOutcome bestMove(Board board);
}
```

- [ ] **Step 4: Implementare `AspSolver`**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Solver che delega la scelta della mossa a DLV2 su un problema di pianificazione. */
public final class AspSolver implements Solver {

    private static final String RISORSA = "/asp/plan.dlv2";

    private final int horizon;
    private final Duration timeout;
    private final Optional<Path> binary;
    private final String programma;

    public AspSolver() {
        this(6, Duration.ofSeconds(3));
    }

    public AspSolver(int horizon, Duration timeout) {
        this(horizon, timeout, DlvBinary.locate());
    }

    public AspSolver(int horizon, Duration timeout, Optional<Path> binary) {
        this.horizon = horizon;
        this.timeout = timeout;
        this.binary = binary;
        this.programma = caricaProgramma();
    }

    private static String caricaProgramma() {
        try (InputStream in = AspSolver.class.getResourceAsStream(RISORSA)) {
            if (in == null) throw new IllegalStateException("risorsa mancante: " + RISORSA);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere " + RISORSA, e);
        }
    }

    @Override
    public SolverOutcome bestMove(Board board) {
        long t0 = System.nanoTime();
        if (binary.isEmpty()) {
            return SolverOutcome.fallito(SolverStatus.BINARIO_ASSENTE, elapsed(t0));
        }
        Path bin = binary.get();
        if (!DlvBinary.checksumValido(bin)) {
            return SolverOutcome.fallito(SolverStatus.CHECKSUM_ERRATO, elapsed(t0));
        }

        String programmaCompleto = programma + "\n" + AspEncoder.facts(board, horizon);
        DlvRunner.DlvResult res = DlvRunner.run(bin, programmaCompleto, timeout);
        if (res.status() != SolverStatus.OK) {
            return SolverOutcome.fallito(res.status(), elapsed(t0));
        }

        return AnswerSetParser.firstMove(res.stdout())
                .map(d -> new SolverOutcome(Optional.of(d), SolverStatus.OK, elapsed(t0)))
                .orElseGet(() -> SolverOutcome.fallito(SolverStatus.NESSUNA_SOLUZIONE, elapsed(t0)));
    }

    private static long elapsed(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}
```

- [ ] **Step 5: Riscrivere `Game` sopra `Board`**

Sostituire integralmente il corpo di `Game.java`. Spariscono i campi `static`, la matrice `Tile[][]`, il flag di rientranza e il doppio motore di mossa.

```java
package it.mac7.dlv2048.core;

import it.mac7.dlv2048.solver.AspSolver;
import it.mac7.dlv2048.solver.Solver;
import it.mac7.dlv2048.solver.SolverOutcome;
import java.util.List;
import java.util.Random;

/** Stato di una partita. Nessun campo statico: due partite sono indipendenti. */
public final class Game {

    public static final int ESPONENTE_VITTORIA = 11; // 2^11 = 2048

    private final Random rand = new Random();
    private final Solver solver;

    private Board board = Board.empty();
    private GameState stato = GameState.START;
    private int punteggio;

    public Game() {
        this(new AspSolver());
    }

    public Game(Solver solver) {
        this.solver = solver;
    }

    public void inizia() {
        board = Board.empty();
        punteggio = 0;
        stato = GameState.RUNNING;
        aggiungiTesseraCasuale();
        aggiungiTesseraCasuale();
    }

    public boolean muovi(Direction d) {
        if (stato != GameState.RUNNING) return false;
        MoveResult r = board.move(d);
        if (!r.moved()) return false;

        board = r.board();
        punteggio += r.gainedScore();

        if (board.maxExponent() >= ESPONENTE_VITTORIA) {
            stato = GameState.WON;
            return true;
        }
        aggiungiTesseraCasuale();
        if (!board.hasMoves()) stato = GameState.OVER;
        return true;
    }

    /** Chiede a DLV la mossa migliore. Non muove: decide il chiamante. */
    public SolverOutcome suggerisci() {
        return solver.bestMove(board);
    }

    private void aggiungiTesseraCasuale() {
        List<int[]> vuote = board.emptyCells();
        if (vuote.isEmpty()) return;
        int[] cella = vuote.get(rand.nextInt(vuote.size()));
        int esponente = rand.nextInt(10) == 0 ? 2 : 1; // 4 col 10%, altrimenti 2
        board = board.withTile(cella[0], cella[1], esponente);
    }

    public Board board() { return board; }
    public GameState stato() { return stato; }
    public int punteggio() { return punteggio; }
}
```

Rinominare `State.java` in `GameState.java` con costanti maiuscole:

```java
package it.mac7.dlv2048.core;

public enum GameState { START, RUNNING, WON, OVER }
```

Cancellare `Tile.java` e `TileDLV.java`: non servono più.

```bash
git rm src/main/java/it/mac7/dlv2048/core/Tile.java src/main/java/it/mac7/dlv2048/core/TileDLV.java
git mv src/main/java/it/mac7/dlv2048/core/State.java src/main/java/it/mac7/dlv2048/core/GameState.java
```

- [ ] **Step 6: Adeguare la GUI perché compili**

In `GamePanel.java`: sostituire `game.getState()` con `game.stato()`, `State.running` con `GameState.RUNNING` (e analoghi), `game.getTile(r,c)` con `game.board().valueAt(r,c)` (che restituisce `0` se vuota, quindi il ramo "cella vuota" diventa `== 0`), `game.moveUp()` con `game.muovi(Direction.UP)` e simili. Reintrodurre il ramo `VK_S`:

```java
case KeyEvent.VK_S -> {
    if (game.stato() == GameState.RUNNING) {
        SolverOutcome esito = game.suggerisci();
        esito.move().ifPresent(game::muovi);
        ultimoEsitoSolver = esito;   // campo nuovo, mostrato dal Task successivo
    }
}
```

Aggiungere il campo `private SolverOutcome ultimoEsitoSolver;` e, nel `drawGrid`, disegnare `ultimoEsitoSolver.status().messaggio()` quando lo stato non è `OK`. Questo elimina il fallimento silenzioso: prima un errore DLV faceva muovere in su.

- [ ] **Step 7: Verificare build e test**

```bash
./mvnw -q test
```
Expected: BUILD SUCCESS, tutti i test verdi

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(solver): AspSolver a orizzonte 6 e Game ricostruito su Board

Game.solve() interpretava 0 sia come 'nessuna soluzione' sia come
'muovi in su': un fallimento di DLV era indistinguibile da una scelta.
Ora l'esito e' esplicito e mostrato in UI."
```

---

### Task 8: Il property test che tiene insieme Java e ASP

**Files:**
- Test: `src/test/java/it/mac7/dlv2048/solver/MeccanicaCoerenteTest.java`

**Interfaces:**
- Consumes: `Board`, `Direction`, `AspEncoder`, `DlvRunner`, `DlvBinary`

Questo è il test portante del progetto. Esistono due implementazioni indipendenti della meccanica di 2048 — `Board.move` in Java e le regole `surv`/`srank` in ASP. Se divergono, il solver ragiona su un gioco diverso da quello giocato, e nessun altro test se ne accorge.

- [ ] **Step 1: Scrivere il test**

```java
package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.MoveResult;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica che la meccanica ASP e quella Java producano la STESSA board.
 * E' l'unico test che impedisce alle due implementazioni di divergere.
 */
class MeccanicaCoerenteTest {

    private static final Pattern AT =
            Pattern.compile("at\\(1,(\\d),(\\d),(\\d+)\\)");

    @Test
    void asp_e_java_concordano_su_board_casuali() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        // seme fisso: un fallimento deve essere riproducibile
        Random rnd = new Random(20260725L);
        int confronti = 0;

        for (int iter = 0; iter < 40; iter++) {
            Board b = boardCasuale(rnd);
            for (Direction d : Direction.values()) {
                MoveResult atteso = b.move(d);
                if (!atteso.moved()) continue;   // ASP rifiuta le mosse illegali

                Optional<Board> ottenuto = applicaConAsp(bin.get(), b, d);
                assertTrue(ottenuto.isPresent(),
                        "ASP non ha prodotto uno stato per " + d + " su\n" + b);
                assertEquals(atteso.board(), ottenuto.get(),
                        "divergenza su " + d + "\npartenza:\n" + b
                        + "atteso (Java):\n" + atteso.board()
                        + "ottenuto (ASP):\n" + ottenuto.get());
                confronti++;
            }
        }
        assertTrue(confronti > 50, "troppi pochi confronti utili: " + confronti);
    }

    /** Forza ASP a eseguire una direzione specifica e restituisce lo stato a T=1. */
    private static Optional<Board> applicaConAsp(Path bin, Board b, Direction d) throws Exception {
        String programma = new String(MeccanicaCoerenteTest.class
                .getResourceAsStream("/asp/plan.dlv2").readAllBytes());

        // orizzonte 1, direzione imposta, e nessuna ottimizzazione di mezzo
        String istanza = AspEncoder.facts(b, 1)
                + ":- not move(0," + d.aspCode() + ").\n";

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("coerenza-", ".asp");
        java.nio.file.Files.writeString(tmp, programma + "\n" + istanza);

        Process p = new ProcessBuilder(bin.toString(), "--silent",
                "--printonlyoptimum", "--filter=at/4", tmp.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        java.nio.file.Files.deleteIfExists(tmp);

        if (!out.contains("at(1,")) return Optional.empty();

        int[] celle = new int[16];
        Matcher m = AT.matcher(out);
        while (m.find()) {
            int r = Integer.parseInt(m.group(1));
            int c = Integer.parseInt(m.group(2));
            celle[r * 4 + c] = Integer.parseInt(m.group(3));
        }
        return Optional.of(Board.of(celle));
    }

    private static Board boardCasuale(Random rnd) {
        int[] celle = new int[16];
        int quante = 3 + rnd.nextInt(11);
        for (int i = 0; i < quante; i++) {
            int pos = rnd.nextInt(16);
            // esponenti bassi: piu' probabilita' di merge, quindi test piu' severo
            celle[pos] = 1 + rnd.nextInt(4);
        }
        return Board.of(celle);
    }
}
```

- [ ] **Step 2: Eseguire il test**

Run: `./mvnw -q test -Dtest=MeccanicaCoerenteTest`
Expected: PASS. Se fallisce, il messaggio stampa board di partenza, risultato Java e risultato ASP affiancati: la divergenza è leggibile senza debugger.

- [ ] **Step 3: Eseguire l'intera suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/it/mac7/dlv2048/solver/MeccanicaCoerenteTest.java
git commit -m "test: property test di coerenza fra meccanica Java e meccanica ASP

Le due implementazioni sono indipendenti; senza questo test possono
divergere in silenzio e il solver ragionerebbe su un gioco diverso."
```

---

### Task 9: README e verifica finale

**Files:**
- Create: `README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: tutto quanto sopra
- Produces: istruzioni di build ed esecuzione corrette

- [ ] **Step 1: Scrivere il `README.md`**

```markdown
# 2048 DLV Solver

2048 in Java Swing il cui suggerimento (tasto `S`) e' calcolato da **DLV2**,
un solver di Answer Set Programming, che pianifica una sequenza di 6 mosse.

## Requisiti

- JDK 25
- DLV2 2.1.2 (scaricato a parte, vedi sotto)

## Preparazione

```bash
./scripts/fetch-dlv.sh   # scarica DLV2 e ne verifica lo SHA-256
./mvnw package
```

Il binario DLV2 non e' incluso nel repository. Lo script lo scarica da
`mat.unical.it`, il cui certificato TLS e' scaduto il 10/12/2025: il download
non e' verificabile via TLS e la verifica del checksum e' quindi obbligatoria.

## Esecuzione

```bash
java -jar target/dlv2048-2.0.0-SNAPSHOT.jar
```

Frecce per muoversi, `S` per il suggerimento di DLV.

## Test

```bash
./mvnw test
```

I test che richiedono DLV si auto-disabilitano se il binario non c'e'.

## Come funziona il solver

La board viene tradotta in fatti ASP (`at(0,R,C,E)`, esponenti: valore = 2^E) e
concatenata al programma `src/main/resources/asp/plan.dlv2`, che modella la
meccanica di 2048 come problema di pianificazione: indovina una sequenza di
mosse, ne deriva gli stati e ottimizza lo stato finale con weak constraint su
angolo, riempimento, monotonia e numero di merge.

Prestazioni misurate su DLV2 nativo arm64 (H = orizzonte):

| H | 1 | 3 | 5 | 6 | 7 |
|---|---|---|---|---|---|
| tempo | 34 ms | 139 ms | 376 ms | ~1 s | 3.7 s |

### Limite noto

Il modello non genera le tessere casuali. Su board quasi piena il risultato e'
identico da H=1 a H=10: il solver non percepisce il pericolo perche' nel suo
modello la board non si riempie mai. La modellazione del rischio e' il prossimo
passo previsto.
```

- [ ] **Step 2: Aggiornare `CLAUDE.md`**

Il `CLAUDE.md` attuale descrive la toolchain JDLV, che non esiste più. Riscrivere le sezioni "Build and run", "Architecture" e "Editing the ASP program" per riflettere Maven, `Board`, la pipeline `AspEncoder`/`DlvRunner`/`AnswerSetParser` e la risorsa `plan.dlv2`. Mantenere e aggiornare la sezione sui vincoli DLV1/DLV2 e sul flag `--printonlyoptimum`, che restano trappole reali.

- [ ] **Step 3: Verifica finale end-to-end**

```bash
./mvnw clean package
ls -l target/dlv2048-2.0.0-SNAPSHOT.jar
./mvnw test 2>&1 | tail -20
```

Expected: BUILD SUCCESS, jar prodotto, tutti i test verdi.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: README e CLAUDE.md allineati alla nuova toolchain"
```

---

## Cosa resta fuori da questo piano

- **Plan 2 — Grafica:** FlatLaf, layout calcolato al posto di `215 + c*121`, animazioni di slide e merge, sostituzione del busy-loop a 60 fps (che oggi consuma una CPU intera girando a vuoto).
- **Plan 3 — Rischio e avversario:** valutazione a ogni passo, vincolo sulle caselle libere, spawn avversariale.
