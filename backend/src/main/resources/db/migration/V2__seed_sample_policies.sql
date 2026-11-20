INSERT INTO policy (
    id, external_id, name, provider, category, region_code, description,
    target_description, support_content, application_start, application_end,
    application_method, application_url, source_url, analysis_status
) VALUES
    (1, 'SAMPLE-SEOUL-HOUSING', '서울 청년 월세 지원', '서울특별시', '주거', 'SEOUL',
     '서울 거주 무주택 청년의 월세 부담을 지원합니다.', '만 19~34세 서울 거주 무주택 청년',
     '월 최대 20만원을 지원합니다.', '2026-01-01', '2026-12-31', '온라인 신청',
     'https://example.go.kr/apply/seoul-housing', 'https://example.go.kr/policies/seoul-housing', 'HUMAN_VERIFIED'),
    (2, 'SAMPLE-JOB-START', '청년 구직활동 지원', '고용지원공단', '취업', 'NATIONWIDE',
     '구직 중이거나 단시간 근로 중인 청년의 취업 준비를 지원합니다.', '만 18~34세 구직자 또는 주 30시간 이하 근로자',
     '취업 프로그램과 월별 활동비를 지원합니다.', '2026-01-01', '2026-12-31', '온라인 신청',
     'https://example.go.kr/apply/job-start', 'https://example.go.kr/policies/job-start', 'AUTO_VALIDATED'),
    (3, 'SAMPLE-WORK-SETTLEMENT', '청년 취업정착 지원', '지역일자리진흥원', '취업', 'NATIONWIDE',
     '취업 초기 청년의 안정적인 근속을 지원합니다.', '만 18~39세 신규 취업자',
     '6개월 근속 시 정착지원금을 지급합니다.', '2026-03-01', '2026-11-30', '고용주 확인 후 온라인 신청',
     'https://example.go.kr/apply/work-settlement', 'https://example.go.kr/policies/work-settlement', 'AI_PARSED'),
    (4, 'SAMPLE-NEWLYWED-HOUSING', '신혼부부 주거안정 지원', '주거복지공단', '주거', 'NATIONWIDE',
     '무주택 신혼부부의 주거비를 지원합니다.', '혼인 중인 무주택 가구 중 소득기준 충족 가구',
     '보증금 대출이자 일부를 지원합니다.', '2026-01-01', '2026-12-31', '관할 기관 방문 신청',
     'https://example.go.kr/apply/newlywed-housing', 'https://example.go.kr/policies/newlywed-housing', 'AUTO_VALIDATED'),
    (5, 'SAMPLE-MANUAL-CARE', '긴급 생활안정 지원', '복지지원센터', '생활', 'NATIONWIDE',
     '위기 상황 가구를 위한 긴급 지원입니다.', '기관이 지원 필요성을 인정한 가구',
     '상황에 따라 생계비를 지원합니다.', NULL, NULL, '복지지원센터 상담',
     'https://example.go.kr/apply/emergency-care', 'https://example.go.kr/policies/emergency-care', 'RAW');

INSERT INTO policy_rule (policy_id, rule_json, validation_score, status) VALUES
    (1, '{"type":"AND","children":[{"type":"CONDITION","field":"AGE","operator":"BETWEEN","expectedValues":["19","34"],"description":"만 19~34세"},{"type":"CONDITION","field":"REGION","operator":"EQ","expectedValues":["SEOUL"],"description":"서울특별시 거주"},{"type":"CONDITION","field":"PERSONAL_INCOME","operator":"LTE","expectedValues":["2500000"],"description":"월 개인소득 250만원 이하"},{"type":"CONDITION","field":"HOME_OWNERSHIP","operator":"EQ","expectedValues":["NONE"],"description":"무주택"}]}'::jsonb, 100.00, 'HUMAN_VERIFIED'),
    (2, '{"type":"AND","children":[{"type":"CONDITION","field":"AGE","operator":"BETWEEN","expectedValues":["18","34"],"description":"만 18~34세"},{"type":"OR","children":[{"type":"CONDITION","field":"EMPLOYMENT","operator":"IN","expectedValues":["JOB_SEEKER","UNEMPLOYED"],"description":"구직 또는 실업 상태"},{"type":"CONDITION","field":"WEEKLY_WORK_HOURS","operator":"LTE","expectedValues":["30"],"description":"주 30시간 이하 근로"}]},{"type":"CONDITION","field":"PERSONAL_INCOME","operator":"LTE","expectedValues":["3000000"],"description":"월 개인소득 300만원 이하"}]}'::jsonb, 92.00, 'AUTO_VALIDATED'),
    (3, '{"type":"AND","children":[{"type":"CONDITION","field":"AGE","operator":"BETWEEN","expectedValues":["18","39"],"description":"만 18~39세"},{"type":"CONDITION","field":"EMPLOYMENT","operator":"EQ","expectedValues":["EMPLOYED"],"description":"현재 취업 상태"},{"type":"CONDITION","field":"WEEKLY_WORK_HOURS","operator":"GTE","expectedValues":["20"],"description":"주 20시간 이상 근로"},{"type":"CONDITION","field":"PERSONAL_INCOME","operator":"BETWEEN","expectedValues":["2000000","4000000"],"description":"월 개인소득 200~400만원"}]}'::jsonb, 76.00, 'AI_PARSED'),
    (4, '{"type":"AND","children":[{"type":"CONDITION","field":"MARITAL_STATUS","operator":"EQ","expectedValues":["MARRIED"],"description":"혼인 상태"},{"type":"CONDITION","field":"HOME_OWNERSHIP","operator":"EQ","expectedValues":["NONE"],"description":"무주택 가구"},{"type":"CONDITION","field":"HOUSEHOLD_INCOME","operator":"LTE","expectedValues":["6000000"],"description":"가구 월소득 600만원 이하"},{"type":"OR","children":[{"type":"CONDITION","field":"REGION","operator":"EQ","expectedValues":["SEOUL"],"description":"수도권 거주"},{"type":"CONDITION","field":"REGION","operator":"EQ","expectedValues":["GYEONGGI"],"description":"수도권 거주"},{"type":"CONDITION","field":"REGION","operator":"EQ","expectedValues":["INCHEON"],"description":"수도권 거주"}]}]}'::jsonb, 90.00, 'AUTO_VALIDATED');

SELECT setval(pg_get_serial_sequence('policy', 'id'), (SELECT max(id) FROM policy));

