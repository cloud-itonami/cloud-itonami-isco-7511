# cloud-itonami-isco-7511

Open Occupation Blueprint for **ISCO-08 7511**: Butchers, Fishmongers and
Related Food Preparers.

This repository designs a forkable OSS business for a butcher-shop/
fishmonger scheduling and logistics coordination practice: a shop
scheduling and supply-coordination robot manages crew/task records under a
governor-gated actor, so a butcher/fishmonger crew keeps its own operating
records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/butcher/` implements the `ButcherActor`
as a `langgraph.graph/state-graph` (`butcher.actor`) wired to a
`Butcher/Fishmonger Advisor` (`butcher.advisor`) and an independent
`ButcherGovernor` (`butcher.governor`), following the itonami actor
pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+->
:commit (:ok?) +-> :request-approval (:escalate?, human-in-the-loop
interrupt) +-> :hold (:hard?)`. 24 tests / 52 assertions green
(`kbb -M:test`). HARD invariants (always hold, never overridable): worker
provenance, shop provenance, no-actuation (`:effect` must be `:propose`),
a closed op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any proposal
that would directly finalize a preparation-execution decision (e.g.
deciding to proceed with a specific cutting or butchering operation) *or*
a food-safety-clearance decision (e.g. declaring a batch fit for sale),
or that would override a shop safety officer's judgment. Always-escalate
paths (human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above the
registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a shop scheduling/logistics
coordination robot performs crew scheduling, task/batch/progress-record
logging and meat/fish-stock supply-order coordination for a butcher-shop/
fishmonger crew, under an actor that proposes actions and an independent
**ButcherGovernor** that gates them. The governor never dispatches
hardware itself, never performs butchering/preparation work on the shop
floor, and never finalizes a preparation-execution decision or a
food-safety-clearance decision, and never overrides a shop safety
officer's judgment; `:high`/`:safety-critical` actions (such as a
flagged cut-hazard/hygiene-compliance/contamination-risk concern, or an
above-threshold supply order) require human sign-off. **This actor
coordinates SHOP SCHEDULING/LOGISTICS ONLY — it never performs
butchering/preparation work and never makes a food-safety-clearance
decision itself.**

## Core Contract

```text
worker roster + shop registration + safety-reporting policy
        |
        v
Butcher/Fishmonger Advisor -> ButcherGovernor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a preparation-execution decision, finalize a food-safety-
clearance decision (e.g. declaring a batch fit for sale), override a
shop safety officer's judgment, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7511`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
