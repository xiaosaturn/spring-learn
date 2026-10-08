package org.masterh.rabbitmqdemo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RetryListenerConfiguration {
    public static final int MAX_ATTEMPTS = 3;

    @Bean
    SimpleRabbitListenerContainerFactory retryListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(buildInterceptor());
        return factory;
    }

    public static MethodInterceptor buildInterceptor() {
        return RetryInterceptorBuilder.stateless()
                // Spring AMQP 4 counts retries after the initial attempt.
                .maxRetries(MAX_ATTEMPTS - 1)
                .backOffOptions(500, 2.0, 1000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
