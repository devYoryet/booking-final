package com.zosh.configrations;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración completa de RabbitMQ para el servicio BOOKING
 * Maneja las colas de booking y notificaciones
 */
@Configuration
public class RabbitConfig {

    // =========================================================================
    // CONVERTERS Y TEMPLATE
    // =========================================================================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        return rabbitTemplate;
    }

    // =========================================================================
    // EXCHANGES
    // =========================================================================

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange("booking.exchange");
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("notification.exchange");
    }

    // =========================================================================
    // QUEUES - Declaración explícita de todas las colas
    // =========================================================================

    @Bean
    public Queue bookingQueue() {
        return QueueBuilder.durable("booking-queue")
                .withArgument("x-dead-letter-exchange", "booking.dlx")
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("notification-queue")
                .withArgument("x-dead-letter-exchange", "notification.dlx")
                .build();
    }

    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable("payment-queue")
                .withArgument("x-dead-letter-exchange", "payment.dlx")
                .build();
    }

    // =========================================================================
    // DEAD LETTER QUEUES
    // =========================================================================

    @Bean
    public Queue bookingDeadLetterQueue() {
        return QueueBuilder.durable("booking-queue.dlq").build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable("notification-queue.dlq").build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable("payment-queue.dlq").build();
    }

    // =========================================================================
    // DEAD LETTER EXCHANGES
    // =========================================================================

    @Bean
    public DirectExchange bookingDeadLetterExchange() {
        return new DirectExchange("booking.dlx");
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange("notification.dlx");
    }

    @Bean
    public DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange("payment.dlx");
    }

    // =========================================================================
    // BINDINGS
    // =========================================================================

    @Bean
    public Binding bookingBinding() {
        return BindingBuilder
                .bind(bookingQueue())
                .to(bookingExchange())
                .with("booking.created");
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with("notification.send");
    }

    @Bean
    public Binding paymentBinding() {
        return BindingBuilder
                .bind(paymentQueue())
                .to(bookingExchange())
                .with("payment.process");
    }

    // Dead Letter Bindings
    @Bean
    public Binding bookingDeadLetterBinding() {
        return BindingBuilder
                .bind(bookingDeadLetterQueue())
                .to(bookingDeadLetterExchange())
                .with("booking-queue");
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder
                .bind(notificationDeadLetterQueue())
                .to(notificationDeadLetterExchange())
                .with("notification-queue");
    }

    @Bean
    public Binding paymentDeadLetterBinding() {
        return BindingBuilder
                .bind(paymentDeadLetterQueue())
                .to(paymentDeadLetterExchange())
                .with("payment-queue");
    }
}