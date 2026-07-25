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
