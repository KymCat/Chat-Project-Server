package com.project.ChatProject.invite;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public enum InviteLinkExpiration {

    ONE_DAY(Duration.ofDays(1)),
    THREE_DAYS(Duration.ofDays(3)),
    SEVEN_DAYS(Duration.ofDays(7));

    private final Duration duration;
}
