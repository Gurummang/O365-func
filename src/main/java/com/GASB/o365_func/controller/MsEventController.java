package com.GASB.o365_func.controller;

import com.GASB.o365_func.service.event.MsFileEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/v1/events/o365")
public class MsEventController {

    private final MsFileEvent msFileEvent;

    @Autowired
    public MsEventController(MsFileEvent msFileEvent) {
        this.msFileEvent = msFileEvent;
    }
    @PostMapping("/file-change")
    public ResponseEntity<String> handleFileChangeEvent(@RequestBody Map<String, Object> payload) {
        msFileEvent.handleFileEvent(payload,"file_change");
        return ResponseEntity.ok("File Change Event received and logged");
    }
}
