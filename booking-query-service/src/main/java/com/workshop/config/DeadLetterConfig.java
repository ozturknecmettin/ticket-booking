package com.workshop.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeadLetterConfig {

    @Bean
    public ConfigurerModule bookingQueryDeadLetterQueueConfigurerModule() {
        return configurer -> configurer.eventProcessing(processing -> {
            processing.registerDeadLetterQueue(
                    "booking-projections",
                    config -> JpaSequencedDeadLetterQueue.builder()
                            .processingGroup("booking-projections")
                            .maxSequences(256)
                            .maxSequenceSize(256)
                            .entityManagerProvider(config.getComponent(EntityManagerProvider.class))
                            .transactionManager(config.getComponent(TransactionManager.class))
                            .serializer(config.serializer())
                            .build()
            );
            processing.registerListenerInvocationErrorHandler(
                    "booking-projections",
                    config -> new RetryingListenerErrorHandler()
            );
        });
    }
}
