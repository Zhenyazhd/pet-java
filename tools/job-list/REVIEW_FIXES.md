# Fixes after the review of job-list

Source: the `job-list-reviewer` report (blockers 1–4, major 5–12, minor list). Rule for every item: **a test that fails
first, then the fix, then it passes**. After each phase the `job-list-reviewer` is run on the fixed parts only.
`V1__baseline.sql` has not been applied to production, so it is edited in place (a local development database needs
`DROP DATABASE job_list; CREATE DATABASE job_list OWNER job_list;`).

## Phase 1 — the money ledger and the run lifecycle (blockers 1 and 4, majors 5 and 6)

- A **spend ledger** `spend_ledger`, written *before* an Apify call (a reservation at the per-source cap) and settled
  after it. It has no foreign keys to `search_profile` or `run`, so deleting a profile or pruning old runs cannot erase
  it. The monthly budget, the per-query interval and the lookback window read it.
- Reserve and check in **one transaction under an advisory lock**, so two runs (or two pods) cannot both pass the
  budget check. A crash leaves the reservation in place: the spend is counted at the cap, and the run's own
  `maxTotalChargeUsd` makes that the ceiling.
- The **interval is keyed by the query** (source + roles + regions), not by the profile id, so a deleted and recreated
  profile, or a second profile with the same query, does not pay again.
- **Lease renewal** at every source and ranking step; the **finishing write is guarded** (`WHERE status = 'RUNNING'`),
  and so is the janitor's release (`AND lease_until < now()`); a worker that finds its run released stops.
- Apify: **retry** a failed poll and a failed dataset read; a finished run's reported cost is used (the cap only when
  it is unknown).
- Tests first: delete profile → usage unchanged and the interval still holds; recreate with the same query → no second
  paid run; concurrent reservations → one wins at the budget edge; crash after reserve → the cost stays counted; a late
  worker cannot overwrite a janitor release; one failed poll does not fail the run.

## Phase 2 — LLM cost (blocker 3)

`max_tokens`; salvage the valid entries of a partly bad answer; take the JSON between the first `{` and the last `}`;
a call budget that is per run *and* a monthly LLM budget in the ledger; a limit on runs per client per day; rank only
what this profile's own query could match (not the whole shared pool) or cap new-profile ranking.

## Phase 3 — CI and deploy (blocker 2)

CI job and image entry; the `deploy/README.md` section (order: DNS record, secrets, db-init job, apply);
`terminationGracePeriodSeconds`; memory limit against the measured heap.

## Phase 4 — correctness and API (majors 7–11, security minors)

Stale matches after a prompt change; fingerprint that does not merge distinct openings; one anti-join query for the
candidates; MCP `save_profile` schema (`regions` as objects, enums, bounds); `null` elements → 400; request size limit;
`Origin` check on `/mcp`; URL scheme and text-length checks in the mappers; validation of `JobListProperties`.

## Phase 5 — tests and clean-up (major 12, minors)

Tests for everything money- and isolation-related that has none; mapper helper duplication; docs that still mention the
removed filter module and schedule.
