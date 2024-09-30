package com.GASB.o365_func.service.message;

import com.GASB.o365_func.config.RabbitMQProperties;
import com.GASB.o365_func.service.MsFileService;
import com.GASB.o365_func.service.MsInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MessageReceiver {
    private final MsInitService msInitService;
    private final MsFileService msFileService;
    @Autowired
    public MessageReceiver(MsInitService msInitService, MsFileService msFileService) {
        this.msInitService = msInitService;
        this.msFileService = msFileService;
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


    @RabbitListener(queues = "{rabbitmq.O365_DELETE_QUEUE}")
    public void receiveDeleteMessage(List<Map<String,String>> message){
        try {
            log.info("Received message from queue: " + message);
            msFileService.fileDelete(message);
        } catch (DataAccessException e) {
            log.error("Error deleting data: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while processing the message: {}", e.getMessage(), e);
        }
    }



}
