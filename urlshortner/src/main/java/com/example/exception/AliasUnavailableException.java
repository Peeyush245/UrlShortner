package com.example.exception;

public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String alias) {
        super("Alias is already in use or reserved: " + alias);
    }
}
