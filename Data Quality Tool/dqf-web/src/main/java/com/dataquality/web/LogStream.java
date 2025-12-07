package com.dataquality.web;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import com.dataquality.common.CoreLogStream;

public class LogStream {

    private static final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public static SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    // This is called from LogController every 200ms
    public static void flush() {
        List<String> msgs = CoreLogStream.drain();
        if (msgs.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                for (String msg : msgs) {
                    emitter.send(SseEmitter.event().data(msg));
                }
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}