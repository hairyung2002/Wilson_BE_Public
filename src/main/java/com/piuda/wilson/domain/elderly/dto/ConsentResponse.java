package com.piuda.wilson.domain.elderly.dto;

import com.piuda.wilson.domain.elderly.entity.Consent;
import com.piuda.wilson.domain.elderly.entity.ConsentGrantor;
import com.piuda.wilson.domain.elderly.entity.ConsentType;
import com.piuda.wilson.domain.elderly.entity.LegalBasis;
import java.time.LocalDateTime;

public record ConsentResponse(ConsentType type, boolean agreed, int version, ConsentGrantor grantedBy,
                              LegalBasis legalBasis, LocalDateTime updatedAt) {

    public static ConsentResponse from(Consent consent) {
        return new ConsentResponse(consent.getType(), consent.isAgreed(), consent.getVersion(), consent.getGrantor(),
                consent.getLegalBasis(), consent.getCreatedAt());
    }
}
