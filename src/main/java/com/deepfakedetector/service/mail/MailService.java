package com.deepfakedetector.service.mail;

import com.deepfakedetector.model.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;


@Service
@Log4j2
@RequiredArgsConstructor
public class MailService {

    // Constants for template variable keys
    private static final String USER = "user";
//    private static final String BASE_URL = "baseUrl";

    @Value("${mail.from}") // Email address for sending emails
    private String mailFrom;

    @Value("${mail.baseUrl}") // Base URL used in email templates
    private String baseUrl;

    // Dependencies injected via constructor
    private final JavaMailSender javaMailSender; // Mail sender utility
    private final MessageSource messageSource; // Used to retrieve localized messages
    private final SpringTemplateEngine templateEngine; // For processing email templates


    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {

        log.debug("Send Email[Multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart,
                isHtml, to, subject, content);

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            // Configure email message
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, isMultipart,
                    StandardCharsets.UTF_8.name());
            messageHelper.setTo(to);
            messageHelper.setSubject(subject);
            messageHelper.setText(content, isHtml);
            messageHelper.setFrom(mailFrom);

            // Send the email
            javaMailSender.send(mimeMessage);
            log.debug("Sent Email to User '{}'", to);

        } catch (Exception e) {
            log.warn("Email does not exist for user '{}'", to, e);
        }
    }


    @Async
    public void sendEmailFromTemplate(User user, String templateName, String titleKey, String link) {

        if (user.getEmail() == null) {
            log.debug("Email does not exist for user '{}'", user.getUserName());
            return;
        }

        // Set the email context with user details and link
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
//        context.setVariable(BASE_URL, baseUrl);
        context.setVariable("link", link);

        // Process the email template and retrieve the content
        String content = templateEngine.process(templateName, context);

        // Retrieve the subject from the message source
        String subject = messageSource.getMessage(titleKey, null, locale);

        // Send the email
        sendEmail(user.getEmail(), subject, content, false, true);
    }


    @Async
    public void sendActivationEmail(User user) {
        log.debug("Send activation email to '{}'", user.getEmail());

        // Generate the activation link
        String activationLink = baseUrl + "/api/v1/auth/activate?key=" + user.getActivationKey();
        log.debug("Generated activation link: '{}'", activationLink);

        // Send the activation email using the specified template
        sendEmailFromTemplate(user, "mail/activationEmail", "email.activation.title", activationLink);
    }

    @Async
    public void sendCreationEmail(User user) {
        log.debug("Sending account creation email to '{}'", user.getEmail());

        // Generate the login link
        String creationLink = baseUrl + "/login";
        log.debug("Generated creation link: '{}'", creationLink);

        // Send the creation email using the specified template
        sendEmailFromTemplate(user, "mail/creationEmail", "email.creation.title", creationLink);
    }


    @Async
    public void sendPasswordResetEmail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());

        // Generate the password reset link
        String resetKey = user.getResetKey();
        log.debug("Generated reset key: '{}'", resetKey);

        // Send the password reset email using the specified template
        sendEmailFromTemplate(user, "mail/passwordResetEmail", "email.reset.title", resetKey);
    }
}