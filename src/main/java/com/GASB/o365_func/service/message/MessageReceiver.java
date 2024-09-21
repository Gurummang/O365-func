package com.GASB.o365_func.service.message;

import com.GASB.o365_func.service.MsInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageReceiver {
    private final MsInitService msInitService;

    @Autowired
    public MessageReceiver(MsInitService msInitService) {
        this.msInitService = msInitService;
    }
    @RabbitListener(queues = "${rabbitmq.O365_INIT_QUEUE}")
    public void receiveMessage(int message) {
        try {
            log.info("Received message from queue: " + message);
            msInitService.fetchAndSaveAll(message); // 이 메소드가 발생할 수 있는 구체적인 예외들을 처리
        } catch (IllegalArgumentException e) {
            // 메시지가 올바르지 않은 형식일 때
            log.error("Invalid message format: {}", e.getMessage(), e);
        } catch (NullPointerException e) {
            // Null 참조 예외 처리
            log.error("Null value encountered: {}", e.getMessage(), e);
        } catch (Exception e) {
            // 그 외 포괄적인 예외는 마지막에 처리
            log.error("An unexpected error occurred while processing the message: {}", e.getMessage(), e);
        }
    }

}
