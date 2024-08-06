package org.iemop.util.instrumentation;

import java.util.List;

public class InstrumentationToolResult {
    public boolean result;
    public List<String> log;

    public InstrumentationToolResult(boolean result, List<String> log) {
        this.result = result;
        this.log = log;
    }

    @Override
    public String toString() {
        return "Tool Result: " + this.result + ", error log: " + this.log;
    }
}
