package com.project.ChatProject.email;

public interface EmailSender {

    void sendVerificationCode(
            String email,
            String code
    );
}
