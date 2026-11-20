package io.aidlink.collector.application;

import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.PolicySource;

public interface PolicySourceClient {

    PolicySource source();

    CollectedPolicyPage fetchPage(int page, int perPage);
}
