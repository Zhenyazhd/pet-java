#!/usr/bin/env bash
# Deploys one commit of main to this k3s cluster: dump the database, apply the manifests with that commit's images,
# wait for the rollout, check the site, and roll the code back if anything fails. Run on the VM (by hand or by the CI
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
DEPLOYMENTS=(pdf-worker core-api web)
NS=cvhelper

# One deploy at a time.
exec 9>"${TMPDIR:-/tmp}/cvhelper-deploy.lock"
flock -n 9 || { echo "another deploy is running" >&2; exit 1; }

# Only commits that are on main are deployed, whoever asks.
git -C "$REPO" fetch --quiet origin main
git -C "$REPO" merge-base --is-ancestor "$SHA" origin/main || { echo "$SHA is not on origin/main" >&2; exit 1; }
git -C "$REPO" checkout --quiet --detach "$SHA"

# The data first. No dump, no deploy.
mkdir -p "$BACKUPS"
chmod 700 "$BACKUPS"
DUMP="$BACKUPS/job_search-$(date +%Y%m%d-%H%M%S)-${SHA:0:8}.dump"
kubectl -n "$NS" exec postgres-0 -- pg_dump -U job_search -Fc job_search > "$DUMP"
[ -s "$DUMP" ] || { echo "the dump is empty, not deploying" >&2; rm -f "$DUMP"; exit 1; }
chmod 600 "$DUMP"
# shellcheck disable=SC2012
ls -1t "$BACKUPS"/job_search-*.dump | tail -n +$((KEEP_DUMPS + 1)) | xargs -r rm -f

# The tags go into a copy, so the checkout stays clean.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp -R "$REPO/deploy/k8s/." "$WORK/"
sed -i.bak "s/newTag: .*/newTag: sha-$SHA/" "$WORK/kustomization.yaml"

rollback() {
  echo "deploy failed, rolling the code back (the database is not touched; dump: $DUMP)" >&2
  for d in "${DEPLOYMENTS[@]}"; do kubectl -n "$NS" rollout undo "deploy/$d" || true; done
  exit 1
}

kubectl apply -k "$WORK" || rollback
for d in "${DEPLOYMENTS[@]}"; do
  kubectl -n "$NS" rollout status "deploy/$d" --timeout=300s || rollback
done

# From the outside, through the ingress: the page and the API both have to answer.
curl -fsS --max-time 15 --retry 5 --retry-delay 5 --retry-connrefused -o /dev/null "$SITE/" || rollback
curl -fsS --max-time 15 -o /dev/null "$SITE/api/auth/config" || rollback

echo "Deployed ${SHA:0:8}. Dump: $DUMP"
