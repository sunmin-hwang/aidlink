package io.aidlink.collector.infrastructure.gov24;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(Gov24ApiProperties.class)
public class Gov24ApiConfiguration {

    @Bean
    @Qualifier("gov24RestClient")
    RestClient gov24RestClient(RestClient.Builder builder, Gov24ApiProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
