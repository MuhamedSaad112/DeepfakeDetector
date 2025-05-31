package com.deepfakedetector.configuration;

import lombok.NoArgsConstructor;


@NoArgsConstructor
public class Validation {


    public static final String USER_NAME_PATTERN = "^[a-zA-Z0-9]{5,50}$";


    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{10,100}$";


    public static final String SYSTEM = "system";


    public static final String DEFAULT_LANGUAGE = "en";
}
