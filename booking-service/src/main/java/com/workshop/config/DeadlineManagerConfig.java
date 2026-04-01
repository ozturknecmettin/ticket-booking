package com.workshop.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class DeadlineManagerConfig {

    @Bean
    @ConditionalOnMissingBean(DeadlineManager.class)
    public DeadlineManager deadlineManager(Configuration axonConfiguration) {
        return SimpleDeadlineManager.builder()
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(axonConfiguration))
                .transactionManager(axonConfiguration.getComponent(
                        org.axonframework.common.transaction.TransactionManager.class,
                        org.axonframework.common.transaction.NoTransactionManager::instance))
                .build();
    }
}
