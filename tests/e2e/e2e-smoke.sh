#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080/api/v1}"
ADMIN_USER="${ADMIN_USER:?set ADMIN_USER}"
ADMIN_PASS="${ADMIN_PASS:?set ADMIN_PASS}"
RUN=$(date +%s%N)
PASS=0; FAIL=0

jcode(){ python -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }
jfield(){ python -c "import sys,json;print(json.load(sys.stdin)['data']['$1'])" 2>/dev/null; }
expect(){ local e="$1" a="${2:-}" l="${3:-}"; if [ "$a" = "$e" ]; then PASS=$((PASS+1)); echo "PASS  $l"; else FAIL=$((FAIL+1)); echo "FAIL  $l (expect $e, got '$a')"; fi; }
# 同时检查HTTP状态与JSON.code。
http(){
  local raw
  raw=$(curl --silent --show-error --write-out '\n%{http_code}' "$@")
  printf '%s' "$raw" | python -c 'import sys,json
raw=sys.stdin.read();body,status=raw.rsplit("\n",1);d=json.loads(body)
expected={"INVALID_ARGUMENT": 400, "UNAUTHENTICATED": 401, "FORBIDDEN": 403, "NOT_FOUND": 404, "CONFLICT": 409, "RATE_LIMITED": 429, "INTERNAL_ERROR": 500, "MOCK_LOGIN_DISABLED": 403, "WECHAT_LOGIN_UNAVAILABLE": 503, "ADMIN_LOGIN_FAILED": 401, "USER_RESTRICTED": 403, "FILE_TOO_LARGE": 413, "UNSUPPORTED_MEDIA_TYPE": 415, "INVALID_EVIDENCE_FILE": 400, "POST_NOT_FOUND": 404, "POST_EDIT_LOCKED": 409, "POST_NOT_EDITABLE": 409, "SELF_CLAIM_FORBIDDEN": 403, "POST_NOT_CLAIMABLE": 409, "ACTIVE_CLAIM_EXISTS": 409, "CLAIM_NOT_FOUND": 404, "CLAIM_NOT_PENDING": 409, "CLAIM_ACCEPT_CONFLICT": 409, "CLAIM_STATE_INVALID": 409, "CLAIM_NOT_COMPLETED": 409, "RESOLVE_NOT_OWNER": 403, "RESOLVE_ALREADY_RESOLVED": 409, "RESOLVE_CATEGORY_MISMATCH": 400, "HANDOVER_PAUSED_BY_DISPUTE": 409, "HANDOVER_NOT_PARTICIPANT": 404, "LEAD_NOT_FOUND": 404, "POST_NOT_LOST": 409, "DISPUTE_NOT_FOUND": 404, "DISPUTE_OPEN_EXISTS": 409, "DISPUTE_NOT_OPEN": 409, "OK": 200}.get(d.get("code"))
if expected is None or int(status)!=expected:
 print("HTTP/code mismatch: "+status+" / "+str(d.get("code")),file=sys.stderr);sys.exit(1)
print(body)'
}
post(){
  local p=$1 t=$2 d="${3:-}"; local args=(-X POST "$BASE$p")
  [ -z "$t" ] || args+=(-H "Authorization: Bearer $t")
  [ -z "$d" ] || args+=(-H "Content-Type: application/json" -d "$d")
  http "${args[@]}"
}
get(){ local args=("$BASE$1"); [ -z "${2:-}" ] || args+=(-H "Authorization: Bearer $2"); http "${args[@]}"; }
has_item(){ local id=$1 key=${2:-id}; ID="$id" KEY="$key" python -c 'import sys,json,os;d=json.load(sys.stdin)["data"];items=d.get("items",[]) if isinstance(d,dict) else d;print("yes" if any(str(x.get(os.environ["KEY"]))==os.environ["ID"] for x in items) else "no")'; }

echo "== smoke run #$RUN =="

# 1 登录三个测试用户
TA=$(post /auth/mock/login "" "{\"testUser\":\"smoke-$RUN-a\",\"nickname\":\"SmokeA\"}" | jfield accessToken)
TB=$(post /auth/mock/login "" "{\"testUser\":\"smoke-$RUN-b\",\"nickname\":\"SmokeB\"}" | jfield accessToken)
TC=$(post /auth/mock/login "" "{\"testUser\":\"smoke-$RUN-c\",\"nickname\":\"SmokeC\"}" | jfield accessToken)
[ -n "$TA" ] && [ -n "$TB" ] && [ -n "$TC" ] && expect OK OK "three mock logins" || expect OK "missing-token" "three mock logins"

# 2 权限
expect FORBIDDEN "$(get /admin/users "$TA" | jcode)" "TC-AUTH-01 user->admin 403"
# 3 发布
FPID=$(post /posts "$TB" "{\"type\":\"FOUND\",\"title\":\"Found wallet $RUN\",\"category\":\"wallet\",\"publicDescription\":\"black wallet\",\"campus\":\"S\",\"eventLocation\":\"Lib\",\"eventTime\":\"2026-09-27T14:00:00Z\",\"imageFileIds\":[]}" | jfield id)
LPID=$(post /posts "$TA" "{\"type\":\"LOST\",\"title\":\"Lost wallet $RUN\",\"category\":\"wallet\",\"publicDescription\":\"my black wallet\",\"campus\":\"S\",\"eventLocation\":\"Lib\",\"eventTime\":\"2026-09-27T13:00:00Z\",\"imageFileIds\":[]}" | jfield id)
[ -n "$FPID" ] && expect OK OK "publish FOUND+LOST" || expect OK missing "publish FOUND+LOST"
# 4 搜索与匹配
expect OK "$(get "/posts/search?keyword=wallet&type=FOUND" "$TA" | jcode)" "FR-SEARCH-01 combined search"
M=$(get "/posts/$LPID/matches" "$TA"); expect OK "$(echo "$M" | jcode)" "FR-MATCH opposite-type candidates"
echo "$M" | grep -q "reasons" && expect OK OK "match returns reasons" || expect OK missing "match returns reasons"
# 5 认领
expect SELF_CLAIM_FORBIDDEN "$(post "/posts/$FPID/claims" "$TB" '{"description":"mine","evidenceFileIds":[]}' | jcode)" "TC-CLAIM-01 self claim"
CID=$(post "/posts/$FPID/claims" "$TA" '{"description":"has my card","evidenceFileIds":[]}' | jfield id)
expect ACTIVE_CLAIM_EXISTS "$(post "/posts/$FPID/claims" "$TA" '{"description":"again","evidenceFileIds":[]}' | jcode)" "TC-CLAIM-02 duplicate"
[ -n "$CID" ] && expect OK OK "claim submitted" || expect OK missing "claim submitted"
expect CLAIM_NOT_FOUND "$(get "/claims/$CID" "$TC" | jcode)" "NFR-SEC-01 stranger 404"
# 6 审核/交接
post "/claims/$CID/review" "$TB" '{"decision":"ACCEPT","reason":"ok"}' > /dev/null
expect HANDOVER "$(get "/posts/$FPID" "$TB" | python -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")" "accept -> post HANDOVER"
post "/claims/$CID/messages" "$TA" '{"body":"meet 3pm"}' > /dev/null
expect CLAIM_NOT_FOUND "$(get "/claims/$CID/messages" "$TC" | jcode)" "FR-MSG-01 stranger 404"
# 6b 收到的申请聚合（B10）
expect yes "$(get "/users/me/received-claims" "$TB" | has_item "$CID")" "B10 received-claims contains submitted claim"
# 7 争议暂停
DID=$(post "/claims/$CID/disputes" "$TA" '{"reason":"WRONG_ITEM","description":"not mine"}' | jfield id)
expect HANDOVER_PAUSED_BY_DISPUTE "$(post "/claims/$CID/confirmations" "$TA" | jcode)" "TC-DISPUTE-01 pause"
# 8 管理员裁决
AT=$(post /admin/auth/login "" "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jfield accessToken)
expect FORBIDDEN "$(post "/admin/disputes/$DID/resolution" "$TA" '{"resolutionType":"CONTINUE","resolutionNote":"x"}' | jcode)" "user cannot resolve"
expect OK "$(post "/admin/disputes/$DID/assign" "$AT" '' | jcode)" "B9 admin assign dispute"
expect OK "$(post "/admin/disputes/$DID/resolution" "$AT" '{"resolutionType":"CONTINUE","resolutionNote":"verified"}' | jcode)" "FR-DISPUTE-02 resolve CONTINUE"
# 9 双向确认
S=$(post "/claims/$CID/confirmations" "$TA"); expect WAITING_HANDOVER "$(echo "$S" | jfield claimStatus)" "TC-HANDOVER-01 one-side"
S=$(post "/claims/$CID/confirmations" "$TA"); expect WAITING_HANDOVER "$(echo "$S" | jfield claimStatus)" "TC-HANDOVER-02 idempotent"
S=$(post "/claims/$CID/confirmations" "$TB"); expect COMPLETED "$(echo "$S" | jfield claimStatus)" "both confirmed -> COMPLETED"
expect COMPLETED "$(get "/posts/$FPID" "$TB" | python -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")" "post COMPLETED"
# 9b V4 闭环：认领完成后，申请人把自己的寻物帖(LPID)关联到本次认领 -> 寻物帖『已找回』
HASLPID=$(get "/claims/$CID/resolved-candidates" "$TA" | LPID="$LPID" python -c "import sys,json,os;d=json.load(sys.stdin);print('yes' if int(os.environ['LPID']) in [i['id'] for i in d['data']['items']] else 'no')" 2>/dev/null)
expect yes "$HASLPID" "V4 resolved-candidates include LPID"
post "/claims/$CID/resolve-lost" "$TA" "{\"lostPostId\":$LPID}" > /dev/null
expect COMPLETED "$(get "/posts/$LPID" "$TA" | python -c "import sys,json;print(json.load(sys.stdin)['data']['status'])")" "V4 resolve-lost -> LOST COMPLETED"
# 10 mark-found / 审计 / 限制（LPID 已由 resolve-lost 闭环，这里另发一条未关联的 LOST 验证手动标记）
LPID2=$(post /posts "$TA" "{\"type\":\"LOST\",\"title\":\"Lost umbrella $RUN\",\"category\":\"umbrella\",\"publicDescription\":\"my umbrella\",\"campus\":\"S\",\"eventLocation\":\"Lib\",\"eventTime\":\"2026-09-27T13:00:00Z\",\"imageFileIds\":[]}" | jfield id)
expect OK "$(post "/posts/$LPID2/mark-found" "$TA" | jcode)" "TC-POST-05 mark found"
expect yes "$(get "/admin/audit-logs?action=DISPUTE_RESOLVE&targetType=DISPUTE&targetId=$DID" "$AT" | has_item "$DID" targetId)" "FR-AUDIT-01 exact dispute resolution audit"
expect yes "$(get "/admin/audit-logs?action=CLAIM_REVIEW&targetType=CLAIM&targetId=$CID" "$AT" | has_item "$CID" targetId)" "B1 exact claim-review audit"
UIDC=$(get /users/me "$TC" | jfield id)
post "/admin/users/$UIDC/restrictions" "$AT" '{"reason":"smoke"}' > /dev/null
expect USER_RESTRICTED "$(post /posts "$TC" "{\"type\":\"LOST\",\"title\":\"r\",\"category\":\"o\",\"publicDescription\":\"x\",\"eventTime\":\"2026-09-27T10:00:00Z\",\"imageFileIds\":[]}" | jcode)" "TC-ADMIN-02 restricted publish"
# B2：限制参与者 A -> 留言应被拒 -> 解除限制
UIDA=$(get /users/me "$TA" | jfield id)
post "/admin/users/$UIDA/restrictions" "$AT" '{"reason":"smoke-msg"}' > /dev/null
expect USER_RESTRICTED "$(post "/claims/$CID/messages" "$TA" '{"body":"hi"}' | jcode)" "B2 restricted participant message"
post "/admin/users/$UIDA/unrestrict" "$AT" '{"reason":"smoke"}' > /dev/null
# 11 私密文件
SMOKE_IMAGE=$(mktemp "${TMPDIR:-/tmp}/clf-smoke.XXXXXX.png")
trap 'rm -f -- "$SMOKE_IMAGE"' EXIT
python -c "import sys;open(sys.argv[1],'wb').write(b'\x89PNG\r\n\x1a\n'+b'\x00'*56)" "$SMOKE_IMAGE"
SMOKE_IMAGE_NATIVE=$(python -c "import os,sys;print(os.path.abspath(sys.argv[1]).replace(chr(92),'/'))" "$SMOKE_IMAGE")
FID=$(http -X POST "$BASE/files" -H "Authorization: Bearer $TA" -F "file=@$SMOKE_IMAGE_NATIVE;type=image/png" -F "purpose=PRIVATE_CLAIM" | jfield fileId)
curl -s -o /dev/null -w "%{http_code}" "$BASE/files/$FID" -H "Authorization: Bearer $TC" | grep -q 404 && expect OK OK "TC-FILE-01 stranger 404" || expect OK bad "TC-FILE-01 stranger 404"
curl -s -o /dev/null -w "%{http_code}" "$BASE/files/$FID" -H "Authorization: Bearer $TA" | grep -q 200 && expect OK OK "owner fetch 200" || expect OK bad "owner fetch 200"
# 12 错误码
curl -s -o /dev/null -w "%{http_code}" "$BASE/nonexistent" | grep -q 404 && expect OK OK "B5 unknown route 404" || expect OK bad "B5 unknown route 404"
# 13 会话生命周期（B3）
NT=$(post /auth/refresh "$TA" | jfield accessToken)
[ -n "$NT" ] && [ "$NT" != "$TA" ] && expect OK OK "B3 refresh issues distinct token" || expect OK missing "B3 refresh issues distinct token"
expect UNAUTHENTICATED "$(get /users/me "$TA" | jcode)" "refresh revokes old session"
expect OK "$(get /users/me "$NT" | jcode)" "refreshed token works"
post /auth/logout "$NT" > /dev/null
expect UNAUTHENTICATED "$(get /users/me "$NT" | jcode)" "B3 logout revokes session"

echo "== RESULT: PASS $PASS / FAIL $FAIL =="
[ "$FAIL" = "0" ]
