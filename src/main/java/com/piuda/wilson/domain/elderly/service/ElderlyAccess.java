package com.piuda.wilson.domain.elderly.service;

import com.piuda.wilson.domain.elderly.entity.Elderly;

public record ElderlyAccess(Elderly elderly, AccessActor actor) {

    public boolean isSelf() {
        return actor == AccessActor.SELF;
    }
}
