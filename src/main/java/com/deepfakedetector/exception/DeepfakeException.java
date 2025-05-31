package com.deepfakedetector.exception;

import lombok.Getter;

@Getter
public class DeepfakeException extends Exception implements IDeepfakeException {
    public DeepfakeException(final Classifiable error) {
        super(error.getClassName());
    }
}
