#!/usr/bin/env bash
# Creates the Secrets the manifests refer to. Run on the server (or anywhere kubectl points at the cluster).
# Nothing here is stored in git. Re-running replaces the Secrets; the database password is only read when the
# database is first created, so do not change it afterwards without also changing it inside PostgreSQL.
set -euo pipefail

: "${OPENROUTER_API_KEY:?set OPENROUTER_API_KEY}"
: "${APP_ADMIN_EMAIL:?set APP_ADMIN_EMAIL (the account that becomes admin)}"
: "${CLOUDFLARE_API_TOKEN:?set CLOUDFLARE_API_TOKEN (Zone:DNS:Edit)}"
: "${APP_BOOTSTRAP_PASSWORD:?set APP_BOOTSTRAP_PASSWORD (>= 12 chars, the first admin login)}"
: "${SMTP_PASSWORD:?set SMTP_PASSWORD (the API key of the mail provider; confirmation and reset mails go through it)}"

rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | head -c 40; }

kubectl get ns cvhelper >/dev/null 2>&1 || kubectl create ns cvhelper

# Keep existing generated values so a re-run does not lock the app out of its own database.
existing() { kubectl -n cvhelper get secret cvhelper-secrets -o "jsonpath={.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }
PG="$(existing POSTGRES_PASSWORD)"; PG="${PG:-$(rand)}"
WT="$(existing PDF_WORKER_TOKEN)"; WT="${WT:-$(rand)}"
AT="$(existing ATS_WORKER_TOKEN)"; AT="${AT:-$(rand)}"

# Sign-in with Google: only when both are given (an OAuth "Web application" client from the Google Cloud console).
GOOGLE=()
if [ -n "${GOOGLE_CLIENT_ID:-}" ] && [ -n "${GOOGLE_CLIENT_SECRET:-}" ]; then
  GOOGLE=(--from-literal=GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" --from-literal=GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET")
fi

kubectl -n cvhelper create secret generic cvhelper-secrets \
  --from-literal=POSTGRES_PASSWORD="$PG" \
  --from-literal=PDF_WORKER_TOKEN="$WT" \
  --from-literal=ATS_WORKER_TOKEN="$AT" \
  --from-literal=OPENROUTER_API_KEY="$OPENROUTER_API_KEY" \
  --from-literal=APP_ADMIN_EMAIL="$APP_ADMIN_EMAIL" \
  --from-literal=APP_BOOTSTRAP_PASSWORD="$APP_BOOTSTRAP_PASSWORD" \
  --from-literal=SMTP_PASSWORD="$SMTP_PASSWORD" \
  ${GOOGLE[@]+"${GOOGLE[@]}"} \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n cert-manager create secret generic cloudflare-api-token \
  --from-literal=api-token="$CLOUDFLARE_API_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
# Private GHCR packages: a GitHub token with only read:packages. Skip both variables if the packages are public.
if [ -n "${GHCR_USER:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  kubectl -n cvhelper create secret docker-registry ghcr \
    --docker-server=ghcr.io --docker-username="$GHCR_USER" --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -
fi
# job-list: only when its variables are given. The tokens and the database password are generated and kept on re-runs.
if [ -n "${APIFY_TOKEN:-}" ] && [ -n "${JOB_LIST_OPENROUTER_API_KEY:-}" ]; then
  existing_job_list() { kubectl -n cvhelper get secret job-list-secrets -o "jsonpath={.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }
  JL_DB="$(existing_job_list DB_PASSWORD)"; JL_DB="${JL_DB:-$(rand)}"
  JL_CORE="$(existing_job_list CORE_API_TOKEN)"; JL_CORE="${JL_CORE:-$(rand)}"
  JL_BOT="$(existing_job_list BOT_TOKEN)"; JL_BOT="${JL_BOT:-$(rand)}"
  kubectl -n cvhelper create secret generic job-list-secrets \
    --from-literal=DB_PASSWORD="$JL_DB" \
    --from-literal=CORE_API_TOKEN="$JL_CORE" \
    --from-literal=BOT_TOKEN="$JL_BOT" \
    --from-literal=APIFY_TOKEN="$APIFY_TOKEN" \
    --from-literal=OPENROUTER_API_KEY="$JOB_LIST_OPENROUTER_API_KEY" \
    --dry-run=client -o yaml | kubectl apply -f -
  echo "job-list secrets applied. The bot's token: kubectl -n cvhelper get secret job-list-secrets -o jsonpath='{.data.BOT_TOKEN}' | base64 -d"
fi
echo "Secrets applied."
