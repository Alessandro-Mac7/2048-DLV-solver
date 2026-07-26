# Rafforzare le euristiche di valutazione del piano ASP

Stato di partenza: quattro termini in livelli lessicografici rigidi
(`inangolo`@4, `carico`@3, `disordine`@2, `nmerge`@1). Il solver arriva a 512
quasi sempre, a 1024 raramente, mai a 2048.

## Cosa cambia, e perche'

1. **Monotonia verticale.** `disordine` conta solo le coppie orizzontali: le
   colonne non sono controllate da nessuna regola. E' una lacuna, non un
   miglioramento.
2. **Levigatezza.** Somma di `|E-E1|` sulle coppie adiacenti. Oggi il modello
   non distingue un 2 accanto a un 1024 da un 512 accanto a un 1024.
3. **Ancoraggio graduato e a ogni passo.** `inangolo` e' binario e valutato solo
   a `horizon(TH)`: i piani che scacciano il massimo dall'angolo a meta' strada
   non pagano nulla. Diventa la distanza di Manhattan del massimo dall'angolo
   (0,0), pesata a ogni passo.
4. **Merge pesati per valore.** `nmerge` conta i merge ignorandone il valore.
5. **Matrice a serpente.** Peso per casella decrescente lungo il percorso
   serpentino dall'angolo. La piu' forte conosciuta, ma va tarata: ultima.
6. **Restrizione del ventaglio.** Vietare la direzione che stacca il massimo
   dall'angolo porta lo spazio di ricerca da `4^H` a `3^H`.

## Struttura dei livelli

Si passa da quattro livelli rigidi a tre:

- `@3` **carico**: riempirsi e' fatale e la penalita' e' gia' convessa, quindi
  resta sopra tutto il resto.
- `@2` **posizionale**, somma pesata: monotonia, levigatezza, distanza
  dall'angolo, serpente. Sono criteri che descrivono la stessa cosa da angoli
  diversi e ordinarli lessicograficamente e' arbitrario: un guadagno
  infinitesimo di monotonia non deve battere qualunque miglioramento di
  levigatezza.
- `@1` **merge pesati**.

`adversary.dlv2` usa i livelli 5 e 6 e resta sopra: non va toccato.

## Proprieta' di sicurezza della restrizione (non negoziabile)

La restrizione non deve MAI rendere il programma incoerente dove una mossa
legale esiste. E' l'errore gia' commesso dall'albero avversariale.

- `legale/2` NON e' utilizzabile: dipende da `move(T,D)`, quindi esiste solo per
  la direzione indovinata. Serve un test di legalita' indipendente dal guess.
- Il test deve **sotto-approssimare o essere esatto**. Sotto-approssimare fa
  perdere potatura, sovra-approssimare rende il programma incoerente.
- `step/1` resta un fatto del chiamante: la componente con negazione non deve
  toccarlo.
- Verifica esplicita con un test JUnit su board con mosse legali.

## Gruppi e misura

Ogni gruppo si misura appaiato sugli stessi semi contro `main`
(`git show main:src/main/resources/asp/plan.dlv2`), orizzonte 4, e si dichiara
il **t appaiato**, non la sola media. Con n=12 e ds delle differenze ~3600
servono ~2500 punti per la significativita' al 5%.

- [x] G1: monotonia verticale, livelli invariati.
- [x] G2: G1 + ristrutturazione livelli + levigatezza + ancoraggio graduato.
- [x] G3: G2 + merge pesati.
- [x] G4: G3 + restrizione del ventaglio (+ test di coerenza).
- [x] G5: G4 + serpente.
- [ ] Conferma della configurazione scelta su un **seme indipendente**.
- [ ] Suite completa con `DLV2_HOME`, README aggiornato con i numeri veri,
      compresi quelli negativi.

`MeccanicaCoerenteTest` deve continuare a passare a ogni gruppo: le regole di
scivolamento e fusione sono meccanica e non si toccano.
