package com.piuda.wilson.domain.schedule.entity;

import com.piuda.wilson.domain.conversation.entity.ConversationType;

/** 선제 대화를 거는 일정 종류 (결정 C1). 대화 유형과 같은 이름을 쓴다. */
public enum ScheduleType {
    MEDICATION,
    MEAL,
    WALK,
    DAILY;

    public ConversationType toConversationType() {
        return ConversationType.valueOf(name());
    }
}
