#!/usr/bin/env bash
# Walks LendFlow end to end against the compose stack: an auto-approved loan through offer, schedule,
# acceptance and payout; a referred one through maker-checker; a decline with reason codes; and a
# policy change that is simulated before a second person publishes it. Needs curl and jq.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

RUN=$(date +%s)
ASHA=$(token "asha-$RUN" lendflow.applicant)
RAVI=$(token "ravi-$RUN" lendflow.applicant)
SONU=$(token "sonu-$RUN" lendflow.applicant)
MAKER=$(token "maker-$RUN" lendflow.underwriter)
MANAGER=$(token "manager-$RUN" lendflow.credit_manager)
OPS=$(token "ops-$RUN" lendflow.ops)
AUTHOR=$(token "policy-a-$RUN" lendflow.policy)
REVIEWER=$(token "policy-b-$RUN" lendflow.policy)

echo "== auto-approval: 3,00,000 over 36 months, clean bureau file"
APP=$(submit "$ASHA" "$(draft "Asha Kulkarni" "$(pan 1234)" 300000 36 90000 5000)")
check "routed" "$(routed "$ASHA" "$APP")" APPROVED
DECISION=$(expect 200 "$MAKER" GET "/v1/applications/$APP/decision")
echo "  decision: $(jq -c '{outcome, ruleset, scorecard, score, grade, reasons}' <<<"$DECISION")"
OFFER=$(expect 200 "$ASHA" GET "/v1/applications/$APP/offer")
OFFER_ID=$(jq -r .id <<<"$OFFER")
echo "  offer: $(jq -c '{amount, annualRate, emi, processingFee, feeGst, netDisbursal, totalInterest}' <<<"$OFFER")"
SCHEDULE=$(expect 200 "$ASHA" GET "/v1/offers/$OFFER_ID/schedule")
check "schedule has one row per month" "$(jq '.instalments | length' <<<"$SCHEDULE")" 36
check "principal repaid equals the loan" "$(jq '([.instalments[].principal] | add) == .principal' <<<"$SCHEDULE")" true

echo "== acceptance is idempotent"
KEY=$(cat /proc/sys/kernel/random/uuid)
A1=$(expect 200 "$ASHA" POST "/v1/offers/$OFFER_ID/accept" "" -H "Idempotency-Key: $KEY" | jq -r .status)
A2=$(expect 200 "$ASHA" POST "/v1/offers/$OFFER_ID/accept" "" -H "Idempotency-Key: $KEY" | jq -r .status)
check "accepted once, replay matches" "$A1/$A2" ACCEPTED/ACCEPTED
check "application sanctioned" "$(expect 200 "$ASHA" GET "/v1/applications/$APP" | jq -r .status)" SANCTIONED

echo "== disbursement handoff over Kafka, confirmed by ops with a UTR"
for _ in $(seq 1 100); do
  PAYOUT=$(expect 200 "$OPS" GET "/v1/disbursements?application=$APP" | jq -c '.[0] // empty')
  [[ -n $PAYOUT ]] && break
  sleep 0.1
done
PAYOUT_ID=$(jq -r .id <<<"$PAYOUT")
check "payout requested for the net amount" \
  "$(jq -r --argjson net "$(jq .netDisbursal <<<"$OFFER")" '.status + " " + (.amount == $net | tostring)' <<<"$PAYOUT")" "REQUESTED true"
UTR="HDFCR5$RUN"
expect 400 "$OPS" POST "/v1/disbursements/$PAYOUT_ID/confirm" '{"utr": "12-34"}' > /dev/null
check "confirm" "$(expect 200 "$OPS" POST "/v1/disbursements/$PAYOUT_ID/confirm" "{\"utr\": \"$UTR\"}" | jq -r .status)" SENT
check "same UTR again is a no-op" "$(expect 200 "$OPS" POST "/v1/disbursements/$PAYOUT_ID/confirm" "{\"utr\": \"$UTR\"}" | jq -r .status)" SENT
check "application disbursed" "$(expect 200 "$ASHA" GET "/v1/applications/$APP" | jq -r .status)" DISBURSED
echo "  history: $(expect 200 "$ASHA" GET "/v1/applications/$APP/history" | jq -c '[.[].to]')"

echo "== referral: no bureau file, so a maker and a different checker decide"
REF=$(submit "$RAVI" "$(draft "Ravi Shankar" "$(pan 9050)" 300000 36 90000 0)")
check "routed" "$(routed "$RAVI" "$REF")" REFER
echo "  reasons shown to the applicant: $(expect 200 "$RAVI" GET "/v1/applications/$REF/decision" | jq -c .reasons)"
TASK=$(expect 200 "$MAKER" GET "/v1/tasks?application=$REF" | jq -r '.[-1].id')
RIVALS=(); for i in 1 2 3 4 5; do RIVALS+=("$(token "uw$i-$RUN" lendflow.underwriter)"); done
RACE=$(mktemp -d)
ALL=("$MAKER" "${RIVALS[@]}")
for i in "${!ALL[@]}"; do call "${ALL[$i]}" POST "/v1/tasks/$TASK/claim" > "$RACE/$i" & done; wait
CLAIMS=$(for f in "$RACE"/*; do tail -n1 "$f"; echo; done); rm -rf "$RACE"
check "six underwriters race for the task, one wins" "$(grep -c '^200$' <<<"$CLAIMS")/$(grep -c '^409$' <<<"$CLAIMS")" 1/5
WINNER=$(expect 200 "$MAKER" GET "/v1/tasks?application=$REF" | jq -r '.[-1].assignee')
# Whoever won the race acts as the maker from here on.
for i in 1 2 3 4 5; do
  if [[ $WINNER == "uw$i-$RUN" ]]; then MAKER=${RIVALS[$((i - 1))]}; fi
done
expect 200 "$MAKER" POST "/v1/tasks/$TASK/complete" '{"action": "APPROVE", "note": "Salary credits verified"}' > /dev/null
CHECK=$(expect 200 "$MANAGER" GET "/v1/tasks?application=$REF" | jq -c '.[-1]')
check "checker task goes to the credit manager" "$(jq -r '.stage + " " + .role' <<<"$CHECK")" "CHECKER CREDIT_MANAGER"
CHECK_ID=$(jq -r .id <<<"$CHECK")
check "the maker cannot check" "$(call "$MAKER" POST "/v1/tasks/$CHECK_ID/claim" | tail -1)" 403
expect 200 "$MANAGER" POST "/v1/tasks/$CHECK_ID/claim" > /dev/null
expect 200 "$MANAGER" POST "/v1/tasks/$CHECK_ID/complete" '{"action": "CONFIRM", "note": "Agree"}' > /dev/null
check "approved after sign-off" "$(expect 200 "$RAVI" GET "/v1/applications/$REF" | jq -r .status)" APPROVED

echo "== decline: a written-off account on the bureau file"
REJ=$(submit "$SONU" "$(draft "Sonu Mehra" "$(pan 8123)" 500000 24 40000 15000)")
check "routed" "$(routed "$SONU" "$REJ")" REJECTED
echo "  reasons shown to the applicant: $(expect 200 "$SONU" GET "/v1/applications/$REJ/decision" | jq -c .reasons)"

echo "== policy change: simulate first, then four eyes to publish"
CURRENT=$(expect 200 "$AUTHOR" GET "/v1/rulesets?product=PERSONAL" | jq -c 'map(select(.status == "PUBLISHED"))[0]')
STRICTER=$(jq -c '.definition | .parameters.autoApproveLimit = "200000"' <<<"$CURRENT")
DRAFT=$(expect 201 "$AUTHOR" POST /v1/rulesets "$(jq -cn --argjson d "$STRICTER" '{product: "PERSONAL", definition: $d}')")
DRAFT_ID=$(jq -r .id <<<"$DRAFT")
SIM=$(expect 200 "$AUTHOR" POST "/v1/rulesets/$DRAFT_ID/simulate?limit=1000")
echo "  $(jq -r .ruleset <<<"$SIM") vs $(jq -r .comparedWith <<<"$SIM"): $(jq -c '.report | {evaluated, changed, matrix}' <<<"$SIM")"
check "the author cannot publish" "$(call "$AUTHOR" POST "/v1/rulesets/$DRAFT_ID/publish" | sed -n 1p | jq -r .code)" four-eyes
check "a second admin publishes" "$(expect 200 "$REVIEWER" POST "/v1/rulesets/$DRAFT_ID/publish" | jq -r .status)" PUBLISHED
check "previous version retired" \
  "$(expect 200 "$AUTHOR" GET "/v1/rulesets/$(jq -r .id <<<"$CURRENT")" | jq -r .status)" RETIRED
NEXT=$(submit "$ASHA" "$(draft "Meena Iyer" "$(pan 1777)" 300000 36 90000 5000)")
check "the same 3,00,000 loan is now referred" "$(routed "$ASHA" "$NEXT")" REFER
# Rolling back is publishing the old definition again; it becomes the next version, history stays intact.
BACK=$(expect 201 "$REVIEWER" POST /v1/rulesets "$(jq -c '{product: "PERSONAL", definition}' <<<"$CURRENT")" | jq -r .id)
check "rolled back by the other admin" "$(expect 200 "$AUTHOR" POST "/v1/rulesets/$BACK/publish" | jq -r '.status + " v" + (.version|tostring)')" \
  "PUBLISHED v$(( $(jq .version <<<"$DRAFT") + 1 ))"

echo "== metrics"
curl -sf "$BASE/actuator/prometheus" | grep -E '^lendflow_(decisions|disbursements|tasks)[a-z_]*_total' | sort || true
echo "demo passed"
