package com.genus.usb_comm.model;

public class LogItem {
    public enum Source {
        USB, BLE, OTHER
    }

    private final String message;
    private final Source source;

    public LogItem(String message, Source source) {
        this.message = message;
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public Source getSource() {
        return source;
    }
}
