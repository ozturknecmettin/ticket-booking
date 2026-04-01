package com.workshop;

import com.workshop.security.CorrelationFilter;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@SpringBootApplication
public class UiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);
        // Forward X-Correlation-ID to all downstream service calls
        restTemplate.setInterceptors(List.of((request, body, execution) -> {
            String correlationId = MDC.get(CorrelationFilter.MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        }));
        return restTemplate;
    }
}
