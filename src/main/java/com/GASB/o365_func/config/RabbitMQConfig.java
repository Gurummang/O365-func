package com.GASB.o365_func.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    private final RabbitMQProperties properties;


    public RabbitMQConfig(RabbitMQProperties properties) {
        this.properties = properties;
    }


    //역직렬화 설정
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
    // 큐 설정
    @Bean
    public Queue fileQueue() {
        return new Queue(properties.getFileQueue(), true, false, false);
    }

    @Bean
    public Queue vtReportQueue() {
        return new Queue(properties.getVtReportQueue(), true, false, false);
    }

    @Bean
    public Queue vtUploadQueue() {
        return new Queue(properties.getVtUploadQueue(), true, false, false);
    }

    @Bean
    public Queue groupingQueue() {
        return new Queue(properties.getGroupingQueue(),true, false,false);
    }

    @Bean
    public Queue O365InitQueue() {
        return new Queue(properties.getO365InitQueue(), true, false, false);
    }

    @Bean
    public Queue O365DeleteQueue() {
        return new Queue(properties.getO365DeleteQueue(), true, false, false);
    }

    // 교환기 설정
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(properties.getExchange());
    }

    // 바인딩 설정
    @Bean
    public Binding fileBinding(@Qualifier("fileQueue")Queue fileQueue, DirectExchange exchange) {
        return BindingBuilder.bind(fileQueue).to(exchange).with(properties.getFileRoutingKey());
    }

    @Bean
    public Binding vtReportBinding(@Qualifier("vtReportQueue")Queue vtReportQueue, DirectExchange exchange) {
        return BindingBuilder.bind(vtReportQueue).to(exchange).with(properties.getVtReportRoutingKey());
    }

    @Bean
    public Binding vtUploadBinding(@Qualifier("vtUploadQueue")Queue vtUploadQueue, DirectExchange exchange) {
        return BindingBuilder.bind(vtUploadQueue).to(exchange).with(properties.getVtUploadRoutingKey());
    }

    @Bean
    public Binding groupingBinding(@Qualifier("groupingQueue")Queue groupingQueue, DirectExchange exchange) {
        return BindingBuilder.bind(groupingQueue).to(exchange).with(properties.getGroupingRoutingKey());
    }

    @Bean
    public Binding O365InitBinding(@Qualifier("O365InitQueue")Queue O365InitQueue, DirectExchange exchange) {
        return BindingBuilder.bind(O365InitQueue).to(exchange).with(properties.getO365RoutingKey());
    }

    @Bean
    public Binding O365DeleteBinding(@Qualifier("O365DeleteQueue")Queue O365DeleteQueue, DirectExchange exchange) {
        return BindingBuilder.bind(O365DeleteQueue).to(exchange).with(properties.getO365DeleteRoutingKey());
    }

    // RabbitTemplate 설정
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setExchange(properties.getExchange());

        return rabbitTemplate;
    }

    @Bean
    public RabbitTemplate groupingRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setExchange(properties.getExchange());
        rabbitTemplate.setRoutingKey(properties.getGroupingRoutingKey());
        return rabbitTemplate;
    }

    @Bean
    public RabbitTemplate initRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setExchange(properties.getExchange());
        rabbitTemplate.setRoutingKey(properties.getO365RoutingKey());
        return rabbitTemplate;
    }
}