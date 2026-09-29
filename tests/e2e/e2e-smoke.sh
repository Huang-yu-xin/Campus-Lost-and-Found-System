#!/usr/bin/env bash
set -u
BASE="${BASE:-http://localhost:8080/api/v1}"
ADMIN_USER="${ADMIN_USER:?set ADMIN_USER}"
ADMIN_PASS="${ADMIN_PASS:?set ADMIN_PASS}"
RUN=$(date +%s)
PASS=0; FAIL=0

jcode(){ python -c "import sys,json;print(json.load(sys.stdin).get('code'))" 2>/dev/null; }
jfield(){ python -c "import sys,json;print(json.load(sys.stdin)['data']['$1'])" 2>/dev/null; }
expect(){ local e="$1" a="${2:-}" l="${3:-}"; if [ "$a" = "$e" ]; then PASS=$((PASS+1)); echo "PASS  $l"; else FAIL=$((FAIL+1)); echo "FAIL  $l (expect $e, got '$a')"; fi; }
post(){ local p=$1 t=$2 d="${3:-}"; if [ -n "$d" ]; then curl -s -X POST "$BASE$p" ${t:+-H "Authorization: Bearer $t"} -H "Content-Type: application/json" -d "$d"; else curl -s -X POST "$BASE$p" ${t:+-H "Authorization: Bearer $t"}; fi; }
get(){ curl -s "$BASE$1" ${2:+-H "Authorization: Bearer $2"}; }

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
expect OK "$(get "/users/me/received-claims" "$TB" | jcode)" "B10 received-claims"
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
# 10 mark-found / 审计 / 限制
expect OK "$(post "/posts/$LPID/mark-found" "$TA" | jcode)" "TC-POST-05 mark found"
expect OK "$(get "/admin/audit-logs?action=DISPUTE_RESOLVE" "$AT" | jcode)" "FR-AUDIT-01 audit filter"
expect OK "$(get "/admin/audit-logs?action=CLAIM_REVIEW" "$AT" | jcode)" "B1 claim-review audit"
UIDC=$(get /users/me "$TC" | jfield id)
post "/admin/users/$UIDC/restrictions" "$AT" '{"reason":"smoke"}' > /dev/null
expect USER_RESTRICTED "$(post /posts "$TC" "{\"type\":\"LOST\",\"title\":\"r\",\"category\":\"o\",\"publicDescription\":\"x\",\"eventTime\":\"2026-09-27T10:00:00Z\",\"imageFileIds\":[]}" | jcode)" "TC-ADMIN-02 restricted publish"
# B2：限制参与者 A -> 留言应被拒 -> 解除限制
UIDA=$(get /users/me "$TA" | jfield id)
post "/admin/users/$UIDA/restrictions" "$AT" '{"reason":"smoke-msg"}' > /dev/null
expect USER_RESTRICTED "$(post "/claims/$CID/messages" "$TA" '{"body":"hi"}' | jcode)" "B2 restricted participant message"
post "/admin/users/$UIDA/unrestrict" "$AT" '{"reason":"smoke"}' > /dev/null
# 11 私密文件
python -c "open('smoke.png','wb').write(b'\x89PNG\r\n\x1a\n'+b'\x00'*56)"
FID=$(curl -s -X POST "$BASE/files" -H "Authorization: Bearer $TA" -F "file=@smoke.png;type=image/png" -F "purpose=PRIVATE_CLAIM" | jfield fileId)
curl -s -o /dev/null -w "%{http_code}" "$BASE/files/$FID" -H "Authorization: Bearer $TC" | grep -q 404 && expect OK OK "TC-FILE-01 stranger 404" || expect OK bad "TC-FILE-01 stranger 404"
curl -s -o /dev/null -w "%{http_code}" "$BASE/files/$FID" -H "Authorization: Bearer $TA" | grep -q 200 && expect OK OK "owner fetch 200" || expect OK bad "owner fetch 200"
rm -f smoke.png
# 12 错误码
curl -s -o /dev/null -w "%{http_code}" "$BASE/nonexistent" | grep -q 404 && expect OK OK "B5 unknown route 404" || expect OK bad "B5 unknown route 404"
# 13 会话生命周期（B3）
NT=$(post /auth/refresh "$TA" | jfield accessToken)
[ -n "$NT" ] && expect OK OK "B3 refresh issues new token" || expect OK missing "B3 refresh issues new token"
post /auth/logout "$TA" > /dev/null
expect UNAUTHENTICATED "$(get /users/me "$TA" | jcode)" "B3 logout revokes session"

echo "== RESULT: PASS $PASS / FAIL $FAIL =="
[ "$FAIL" = "0" ]
