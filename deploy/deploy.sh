#!/usr/bin/env bash
# Deploys one commit of main to this k3s cluster: dump the databases, apply the manifests with that commit's images,
# wait for the rollout, check the site, and roll the code back if anything fails. job-list, which is set up by hand
# (its secrets and database), is updated last and only where those exist. Run on the VM (by hand or by the CI
# job through a remote-command channel); it needs nothing but kubectl, git and curl.
#
#   deploy/deploy.sh <full 40-character commit sha>
#
# Flyway migrations are not reversed by the rollback: the dump taken first is the way back for the data.
set -euo pipefail

SHA="${1:-}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "usage: $0 <full 40-character commit sha>" >&2; exit 2; }

REPO="${CVHELPER_REPO:-$(cd "$(dirname "$0")/.." && pwd)}"
BACKUPS="${CVHELPER_BACKUPS:-/var/backups/cvhelper}"
SITE="${CVHELPER_SITE:-https://cvhelper.dev}"
KEEP_DUMPS=14
DEPLOYMENTS=(pdf-worker ats-worker core-api web)
NS=cvhelper
CERT=cvhelper-dev-tls   # secretName in deploy/k8s/ingress.yaml, which cert-manager also uses as the Certificate name

# One deploy at a time.
exec 9>"${TMPDIR:-/tmp}/cvhelper-deploy.lock"
flock -n 9 || { echo "another deploy is running" >&2; exit 1; }

# Only commits that are on main are deployed, whoever asks.
git -C "$REPO" fetch --quiet origin main
git -C "$REPO" merge-base --is-ancestor "$SHA" origin/main || { echo "$SHA is not on origin/main" >&2; exit 1; }
git -C "$REPO" checkout --quiet --detach "$SHA"

# One database of the shared Postgres into $BACKUPS. No dump, no deploy.
DUMPS=()
dump_db() {
  local file="$BACKUPS/$1-$(date +%Y%m%d-%H%M%S)-${SHA:0:8}.dump"
  kubectl -n "$NS" exec postgres-0 -- pg_dump -U job_search -Fc "$1" > "$file"
  [ -s "$file" ] || { echo "the $1 dump is empty, not deploying" >&2; rm -f "$file"; exit 1; }
  chmod 600 "$file"
  # shellcheck disable=SC2012
  ls -1t "$BACKUPS/$1"-*.dump | tail -n +$((KEEP_DUMPS + 1)) | xargs -r rm -f
  DUMPS+=("$file")
}

# The data first, except on the very first install, when there is nothing to dump yet.
if kubectl -n "$NS" get statefulset postgres >/dev/null 2>&1; then
  mkdir -p "$BACKUPS"
  chmod 700 "$BACKUPS"
  dump_db job_search
  if [ "$(kubectl -n "$NS" exec postgres-0 -- psql -U job_search -d postgres -Atc "select 1 from pg_database where datname = 'job_list'")" = 1 ]; then
    dump_db job_list
  fi
fi

# The tags go into a copy, so the checkout stays clean.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
render() {
  mkdir -p "$WORK/$1"
  cp -R "$REPO/deploy/$1/." "$WORK/$1/"
  sed -i.bak "s/newTag: .*/newTag: sha-$SHA/" "$WORK/$1/kustomization.yaml"
}
render k8s

rollback() {
  echo "deploy failed, rolling the code back (the database is not touched; dumps: ${DUMPS[*]:-none, first install})" >&2
  for d in "${DEPLOYMENTS[@]}"; do kubectl -n "$NS" rollout undo "deploy/$d" || true; done
  exit 1
}

kubectl apply -k "$WORK/k8s" || rollback
for d in "${DEPLOYMENTS[@]}"; do
  kubectl -n "$NS" rollout status "deploy/$d" --timeout=300s || rollback
done

# On the first install the certificate is still being issued; until then Traefik serves its own and curl refuses it.
# The same wait costs nothing later, when the certificate is already Ready.
for _ in $(seq 1 60); do
  [ "$(kubectl -n "$NS" get certificate "$CERT" -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)" = True ] && break
  sleep 5
done

# From the outside, through the ingress: the page and the API both have to answer.
curl -fsS --max-time 15 --retry 5 --retry-delay 5 --retry-connrefused -o /dev/null "$SITE/" || rollback
curl -fsS --max-time 15 -o /dev/null "$SITE/api/auth/config" || rollback

# job-list is optional and prepared by hand (docs: deploy/README.md, section job-list). It goes last, after the site is
# known to be good, so a failure here rolls back only job-list and still turns the run red.
if kubectl -n "$NS" get secret job-list-secrets >/dev/null 2>&1; then
  render job-list
  kubectl apply -k "$WORK/job-list" && kubectl -n "$NS" rollout status deploy/job-list --timeout=300s || {
    echo "job-list failed to roll out; rolling job-list back, the rest of the release stays" >&2
    kubectl -n "$NS" rollout undo deploy/job-list || true
    exit 1
  }
fi

echo "Deployed ${SHA:0:8}. Dumps: ${DUMPS[*]:-none, first install}"
