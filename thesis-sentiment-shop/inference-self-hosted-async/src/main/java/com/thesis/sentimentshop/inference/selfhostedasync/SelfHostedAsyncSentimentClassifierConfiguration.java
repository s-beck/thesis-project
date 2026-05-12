package com.thesis.sentimentshop.inference.selfhostedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.EnumSet;
import java.util.Set;

@Configuration
@EnableRabbit
public class SelfHostedAsyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES =
            EnumSet.of(FailureMode.UNREACHABLE, FailureMode.UNKNOWN);

    private static final String VARIANT_NAME = "S-Async";

    @Bean
    public DirectExchange sentimentExchange(
            @Value("${sentiment.async.exchange:sentiment.exchange}") String name) {
        return new DirectExchange(name, /* durable */ true, /* autoDelete */ false);
    }

    @Bean
    public DirectExchange sentimentDeadLetterExchange(
            @Value("${sentiment.async.dlx:sentiment.dlx}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue sentimentRequestsQueue(
            @Value("${sentiment.async.requests-queue:sentiment.requests}") String name,
            @Value("${sentiment.async.dlx:sentiment.dlx}") String dlxName,
            @Value("${sentiment.async.requests-dlq-routing-key:requests.dlq}") String dlqRoutingKey,
            @Value("${sentiment.async.delivery-limit:3}") int deliveryLimit) {
        return QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .withArgument("x-delivery-limit", deliveryLimit)
                .build();
    }

    @Bean
    public Queue sentimentRequestsDlq(
            @Value("${sentiment.async.requests-dlq:sentiment.requests.dlq}") String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .build();
    }

    @Bean
    public Queue sentimentResultsQueue(
            @Value("${sentiment.async.results-queue:sentiment.results}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    public Binding sentimentRequestsBinding(
            Queue sentimentRequestsQueue,
            DirectExchange sentimentExchange,
            @Value("${sentiment.async.requests-routing-key:requests}") String routingKey) {
        return BindingBuilder.bind(sentimentRequestsQueue).to(sentimentExchange).with(routingKey);
    }

    @Bean
    public Binding sentimentResultsBinding(
            Queue sentimentResultsQueue,
            DirectExchange sentimentExchange,
            @Value("${sentiment.async.results-routing-key:results}") String routingKey) {
        return BindingBuilder.bind(sentimentResultsQueue).to(sentimentExchange).with(routingKey);
    }

    @Bean
    public Binding sentimentRequestsDlqBinding(
            Queue sentimentRequestsDlq,
            DirectExchange sentimentDeadLetterExchange,
            @Value("${sentiment.async.requests-dlq-routing-key:requests.dlq}") String routingKey) {
        return BindingBuilder.bind(sentimentRequestsDlq).to(sentimentDeadLetterExchange).with(routingKey);
    }

    @Bean
    public MessageConverter sentimentMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate sentimentRabbitTemplate(ConnectionFactory connectionFactory,
                                                  MessageConverter sentimentMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(sentimentMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory sentimentRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter sentimentMessageConverter,
            @Value("${sentiment.async.listener-concurrency:2}") int concurrency,
            @Value("${sentiment.async.listener-retry-attempts:3}") int retryAttempts,
            @Value("${sentiment.async.listener-retry-backoff-ms:500}") long retryBackoffMs) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(sentimentMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        factory.setRetryTemplate(buildRetryTemplate(retryAttempts, retryBackoffMs));

        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    private static RetryTemplate buildRetryTemplate(int attempts, long backoffMs) {
        RetryTemplate template = new RetryTemplate();
        SimpleRetryPolicy policy = new SimpleRetryPolicy();
        policy.setMaxAttempts(attempts);
        template.setRetryPolicy(policy);
        FixedBackOffPolicy backoff = new FixedBackOffPolicy();
        backoff.setBackOffPeriod(backoffMs);
        template.setBackOffPolicy(backoff);
        return template;
    }

    @Bean
    public AsyncSentimentClassifier selfHostedAsyncSentimentClassifier(
            RabbitTemplate sentimentRabbitTemplate,
            @Value("${sentiment.async.exchange:sentiment.exchange}") String exchange,
            @Value("${sentiment.async.requests-routing-key:requests}") String routingKey,
            @Value("${sentiment.async.publish-confirm-timeout-ms:2000}") long confirmTimeoutMs,
            @Value("${sentiment.fault-injection.enabled:true}") boolean faultInjectionEnabled) {

        AsyncSentimentClassifier core = new RabbitPublishingAsyncSentimentClassifier(
                sentimentRabbitTemplate, exchange, routingKey, confirmTimeoutMs);

        if (!faultInjectionEnabled) {
            return core;
        }
        return new FaultInjectingAsyncSentimentClassifier(core, SUPPORTED_FAILURE_MODES, VARIANT_NAME);
    }

    @Bean
    public SentimentResultListener sentimentResultListener(ObjectProvider<SentimentResultSink> sinkProvider) {
        return new SentimentResultListener(sinkProvider::getObject);
    }

    @Bean
    public DeadLetterListener sentimentDeadLetterListener(ObjectProvider<SentimentResultSink> sinkProvider) {
        return new DeadLetterListener(sinkProvider::getObject);
    }
}