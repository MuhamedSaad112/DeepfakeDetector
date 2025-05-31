package com.deepfakedetector.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class RandomUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_KEY_LENGTH = 20;
    private static final int DEFAULT_PASSWORD_LENGTH = 12;

    // Character sets for password generation
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHARS;


    public static String generateRandomKey(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Key length must be at least 1");
        }

        // Calculate required byte length for Base64 encoding
        int byteLength = (int) Math.ceil(length * 3.0 / 4.0);
        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);

        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return encoded.length() > length ? encoded.substring(0, length) : encoded;
    }

    public static String generateResetKey() {
        return generateRandomKey(DEFAULT_KEY_LENGTH);
    }

    public static String generateActivationKey() {
        return generateRandomKey(DEFAULT_KEY_LENGTH);
    }

    public static String generatePassword() {
        return generatePassword(DEFAULT_PASSWORD_LENGTH);
    }

    public static String generatePassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password length must be at least 8 characters");
        }

        StringBuilder password = new StringBuilder(length);

        // Ensure at least one character from each category
        password.append(UPPERCASE.charAt(SECURE_RANDOM.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(SECURE_RANDOM.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL_CHARS.charAt(SECURE_RANDOM.nextInt(SPECIAL_CHARS.length())));

        // Fill the rest with random characters from all categories
        for (int i = 4; i < length; i++) {
            password.append(ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length())));
        }

        // Shuffle the password to avoid predictable patterns
        return shuffleString(password.toString());
    }

    public static String generateNumericOTP(int length) {
        if (length < 4) {
            throw new IllegalArgumentException("OTP length must be at least 4 digits");
        }

        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }

        return otp.toString();
    }

    public static String generateSixDigitOTP() {
        return generateNumericOTP(6);
    }

    private static String shuffleString(String input) {
        char[] chars = input.toCharArray();

        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            // Swap chars[i] and chars[j]
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    public static String generateAlphanumeric(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be at least 1");
        }

        String alphanumeric = UPPERCASE + LOWERCASE + DIGITS;
        StringBuilder result = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            result.append(alphanumeric.charAt(SECURE_RANDOM.nextInt(alphanumeric.length())));
        }

        return result.toString();
    }
}