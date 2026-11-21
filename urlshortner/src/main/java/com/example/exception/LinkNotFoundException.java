package com.example.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String code) {
        super("Short code '" + code + "' was not found. It may have expired and been removed, or it never existed.");
    }
}
