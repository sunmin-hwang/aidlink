ALTER TABLE policy
    ADD COLUMN required_documents TEXT NOT NULL DEFAULT '정보 없음';

UPDATE policy
SET required_documents = CASE external_id
    WHEN 'SAMPLE-SEOUL-HOUSING' THEN '신분증, 주민등록등본, 임대차계약서'
    WHEN 'SAMPLE-JOB-START' THEN '구직활동계획서'
    WHEN 'SAMPLE-WORK-SETTLEMENT' THEN '재직증명서, 근로계약서'
    WHEN 'SAMPLE-NEWLYWED-HOUSING' THEN '혼인관계증명서, 주민등록등본, 소득증빙서류'
    WHEN 'SAMPLE-MANUAL-CARE' THEN '상담 후 안내'
    ELSE required_documents
END;
