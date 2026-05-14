package com.thesis.sentimentshop.inference.externalasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.external.HuggingFaceSentimentClient;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

@Configuration
@EnableRabbit
public class ExternalAsyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES =
            EnumSet.of(FailureMode.UNREACHABLE, FailureMode.UNKNOWN);

    private static final String VARIANT_NAME = "X-Async";

    @Bean
    public DirectExchange sentimentExternalExchange(
            @Value("${sentiment.external-async.exchange:sentiment.external.exchange}") String name) {
        return new DirectExchange(name, /* durable */ true, /* autoDelete */ false);
    }

    @Bean
    public DirectExchange sentimentExternalDeadLetterExchange(
            @Value("${sentiment.external-async.dlx:sentiment.external.dlx}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue sentimentExternalRequestsQueue(
            @Value("${sentiment.external-async.requests-queue:sentiment.external.requests}") String name,
            @Value("${sentiment.external-async.dlx:sentiment.external.dlx}") String dlxName,
            @Value("${sentiment.external-async.requests-dlq-routing-key:requests.dlq}") String dlqRoutingKey,
            @Value("${sentiment.external-async.delivery-limit:3}") int deliveryLimit) {
        return QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .withArgument("x-delivery-limit", deliveryLimit)
                .build();
    }

    @Bean
    public Queue sentimentExternalRequestsDlq(
            @Value("${sentiment.external-async.requests-dlq:sentiment.external.requests.dlq}") String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .build();
    }

    @Bean
    public Queue sentimentExternalResultsQueue(
            @Value("${sentiment.external-async.results-queue:sentiment.external.results}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    public Binding sentimentExternalRequestsBinding(
            Queue sentimentExternalRequestsQueue,
            DirectExchange sentimentExternalExchange,
            @Value("${sentiment.external-async.requests-routing-key:requests}") String routingKey) {
        return BindingBuilder.bind(sentimentExternalRequestsQueue)
                .to(sentimentExternalExchange).with(routingKey);
    }

    @Bean
    public Binding sentimentExternalResultsBinding(
            Queue sentimentExternalResultsQueue,
            DirectExchange sentimentExternalExchange,
            @Value("${sentiment.external-async.results-routing-key:results}") String routingKey) {
        return BindingBuilder.bind(sentimentExternalResultsQueue)
                .to(sentimentExternalExchange).with(routingKey);
    }

    @Bean
    public Binding sentimentExternalRequestsDlqBinding(
            Queue sentimentExternalRequestsDlq,
            DirectExchange sentimentExternalDeadLetterExchange,
            @Value("${sentiment.external-async.requests-dlq-routing-key:requests.dlq}") String routingKey) {
        return BindingBuilder.bind(sentimentExternalRequestsDlq)
                .to(sentimentExternalDeadLetterExchange).with(routingKey);
    }

    @Bean
    public MessageConverter sentimentExternalMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate sentimentExternalRabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter sentimentExternalMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(sentimentExternalMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory sentimentExternalRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter sentimentExternalMessageConverter,
            @Value("${sentiment.external-async.listener-concurrency:2}") int concurrency,
            @Value("${sentiment.external-async.listener-retry-attempts:3}") int retryAttempts,
            @Value("${sentiment.external-async.listener-retry-backoff-ms:500}") long retryBackoffMs) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(sentimentExternalMessageConverter);
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

    private static HuggingFaceSentimentClient buildHuggingFaceClient(String endpointUrl,
                                                                     String token,
                                                                     long connectTimeoutMs,
                                                                     long readTimeoutMs) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "X-Async requires a HuggingFace API token. Set the "
                            + "HUGGINGFACE_API_TOKEN environment variable (or "
                            + "sentiment.external.token property) before "
                            + "starting under the x-async Maven profile.");
        }
        return new HuggingFaceSentimentClient(
                endpointUrl,
                token,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public AsyncSentimentClassifier externalAsyncSentimentClassifier(
            RabbitTemplate sentimentExternalRabbitTemplate,
            @Value("${sentiment.external-async.exchange:sentiment.external.exchange}") String exchange,
            @Value("${sentiment.external-async.requests-routing-key:requests}") String routingKey,
            @Value("${sentiment.external-async.publish-confirm-timeout-ms:2000}") long confirmTimeoutMs) {
        return new RabbitPublishingExternalAsyncSentimentClassifier(
                sentimentExternalRabbitTemplate, exchange, routingKey, confirmTimeoutMs);
    }

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "true"
    )
    public FaultInjectingExternalAsyncSentimentClassifier faultyExternalAsyncSentimentClassifier(
            RabbitTemplate sentimentExternalRabbitTemplate,
            @Value("${sentiment.external-async.exchange:sentiment.external.exchange}") String exchange,
            @Value("${sentiment.external-async.requests-routing-key:requests}") String routingKey,
            @Value("${sentiment.external-async.publish-confirm-timeout-ms:2000}") long confirmTimeoutMs) {
        AsyncSentimentClassifier core = new RabbitPublishingExternalAsyncSentimentClassifier(
                sentimentExternalRabbitTemplate, exchange, routingKey, confirmTimeoutMs);
        return new FaultInjectingExternalAsyncSentimentClassifier(
                core, SUPPORTED_FAILURE_MODES, VARIANT_NAME);
    }

    @Bean
    public ExternalAsyncRequestConsumer externalAsyncRequestConsumer(
            RabbitTemplate sentimentExternalRabbitTemplate,
            @Value("${sentiment.external-async.exchange:sentiment.external.exchange}") String exchange,
            @Value("${sentiment.external-async.results-routing-key:results}") String resultsRoutingKey,
            @Value("${sentiment.external.url}") String endpointUrl,
            @Value("${sentiment.external.token:}") String token,
            @Value("${sentiment.external.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${sentiment.external.read-timeout-ms:10000}") long readTimeoutMs) {

        SentimentClassifier huggingFaceClient = buildHuggingFaceClient(
                endpointUrl, token, connectTimeoutMs, readTimeoutMs);

        return new ExternalAsyncRequestConsumer(
                huggingFaceClient,
                sentimentExternalRabbitTemplate,
                exchange,
                resultsRoutingKey);
    }

    @Bean
    public ExternalSentimentResultListener externalSentimentResultListener(
            ObjectProvider<SentimentResultSink> sinkProvider) {
        return new ExternalSentimentResultListener(sinkProvider::getObject);
    }

    @Bean
    public ExternalDeadLetterListener externalDeadLetterListener(
            ObjectProvider<SentimentResultSink> sinkProvider) {
        return new ExternalDeadLetterListener(sinkProvider::getObject);
    }
}