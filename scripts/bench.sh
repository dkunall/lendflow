#!/usr/bin/env bash
# Rough numbers against the compose stack, not a load test. Submits N clean personal-loan applications,
# C at a time, and times each from the submit call until the API reports APPROVED (polled every 50 ms).
# Each applicant gets its own date of birth, otherwise the near-identical names trip the duplicate check.
# Then races C acceptances with different idempotency keys at one offer.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
N=${N:-40}
C=${C:-8}
RUN=$(date +%s)
APPLICANT=$(token "bench-$RUN" lendflow.applicant)
OUT=$(mktemp -d)
# A fresh block of 40-odd birth dates per run, so reruns don't match the previous run's applicants either.
DOB_BASE=$(( RANDOM % 400 * 20 ))

one() {
  local i=$1 id t0 s
  id=$(expect 201 "$APPLICANT" POST /v1/applications \
    "$(draft "Bench Applicant $i" "$(pan $(( 1000 + i )))" 300000 36 90000 5000 \
      "$(date -u -d "1975-01-01 + $(( DOB_BASE + i )) days" +%F)")" | jq -r .id)
  doc=$(mktemp)
  for type in PAN_CARD INCOME_PROOF; do
    printf '%%PDF-1.4\n%% %s\n%%%%EOF\n' "$type" > "$doc"
    curl -sf -o /dev/null -H "Authorization: Bearer $APPLICANT" -F "type=$type" -F "file=@$doc;filename=$type.pdf" \
      "$BASE/v1/applications/$id/documents"
  done
  rm -f "$doc"
  t0=$(now_ms)
  expect 200 "$APPLICANT" POST "/v1/applications/$id/submit" > /dev/null
  for _ in $(seq 1 600); do
    s=$(expect 200 "$APPLICANT" GET "/v1/applications/$id" | jq -r .status)
    [[ $s != SUBMITTED && $s != UNDERWRITING ]] && break
    sleep 0.05
  done
  echo "$(( $(now_ms) - t0 )) $s $id" > "$OUT/$i"
}

echo "submitting $N applications, $C at a time"
START=$(now_ms)
for i in $(seq 1 "$N"); do
  one "$i" &
  (( i % C == 0 )) && wait
done
wait
WALL=$(( $(now_ms) - START ))
cat "$OUT"/* > "$OUT/all"
echo "outcomes: $(cut -d' ' -f2 "$OUT/all" | sort | uniq -c | xargs)"
cut -d' ' -f1 "$OUT/all" | sort -n | awk -v wall="$WALL" '{a[NR]=$1} END {
  printf "submit -> decided: p50 %d ms, p95 %d ms, max %d ms over %d; wall %d ms (incl. drafts and uploads)\n",
    a[int(NR*0.5+0.5)], a[int(NR*0.95+0.5)], a[NR], NR, wall }'

OFFER=$(expect 200 "$APPLICANT" GET "/v1/applications/$(awk '$2 == "APPROVED" {print $3; exit}' "$OUT/all")/offer" | jq -r .id)
for i in $(seq 1 "$C"); do
  call "$APPLICANT" POST "/v1/offers/$OFFER/accept" "" -H "Idempotency-Key: race-$RUN-$i" | tail -n1 > "$OUT/accept-$i" && echo >> "$OUT/accept-$i" &
done
wait
echo "racing acceptances of one offer: $(cat "$OUT"/accept-* | sort | uniq -c | xargs)"
rm -rf "$OUT"
