package com.deepfakedetector.util;

import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class DetectionMessageResolver {


    private static final String BUNDLE_NAME = "i18n/messages";

    public static String getArabicMessage(String key) {
        return getMessage(key, new Locale("ar"));
    }

    public static String getEnglishMessage(String key) {
        return getMessage(key, Locale.ENGLISH);
    }


    private static String getMessage(String key, Locale locale) {
        try {
            ResourceBundle messages = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            return messages.getString(key);
        } catch (MissingResourceException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }
}

