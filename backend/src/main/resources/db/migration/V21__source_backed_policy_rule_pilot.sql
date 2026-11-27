-- Bounded source-backed pilot; drafts only, never HUMAN_VERIFIED.
-- Reapplicable using psql --single-transaction -v ON_ERROR_STOP=1 -f this-file.
-- Missing/changed/reviewed policies are skipped; inspect NOTICEs during deployment.
-- External IDs are portable; local policy/user IDs and private profiles are not included.
DO $pilot$
DECLARE
    seed jsonb;
    current_policy policy%ROWTYPE;
    applied integer := 0;
BEGIN
    FOR seed IN SELECT value FROM jsonb_array_elements($seeds$
[
  {
    "externalId": "GOV24:453000000196",
    "snapshot": {
      "name": "지역사랑상품권(서산사랑상품권)",
      "provider": "충청남도 서산시",
      "region_code": "CHUNGNAM",
      "description": "서산사랑상품권 구매자에게 구매 시 10% 할인 지원",
      "target_description": "지원대상: ○ 만 19세 이상",
      "support_content": "○ 상품권 할인율: 상시, 명절 10%\r\n○ 1인 월 구입한도액: 상시 40만원/ 명절 50만원",
      "application_method": "○ 지류상품권 : 방문 구입\r\n    - 관내 농협, 새마을금고, 신협, 하나은행, 산림조합, 서산우체국, 수협\r\n○ 모바일상품권 : 지역상품권 chak 어플에서 구입\n\n신청기한: 매월 선착순 판매",
      "required_documents": "신분증",
      "source_url": "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=1005047&bnefType=all&svcId=453000000196",
      "application_url": "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=1005047&bnefType=all&svcId=453000000196",
      "application_period_raw": "매월 선착순 판매"
    },
    "rule": {
      "type": "AND",
      "children": [
        {
          "type": "CONDITION",
          "field": "AGE",
          "operator": "GTE",
          "expectedValues": [
            "19"
          ],
          "description": "만 19세 이상",
          "sourceText": "만 19세 이상"
        },
        {
          "type": "CONDITION",
          "field": "MANUAL_REVIEW",
          "operator": "REQUIRED",
          "expectedValues": [],
          "description": "신분증 지참·본인 구매 여부와 선착순 판매 잔여분 확인",
          "sourceText": "매월 선착순 판매"
        }
      ]
    },
    "evidenceUrl": "https://www.seosan.go.kr/www/contents.do?key=5012",
    "reviewNote": "서산시 이용규약의 본인 구매·신분증 조건은 현재 프로필로 판정할 수 없다. 지역은 서비스 제공 지역이며 거주 자격 조건으로 추가하지 않는다. 할인율·한도·잔여분은 별도 확인한다.",
    "preparedBy": "codex-local-pilot",
    "preparedOn": "2026-11-27",
    "expectedStatus": "REVIEW_REQUIRED"
  },
  {
    "externalId": "GOV24:B55273500017",
    "snapshot": {
      "name": "창업에듀",
      "provider": "창업진흥원",
      "region_code": "NATIONWIDE",
      "description": "전국민 대상 창업 교육 제공",
      "target_description": "지원대상: 전국민",
      "support_content": "전국민 대상 창업 교육 제공",
      "application_method": "○ 온라인 신청 \r\n-누리집:www.k-startup.go.kr/edu 회원가입후진행\n\n신청기한: 상시신청",
      "required_documents": "해당없음",
      "source_url": "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=2150422&bnefType=all&svcId=B55273500017",
      "application_url": "http://www.k-startup.go.kr/edu",
      "application_period_raw": "상시신청"
    },
    "rule": {
      "type": "CONDITION",
      "field": "MANUAL_REVIEW",
      "operator": "REQUIRED",
      "expectedValues": [],
      "description": "K-Startup 회원가입·수강할 강좌의 이용 조건 확인",
      "sourceText": "회원가입후진행"
    },
    "evidenceUrl": "https://www.k-startup.go.kr/edu/",
    "reviewNote": "전국민 대상 온라인 교육. 나이·소득 제한을 임의로 만들지 않는다. 외부 회원가입·개별 강좌 이용 여부는 프로필에서 확인할 수 없다.",
    "preparedBy": "codex-local-pilot",
    "preparedOn": "2026-11-27",
    "expectedStatus": "REVIEW_REQUIRED"
  },
  {
    "externalId": "GOV24:B55370100033",
    "snapshot": {
      "name": "서민금융진흥원 금융교육",
      "provider": "서민금융진흥원",
      "region_code": "NATIONWIDE",
      "description": "서민·취약계층 등이 금융 소비자로서 적절한 의사결정을 수행할 수 있도록 경제·금융교육 실시",
      "target_description": "지원대상: 전국민",
      "support_content": "(재무설계) 재무설계 필요성, 재무설계 방법, 연령대별 재무설계 등\r\n(저축과 소비) 금융상품 선택법과 합리적인 소비관리\r\n(부채관리) 대출상품 선택방법, 부채수준 판단 등\r\n(신용관리) 신용관리의 필요성 및 신용관리 방법\r\n(금융사기예방) 불법추심, 불법사금융, 금융사기 예방\r\n※ (체험형 교육) 고령층 대상 금융사기예방 게임북(노다지)\r\n(서민금융제도 이해) 서민금융제도 및 채무자 지원제도\r\n(생활·복지) 주택 및 일자리 지원제도, 생활위기와 생계유지 문제 지원제도\r\n(창업금융) 창업지원 금융서비스 지원제도 등\r\n(금융생활) 청소년 진로·금융생활 보드게임(초·중학생)\r\n(디지털 금융교육) 고령층 대상 키오스크, 은행업무, 페이결제 등",
      "application_method": "교육수요기관이 금융교육포털(edu.kinfa.or.kr)에서 원하는 주제로 교육신청 후 수강\r\nㅇ온라인교육 : 금융교육포털에 접속 후, 무료 수강(PC, 모바일 모두 이용 가능)\r\nㅇ방문교육 : 강사가 방문하여 교육 실시\r\n※ 단, 교육 대상 인원 20명 이상 시 개설 가능\r\nㅇ 온택트교육 : 강사가 화상강의시스템 또는 메타버스 플랫폼을 통해 실시간 금융교육\r\n- 공개교육 : 정해진 교육일정, 주제로 교육 신청\r\n- 기관교육 : 희망하는 일정, 주제를 자유롭게 신청\r\n※ 단, 교육 대상 인원 20명 이상 시 개설 가능\n\n신청기한: 상시신청",
      "required_documents": "해당없음",
      "source_url": "https://plus.gov.kr/portal/benefitV2/benefitTotalSrvcList/benefitSrvcDtl?svcSeq=2149520&bnefType=all&svcId=B55370100033",
      "application_url": "http://edu.kinfa.or.kr",
      "application_period_raw": "상시신청"
    },
    "rule": {
      "type": "CONDITION",
      "field": "MANUAL_REVIEW",
      "operator": "REQUIRED",
      "expectedValues": [],
      "description": "온라인·방문·온택트 교육 방식과 단체 인원 요건 확인",
      "sourceText": "교육 대상 인원 20명 이상 시 개설 가능"
    },
    "evidenceUrl": "https://www.kinfa.or.kr/financialLife/financialEducation.do",
    "reviewNote": "온라인 무료 교육과 방문·기관교육을 구분한다. 단체 20명 조건을 개인의 가구원수로 변환하지 않는다. 교육 방식이 프로필에 없어 확인 필요로 보존한다.",
    "preparedBy": "codex-local-pilot",
    "preparedOn": "2026-11-27",
    "expectedStatus": "REVIEW_REQUIRED"
  }
]
$seeds$::jsonb)
    LOOP
        SELECT * INTO current_policy FROM policy
        WHERE external_id = seed->>'externalId' FOR UPDATE;
        IF NOT FOUND THEN
            RAISE NOTICE 'policy pilot: missing %', seed->>'externalId';
            CONTINUE;
        END IF;
        IF current_policy.lifecycle_status <> 'ACTIVE'
           OR current_policy.analysis_status <> 'RAW'
           OR NOT (to_jsonb(current_policy) @> (seed->'snapshot'))
           OR EXISTS (SELECT 1 FROM policy_rule WHERE policy_id = current_policy.id)
           OR EXISTS (SELECT 1 FROM policy_rule_candidate WHERE policy_id = current_policy.id)
           OR EXISTS (SELECT 1 FROM policy_rule_version WHERE policy_id = current_policy.id)
        THEN
            RAISE NOTICE 'policy pilot: unchanged/reviewed/conflicting %; preserved', seed->>'externalId';
            CONTINUE;
        END IF;
        INSERT INTO policy_rule(policy_id, rule_json, status)
        VALUES (current_policy.id, seed->'rule', 'REVIEW_REQUIRED');
        UPDATE policy SET analysis_status = 'REVIEW_REQUIRED', updated_at = CURRENT_TIMESTAMP
        WHERE id = current_policy.id;
        INSERT INTO admin_review(policy_id, reason, priority_score, status)
        VALUES (current_policy.id, 'UNSUPPORTED_EXPRESSION', 50, 'OPEN')
        ON CONFLICT (policy_id, reason) WHERE status IN ('OPEN', 'IN_PROGRESS') DO NOTHING;
        applied := applied + 1;
        RAISE NOTICE 'policy pilot: draft applied %', seed->>'externalId';
    END LOOP;
    RAISE NOTICE 'policy pilot: % drafts applied (maximum 3)', applied;
END
$pilot$;
