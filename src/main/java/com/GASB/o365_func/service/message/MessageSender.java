package com.GASB.o365_func.service.message;


import com.GASB.o365_func.config.RabbitMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitTemplate groupingRabbitTemplate;
    private final RabbitMQProperties properties;

    @Autowired
    public MessageSender(@Qualifier("rabbitTemplate") RabbitTemplate rabbitTemplate,
                         @Qualifier("groupingRabbitTemplate") RabbitTemplate groupingRabbitTemplate,
                         RabbitMQProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.groupingRabbitTemplate = groupingRabbitTemplate;
        this.properties = properties;
    }

    public void sendMessage(Long message) {
        rabbitTemplate.convertAndSend(properties.getFileRoutingKey(),message);
        log.info("Sent message to default queue: " + message);
    }

    public void sendGroupingMessage(Long message) {
        groupingRabbitTemplate.convertAndSend(message);
        log.info("Sent message to grouping queue: " + message);
    }
}
