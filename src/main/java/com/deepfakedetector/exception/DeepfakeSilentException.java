package com.deepfakedetector.exception;

import lombok.Getter;

@Getter
public class DeepfakeSilentException extends RuntimeException implements IDeepfakeException {
    private final Classifiable errorMessage;

    public DeepfakeSilentException(final Classifiable errorMessage) {
        super(errorMessage.getClassName());
        this.errorMessage = errorMessage;
    }


}