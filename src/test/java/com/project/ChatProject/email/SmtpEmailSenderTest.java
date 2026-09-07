package com.project.ChatProject.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationCodeBuildsExpectedMessage() {
        SmtpEmailSender emailSender = new SmtpEmailSender(
                mailSender,
                "no-reply@chatproject.local"
        );

        emailSender.sendVerificationCode(
                "user@example.com",
                "123456"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom())
                .isEqualTo("no-reply@chatproject.local");
        assertThat(message.getTo())
                .containsExactly("user@example.com");
        assertThat(message.getSubject())
                .isEqualTo("[ChatProject] 이메일 인증 코드");
        assertThat(message.getText())
                .contains("123456")
                .contains("5분");
    }
}
