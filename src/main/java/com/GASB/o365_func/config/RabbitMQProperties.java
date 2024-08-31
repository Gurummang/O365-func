package com.GASB.o365_func.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMQProperties {
    private String exchange;
    private String groupingQueue;
    private String groupingRoutingKey;
    private String fileQueue;
    private String fileRoutingKey;
    private String vtReportQueue;
    private String vtReportRoutingKey;
    private String vtUploadQueue;
    private String vtUploadRoutingKey;
    private String grmScanQueue;
    private String grmScanRoutingKey;
    private String googleInitQueue;
    private String googledriveRoutingKey;
    private String o365InitQueue;
    private String o365RoutingKey;
}
