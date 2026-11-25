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
@lombok.extern.slf4j.Slf4j
public class RabbitMQConfig {

    /** Exchange único del curso para eventos de dominio (TopicExchange, durable). */
    public static final String EXCHANGE = "styleradar.exchange";
    public static final String DLX_EXCHANGE = "styleradar.dlx";
    public static final String QUEUE = "styleradar.auth.user-status-changed.q";
    public static final String DLQ = "styleradar.auth.dlq";
    public static final String ROUTING_KEY = "user.status.changed";
    /** Cola de eventos que revocan todas las sesiones: contraseña cambiada y cuenta eliminada. */
    public static final String QUEUE_CUENTA = "styleradar.auth.user-account-changed.q";
    public static final String ROUTING_KEY_CONTRASENA = "user.password.changed";
    /** Routing key actual de ms-usuarios para la cuenta eliminada; también se escucha {@code user.deleted}. */
    public static final String ROUTING_KEY_CUENTA_ELIMINADA = "styleradar.usuarios.cuenta.eliminada";
    public static final String ROUTING_KEY_USUARIO_ELIMINADO = "user.deleted";

    @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.password:}")
    private String rabbitPassword = "";

    /**
     * Deja en el log si el consumidor de user.status.changed quedó activo. Sin RABBITMQ_PASSWORD el listener no
     * arranca y la cola no se declara en el broker, así que ms-auth no recibe los cambios de estado de cuenta.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void registrarEstadoConsumidor() {
        if (consumidorActivo()) {
            log.info("RabbitMQ: consumidor activo en la cola {} (exchange {}, routing key {})", QUEUE, EXCHANGE, ROUTING_KEY);
            log.info("RabbitMQ: consumidor activo en la cola {} (routing keys {}, {} y {})", QUEUE_CUENTA,
                    ROUTING_KEY_CONTRASENA, ROUTING_KEY_CUENTA_ELIMINADA, ROUTING_KEY_USUARIO_ELIMINADO);
        } else {
            log.warn("RabbitMQ: consumidor DESACTIVADO porque falta RABBITMQ_PASSWORD; la cola {} no se declara y "
                    + "ms-auth no recibe {} de {}", QUEUE, ROUTING_KEY, EXCHANGE);
        }
    }

    boolean consumidorActivo() {
        return org.springframework.util.StringUtils.hasText(rabbitPassword);
    }

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
    Queue cuentaUsuarioQueue() {
        return QueueBuilder.durable(QUEUE_CUENTA)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    Binding contrasenaCambiadaBinding() {
        return new Binding(QUEUE_CUENTA, Binding.DestinationType.QUEUE, EXCHANGE, ROUTING_KEY_CONTRASENA, null);
    }

    @Bean
    Binding cuentaEliminadaBinding() {
        return new Binding(QUEUE_CUENTA, Binding.DestinationType.QUEUE, EXCHANGE, ROUTING_KEY_CUENTA_ELIMINADA, null);
    }

    @Bean
    Binding usuarioEliminadoBinding() {
        return new Binding(QUEUE_CUENTA, Binding.DestinationType.QUEUE, EXCHANGE, ROUTING_KEY_USUARIO_ELIMINADO, null);
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
        factory.setAutoStartup(consumidorActivo());
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
