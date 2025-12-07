package com.dataquality.common;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class CoreLogStream {

    private static final List<String> logs = new CopyOnWriteArrayList<>();

    public static void push(String msg) {
        logs.add(msg);
    }

    public static List<String> drain() {
        List<String> copy = List.copyOf(logs);
        logs.clear();
        return copy;
    }
}