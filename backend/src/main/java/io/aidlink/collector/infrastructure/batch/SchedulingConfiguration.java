package io.aidlink.collector.infrastructure.batch;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("postgres")
@EnableScheduling
public class SchedulingConfiguration {
}
