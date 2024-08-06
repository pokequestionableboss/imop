package org.iemop.util;

public class TimerUtil {
    public static void log(String message) {
        if (Config.timer)
            System.out.println("[IEMOP Timer] " + message);
    }
}
