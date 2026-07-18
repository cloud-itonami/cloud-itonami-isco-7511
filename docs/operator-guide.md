# Operator Guide

## First Deployment

1. Define the operator's shop coverage and crew intake process.
2. Define consent and purpose categories for worker/shop records.
3. Run synthetic operating cases (work-log entry, crew-operation
   scheduling, supply coordination, safety-concern flagging).
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions
   (all flagged safety concerns, above-threshold supply orders).
5. Measure operating outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path (cut hazard, hygiene-compliance
  concern, contamination-risk concern)
- provenance for all operating records (worker and shop both
  independently registered)
- human review for high-risk cases
- audit export for all gated actions
- a hard, unconditional block on any attempt to route a preparation-
  execution decision, a food-safety-clearance decision (e.g. declaring a
  batch fit for sale), or a shop-safety-officer override decision,
  through this actor — those decisions stay a shop safety officer's (and,
  for food-safety clearance, the shop's designated food-safety authority)
  exclusive authority end to end

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans, and that no deployment configuration can route a preparation-
execution decision, a food-safety-clearance decision, or a
shop-safety-officer judgment override through this actor.
