package com.GASB.o365_func.service.message;

import com.GASB.o365_func.service.MsInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageReceiver {
    private final MsInitService msInitService;

    @Autowired
    public MessageReceiver(MsInitService msInitService) {
        this.msInitService = msInitService;
    }
    @RabbitListener(queues = "${rabbitmq.O365_INIT_QUEUE}") //수정 필요
    public void receiveMessage(int message) {
        try {
            log.info("Received message from queue: " + message);
            msInitService.fetchAndSaveAll(message);
        } catch (Exception e) {
            log.error("An error occurred while processing the message: {}", e.getMessage(), e);
        }
    }
}
