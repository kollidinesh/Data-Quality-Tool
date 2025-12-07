package com.dataquality.common;
 
public class CoreLogger {
 
    // This will be assigned by web module at runtime
    public static LogListener listener = null;
 
    public interface LogListener {
        void log(String msg);
    }
 
    public static void push(String msg) {
        System.out.println(msg);   // always print to console
 
        if (listener != null) {
            try {
                listener.log(msg);   // push to UI if available
            } catch (Exception ignored) {}
        }
    }
}
 
