package com.xjtucsse.meetingassistant;

public class CloudException extends Exception {
    public CloudException(String message) {
        super(message);
    }

    public CloudException(String message, Throwable cause) {
        super(message, cause);
    }
}
