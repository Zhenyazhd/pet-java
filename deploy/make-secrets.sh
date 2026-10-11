#!/usr/bin/env bash
# Creates the Secrets the manifests refer to. Run on the server (or anywhere kubectl points at the cluster).
# Nothing here is stored in git. Re-running replaces the Secrets; the database password is only read when the
# database is first created, so do not change it afterwards without also changing it inside PostgreSQL.
set -euo pipefail

: "${OPENROUTER_API_KEY:?set OPENROUTER_API_KEY}"
: "${APP_ADMIN_EMAIL:?set APP_ADMIN_EMAIL (the account that becomes admin)}"
: "${CLOUDFLARE_API_TOKEN:?set CLOUDFLARE_API_TOKEN (Zone:DNS:Edit)}"
: "${APP_BOOTSTRAP_PASSWORD:?set APP_BOOTSTRAP_PASSWORD (>= 12 chars, the first admin login)}"

rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | head -c 40; }

kubectl get ns cvhelper >/dev/null 2>&1 || kubectl create ns cvhelper

# Keep existing generated values so a re-run does not lock the app out of its own database.
existing() { kubectl -n cvhelper get secret cvhelper-secrets -o "jsonpath={.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }
PG="$(existing POSTGRES_PASSWORD)"; PG="${PG:-$(rand)}"
WT="$(existing LATEX_WORKER_TOKEN)"; WT="${WT:-$(rand)}"

kubectl -n cvhelper create secret generic cvhelper-secrets \
  --from-literal=POSTGRES_PASSWORD="$PG" \
  --from-literal=LATEX_WORKER_TOKEN="$WT" \
  --from-literal=OPENROUTER_API_KEY="$OPENROUTER_API_KEY" \
  --from-literal=APP_ADMIN_EMAIL="$APP_ADMIN_EMAIL" \
  --from-literal=APP_BOOTSTRAP_PASSWORD="$APP_BOOTSTRAP_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n cert-manager create secret generic cloudflare-api-token \
  --from-literal=api-token="$CLOUDFLARE_API_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
echo "Secrets applied."
