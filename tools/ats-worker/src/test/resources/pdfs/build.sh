#!/bin/sh
# Rebuilds the fixture PDFs from the .tex files (needs tectonic: brew install tectonic). CI does not run this; the
# PDFs are committed. After changing a template or the plain-text renderer, regenerate and commit all of them.
set -e
cd "$(dirname "$0")"
for f in *.tex; do tectonic "$f"; done
