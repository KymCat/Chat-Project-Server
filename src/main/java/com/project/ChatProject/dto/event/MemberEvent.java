package com.project.ChatProject.dto.event;

public record MemberEvent(
        MemberEventType type,
        Long memberId
) {
    public static MemberEvent hidden(Long memberId) {
        return new MemberEvent(
                MemberEventType.BLOCKED,
                memberId
        );
    }

    public static MemberEvent unhidden(Long memberId) {
        return new MemberEvent(
                MemberEventType.UNBLOCKED,
                memberId
        );
    }
}
