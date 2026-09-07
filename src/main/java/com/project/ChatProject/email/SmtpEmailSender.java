package com.project.ChatProject.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String sender;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${email-verification.sender}") String sender
    )
    {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Override
    public void sendVerificationCode(
            String email,
            String code
    )
    {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("[ChatProject] 이메일 인증 코드");
        message.setText("""
                이메일 인증 코드는 다음과 같습니다.
                
                %s
                
                인증 코드는 5분 동안 유효합니다.
                """.formatted(code));

        mailSender.send(message);
    }
}
