(ns butcher.governor
  "ButcherGovernor — the independent safety/scope layer gating every shop
  scheduling/logistics proposal an advisor may make for a butcher-shop/
  fishmonger crew. The governor never dispatches hardware itself, never
  performs butchering/preparation work on the shop floor, and never
  finalizes a preparation-execution decision (e.g. deciding to proceed
  with a specific cutting or butchering operation) or a food-safety-
  clearance decision (e.g. declaring a batch fit for sale), and never
  overrides a shop safety officer's judgment — those are permanently out
  of this actor's scope and remain a shop safety officer's exclusive
  judgment (README's 'Robotics premise': this actor coordinates SHOP
  SCHEDULING/LOGISTICS ONLY — it never performs butchering/preparation
  work or makes food-safety-clearance decisions itself). Modeled closely
  on cloud-itonami-isco-7213's sheetmetal.governor for the cutting-tool
  physical-safety-domain shape, extended with a second, independent
  food-safety/hygiene-compliance scope-exclusion dimension (butchers and
  fishmongers work raw meat/fish under hygiene-compliance requirements,
  so contamination-risk stakes stack on top of cut-hazard stakes).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. worker provenance     — the crew member must be independently
                                verified/registered before any action.
    2. shop provenance       — the shop must be independently verified/
                                registered before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs butchering/preparation
                                work itself; it only gates what the
                                advisor may coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                preparation-execution decision (e.g.
                                deciding to proceed with a specific
                                cutting or butchering operation), or to
                                directly finalize a food-safety-clearance
                                decision (e.g. declaring a batch fit for
                                sale), or to override a shop safety
                                officer's judgment, is a hard, permanent
                                block (checked both against the proposed
                                :op and, defense-in-depth, against the
                                proposal's :rationale text — matched as
                                full finalization/execution ACTION
                                phrases such as \"proceed with the
                                cutting operation\" / \"declare the batch
                                fit for sale\" / \"override the shop
                                safety officer's judgment\", never as
                                bare nouns like \"cutting\", \"batch\" or
                                \"safety\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged work record
                                for worker …\" or \"scheduled crew
                                operation for cutting task …\" or
                                \"…routed for shop safety officer
                                review\" — all three legitimately contain
                                those bare nouns but none is a
                                finalization action, and all are
                                exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a cut-hazard / hygiene-compliance /
                                contamination-risk concern always
                                escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [butcher.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops` above,
;; so they are already refused by the closed-allowlist check below; they
;; are named again here — as explicit finalization/execution ACTIONS,
;; never bare nouns — so a future allowlist edit cannot silently re-open
;; either of these two independent out-of-scope paths (preparation-
;; execution finalization, food-safety-clearance finalization) without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-cutting-operation :proceed-with-butchering-operation
    :finalize-preparation-execution-decision
    :declare-batch-fit-for-sale :finalize-food-safety-clearance
    :clear-batch-for-sale
    :override-safety-officer-judgment :override-shop-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("cutting", "butchering", "batch", "sale", "safety", "shop safety
;; officer") — so this can never match inside the mock advisor's own
;; default rationale text (which legitimately contains those bare
;; nouns, e.g. "cutting task" / "shop safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the cutting operation" "proceed with the butchering operation"
   "finalize the cutting operation" "finalize the preparation decision"
   "declare the batch fit for sale" "declare this batch fit for sale"
   "finalize the food safety clearance" "clear the batch for sale"
   "override the shop safety officer's judgment"
   "override the safety officer's judgment"
   "override shop safety officer judgment"
   "override safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal worker-record shop-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? worker-record)
      (conj {:rule :no-worker
             :detail "未登録 worker への提案は不可（worker record は独立して検証・登録済みでなければならない）"})

      (nil? shop-record)
      (conj {:rule :no-shop
             :detail "未登録 shop への提案は不可（shop record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は現場作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "調理・解体工程の実行判断の確定、食品安全クリアランス判断（出荷可否の確定を含む）の確定、shop safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `butcher.store/Store`. Pure — never mutates the store,
  never dispatches a shop-floor operation, never finalizes a food-safety-
  clearance decision."
  [request _context proposal store]
  (let [worker-record (store/worker store (:worker-id request))
        shop-record (some->> (:shop-id proposal) (store/shop store))
        hard (hard-violations proposal worker-record shop-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
