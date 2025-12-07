package com.dataquality.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController
public class LogController {

    @GetMapping("/logs/stream")
    public SseEmitter streamLogs() {
        return LogStream.subscribe();
    }

    @Scheduled(fixedRate = 200)
    public void flushLogs() {
        LogStream.flush();
    }
}