package io.aidlink.ai.infrastructure.openai;

import io.aidlink.ai.application.PolicyRuleParser;
import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "aidlink.ai.openai",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledPolicyRuleParser implements PolicyRuleParser {

    @Override
    public PolicyRuleCandidate parse(PolicyQualificationText sourceText) {
        throw new IllegalStateException(
                "AI rule parsing is disabled. Set AIDLINK_OPENAI_ENABLED=true and provide an API key."
        );
    }
}
