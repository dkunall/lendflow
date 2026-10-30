# Shared helpers for demo.sh and bench.sh. Source, don't run.
BASE=${BASE:-http://localhost:7990}
OAUTH=${OAUTH:-http://localhost:7991}

# The app validates the issuer as http://oauth:8080/lendflow (its in-network name), and
# mock-oauth2-server derives the issuer from the Host header. The client_id becomes the subject.
token() {
  curl -sf -H "Host: oauth:8080" -d grant_type=client_credentials -d "client_id=$1" -d client_secret=unused \
    -d "scope=$2" "$OAUTH/lendflow/token" | jq -r .access_token
}

# call TOKEN METHOD PATH [BODY] [extra curl args...]; prints the body, then the status on its own line.
call() {
  local tok=$1 method=$2 path=$3 body=${4:-}
  shift 4 || shift $#
  local auth=()
  [[ -n $tok ]] && auth=(-H "Authorization: Bearer $tok")
  curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" "${auth[@]}" -H 'Content-Type: application/json' \
    "$@" ${body:+-d "$body"}
}

# expect STATUS TOKEN METHOD PATH [BODY] [extra...]; prints the body or fails loudly.
expect() {
  local want=$1 out status
  shift
  out=$(call "$@")
  status=${out##*$'\n'}
  if [[ $status != "$want" ]]; then
    echo "FAIL: $2 $3 -> $status (wanted $want): ${out%$'\n'*}" >&2
    exit 1
  fi
  printf '%s' "${out%$'\n'*}"
}

check() {
  if [[ $2 == "$3" ]]; then echo "  ok   $1"; else echo "  FAIL $1: got '$2', wanted '$3'" >&2; exit 1; fi
}

# A PAN whose four digits pick the mock bureau profile; the letters are random so reruns are new people.
pan() {
  printf '%s%04d%s' "$(tr -dc 'A-Z' < /dev/urandom | head -c 5)" "$1" "$(tr -dc 'A-Z' < /dev/urandom | head -c 1)"
}

# draft NAME PAN AMOUNT TENURE INCOME EXISTING_EMI [DOB] -> JSON body for a salaried personal loan.
# Date of birth, phone and email vary per run so the duplicate check doesn't match an earlier run.
draft() {
  jq -cn --arg name "$1" --arg pan "$2" --arg amount "$3" --argjson tenure "$4" --arg income "$5" --arg emi "$6" \
    --arg dob "${7:-$(date -u -d "1985-01-01 + $(( RANDOM % 3000 )) days" +%F)}" \
    --arg phone "9$(printf '%09d' $(( (RANDOM << 15 | RANDOM) % 1000000000 )))" \
    --arg email "$(tr ' A-Z' '.a-z' <<<"$1").$RANDOM$RANDOM@example.com" \
    '{product: "PERSONAL", amount: $amount, tenureMonths: $tenure, monthlyIncome: $income, existingEmi: $emi,
      employmentType: "SALARIED", monthsInCurrentJob: 30,
      applicant: {fullName: $name, pan: $pan, dateOfBirth: $dob, phone: $phone, email: $email},
      consent: {bureauPull: true}}'
}

# submit TOKEN BODY -> application id, after uploading the PAN card and income proof.
submit() {
  local tok=$1 id doc
  id=$(expect 201 "$tok" POST /v1/applications "$2" | jq -r .id)
  doc=$(mktemp)
  for type in PAN_CARD INCOME_PROOF; do
    printf '%%PDF-1.4\n%% %s\n%%%%EOF\n' "$type" > "$doc"
    curl -sf -o /dev/null -H "Authorization: Bearer $tok" -F "type=$type" -F "file=@$doc;filename=$type.pdf" \
      "$BASE/v1/applications/$id/documents"
  done
  rm -f "$doc"
  expect 200 "$tok" POST "/v1/applications/$id/submit" > /dev/null
  echo "$id"
}

# routed TOKEN ID -> status once the background checks have moved it past SUBMITTED/UNDERWRITING.
routed() {
  local s
  for _ in $(seq 1 200); do
    s=$(expect 200 "$1" GET "/v1/applications/$2" | jq -r .status)
    [[ $s != SUBMITTED && $s != UNDERWRITING ]] && { echo "$s"; return; }
    sleep 0.1
  done
  echo "$s"
}

now_ms() { date +%s%3N; }
