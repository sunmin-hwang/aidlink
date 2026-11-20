package io.aidlink.ai.infrastructure.openai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiRuleParserProperties.class)
public class OpenAiRuleParserConfiguration {

    @Bean
    @Qualifier("openAiRestClient")
    @ConditionalOnProperty(
            prefix = "aidlink.ai.openai",
            name = "enabled",
            havingValue = "true"
    )
    RestClient openAiRestClient(RestClient.Builder builder, OpenAiRuleParserProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "OpenAI rule parser is enabled, but AIDLINK_OPENAI_API_KEY is missing"
            );
        }
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
    }
}
