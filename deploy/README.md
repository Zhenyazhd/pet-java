# Deploying to k3s (single node)

Target: one Amazon Lightsail VM (4 GB RAM, 2 vCPU, Ubuntu 24.04, x86_64) running k3s, domain `cvhelper.dev`
(DNS at Cloudflare). Everything lives in the `cvhelper` namespace. There are **no backups** configured (decision for
now) — see "Data loss" at the end.

```
Internet -> :443 Traefik (TLS, cert-manager) -> web (nginx: SPA + /api proxy) -> core-api -> postgres
                                                                              \-> latex-worker
```

## 0. Once, before anything else

1. **OpenRouter**: set a credit limit on the key and top up the balance.
2. **Cloudflare**: create an API token with *Zone -> DNS -> Edit* for `cvhelper.dev` only.
3. **Lightsail**: in the instance's *Networking* tab create and attach a **static IP** (the default public address
   changes when the instance is stopped), and in *IPv4 Firewall* add rules for HTTPS (443) and HTTP (80) next to SSH.
   Under *Snapshots* you can switch on automatic daily snapshots (about $1/month for this disk) — the easiest backup
   there is, if you change your mind about backups.
4. **Cloudflare DNS**: A record `cvhelper.dev` -> the static IP, **DNS only (grey cloud)**. (Orange cloud would put
   Cloudflare's proxy in front, which changes the client address and TLS handling this setup is built for.)
5. **GitHub**: push to `main`; the `images` job in CI publishes `ghcr.io/zhenyazhd/cvhelper-{core-api,latex-worker,web}` for amd64.
   Either make the three packages public, or create a pull secret
   (`kubectl -n cvhelper create secret docker-registry ghcr --docker-server=ghcr.io --docker-username=<user> --docker-password=<PAT with read:packages>`
   and add `imagePullSecrets: [{name: ghcr}]` to the three Deployments).

## 1. Install k3s and cert-manager (on the VM)

```bash
git clone https://github.com/Zhenyazhd/pet-java.git && cd pet-java   # the manifests come from the repo
curl -sfL https://get.k3s.io | sh -          # includes Traefik, local-path storage, NetworkPolicy support
# let your own user run kubectl (the k3s config file is root-only):
mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config && sudo chown "$USER" ~/.kube/config
export KUBECONFIG=~/.kube/config
kubectl get nodes                            # wait for Ready
# Keep the visitor's real IP (the rate limits depend on it):
kubectl apply -f deploy/k3s/traefik-config.yaml
# cert-manager:
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.18.2/cert-manager.yaml
kubectl -n cert-manager rollout status deploy/cert-manager-webhook
```

Edit the e-mail in `deploy/k3s/cluster-issuers.yaml`, then `kubectl apply -f deploy/k3s/cluster-issuers.yaml`.

The 4 GB machine is tight: limits are postgres 512 Mi + core-api 1 Gi + worker 1.25 Gi + web 128 Mi, plus k3s itself
(~0.6 GB). Add swap as a safety net: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab`.
Watch `kubectl top pod -n cvhelper` during a PDF compile and raise the worker limit if it is OOM-killed.

## 2. Secrets

```bash
export OPENROUTER_API_KEY=...  APP_ADMIN_EMAIL=you@example.com
export APP_BOOTSTRAP_PASSWORD=...   # >= 12 chars: the first admin login
export CLOUDFLARE_API_TOKEN=...
./deploy/make-secrets.sh
```

After the first login, set `APP_BOOTSTRAP_PASSWORD` to something unused (re-run the script with a random value): it is
only read while the admin has no password, but there is no reason to keep the real one in the cluster.

`APP_STRICT_SECRETS=true` makes core-api refuse to start with a weak database password or worker token; the script
generates strong ones.

## 3. First deploy (use the staging certificate first)

```bash
cd deploy/k8s
# set the three newTag: values in kustomization.yaml to sha-<full commit sha> of a commit whose `images` job finished:
sed -i 's/newTag: .*/newTag: sha-<full commit sha>/' kustomization.yaml
# in ingress.yaml set cert-manager.io/cluster-issuer: letsencrypt-staging for the first run
kubectl apply -k .
kubectl -n cvhelper get pods -w
kubectl -n cvhelper get certificate        # READY=True after a minute or two
```

When the staging certificate is issued, switch the annotation to `letsencrypt-prod`, delete the secret
`cvhelper-dev-tls` and re-apply. Do not skip staging: production issuers rate-limit failed attempts.

Then apply the network policies **last**, after everything works:
`kubectl apply -n cvhelper -f network-policies.yaml` — and re-test the checklist below. If something breaks, delete
them with `kubectl -n cvhelper delete networkpolicy --all`.

## 4. Smoke test checklist

- `https://cvhelper.dev` loads, `http://` redirects to https, certificate is valid.
- Log in as the admin (`APP_ADMIN_EMAIL` / `APP_BOOTSTRAP_PASSWORD`); the session cookie is kept after reload.
- Create an invite code in the admin area, register a second account with it.
- Build a resume, run an AI edit, compile and **preview the PDF** (CSP: iframe on a blob: URL must not be blank).
- Rate limit sees real addresses: hit the login with wrong passwords ~10 times from your machine and from a phone on
  mobile data; each should be limited separately, not together. (If both are limited together, the client address is
  wrong: check `externalTrafficPolicy: Local` and the Traefik pod's `X-Forwarded-For`.)
- `kubectl -n cvhelper logs deploy/core-api` shows `[user=<id>]` and no stack traces.
- After the policies: the same flow still works.

## 5. Updating

Push to `main`, wait for the `images` job, then on the VM:
`git pull`, set the new `newTag:` values as in step 3 and `kubectl apply -k deploy/k8s`. core-api uses `Recreate`, so there is a
short gap (seconds to a minute) while it restarts; Flyway migrations run on startup.

## 6. Rollback

`kubectl -n cvhelper rollout undo deploy/core-api` (or set the previous sha). Flyway migrations are not reversed, so
a release that changed the schema is rolled back in code only if the old code still works with the new schema.

## Data loss

All data is on one disk of one VM (`local-path` volume of the `postgres` StatefulSet). If the VM or the volume is
lost, the data is lost. Until backups are set up, at least take a manual dump before every update that has a
migration:

```bash
kubectl -n cvhelper exec postgres-0 -- pg_dump -U job_search -Fc job_search > job_search-$(date +%F).dump
# restore into an empty database:
kubectl -n cvhelper exec -i postgres-0 -- pg_restore -U job_search -d job_search --clean --if-exists < job_search-DATE.dump
```

Options for later: Lightsail automatic snapshots, or a nightly `pg_dump` CronJob uploaded to
Cloudflare R2 / S3.

## Notes

- HSTS is sent with `max-age=300` (nginx `security-headers.conf`). Raise it after the site has run for a while.
  `.dev` is already HTTPS-only in browsers.
- ATS screening is off (`ATS_SCREENER_ENABLED=false`); there is no ats-screener service in this deployment.
