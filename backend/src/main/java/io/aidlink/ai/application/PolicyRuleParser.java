package io.aidlink.ai.application;

import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleCandidate;

public interface PolicyRuleParser {

    PolicyRuleCandidate parse(PolicyQualificationText sourceText);
}
