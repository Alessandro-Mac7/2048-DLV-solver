#!/bin/sh
# Scarica DLV2 2.1.2 per macOS arm64 e ne verifica il checksum.
#
# ATTENZIONE: il certificato TLS di mat.unical.it e' scaduto il 10/12/2025,
# quindi il download usa -k e NON e' verificabile via TLS. La verifica del
# checksum qui sotto e' l'unica garanzia di integrita': non rimuoverla.
#
# Il download avviene su un file temporaneo (OUT.download): l'esito di curl
# viene controllato esplicitamente (niente set -e sul download) e il checksum
# e' verificato sul temporaneo PRIMA di sovrascrivere un eventuale "dlv2" gia'
# presente. Cosi' un trasferimento troncato o fallito a meta' (connessione
# resettata, curl che esce con errore) non puo' mai lasciare sul posto un
# binario corrotto ne' distruggere un "dlv2" valido preesistente: il
# temporaneo viene sempre cancellato su qualunque percorso di errore.
set -e
URL="https://www.mat.unical.it/DLV2/releases/2.1.2/dlv-2.1.2-arm64"
EXPECTED="b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7"
OUT="dlv2"
TMP="$OUT.download"

rm -f "$TMP"
echo "Scarico DLV2 da $URL"
if ! curl -skL -o "$TMP" "$URL"; then
  echo "DOWNLOAD FALLITO"
  rm -f "$TMP"
  exit 1
fi

ACTUAL=$(shasum -a 256 "$TMP" | cut -d' ' -f1)
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "CHECKSUM ERRATO"
  echo "  atteso:   $EXPECTED"
  echo "  ottenuto: $ACTUAL"
  rm -f "$TMP"
  exit 1
fi

chmod +x "$TMP"
mv "$TMP" "$OUT"
echo "OK: $OUT verificato"
