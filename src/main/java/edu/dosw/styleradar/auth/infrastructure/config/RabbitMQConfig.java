package edu.dosw.styleradar.auth.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /** Exchange de eventos de cuenta de ms-usuarios (análisis v2 de ms-usuarios, SR-RF-66). */
    public static final String EXCHANGE = "styleradar.users";
    public static final String DLX_EXCHANGE = "styleradar.dlx";
    public static final String QUEUE = "styleradar.auth.user-status-changed.q";
    public static final String DLQ = "styleradar.auth.dlq";
    public static final String ROUTING_KEY = "user.status.changed";

    @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.password:}")
    private String rabbitPassword = "";

    /**
     * Exchanges (topic for user events, direct for dead letters), durable and declared idempotently so the queue
     * bindings never fail on a fresh broker.
     */
    @Bean
    TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange mensajesMuertosExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    Queue usuarioEstadoQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    Queue usuarioEstadoDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding usuarioEstadoBinding() {
        return new Binding(
                QUEUE,
                Binding.DestinationType.QUEUE,
                EXCHANGE,
                ROUTING_KEY,
                null);
    }

    @Bean
    Binding usuarioEstadoDlqBinding() {
        return new Binding(
                DLQ,
                Binding.DestinationType.QUEUE,
                DLX_EXCHANGE,
                DLQ,
                null);
    }

    @Bean
    Jackson2JsonMessageConverter jacksonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages(
                "edu.dosw.styleradar.*",
                "java.util",
                "java.time"
        );
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        factory.setAutoStartup(org.springframework.util.StringUtils.hasText(rabbitPassword));
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
