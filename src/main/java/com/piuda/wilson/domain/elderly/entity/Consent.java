package com.piuda.wilson.domain.elderly.entity;

import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의·철회 기록 한 건. 고치지 않고 쌓으며, 종류별 가장 최근 기록이 효력을 가진다 (api-spec 5-12).
 * 언제(createdAt)·누가·어떤 근거로·몇 번째 약관 버전에 동의했는지 남긴다.
 */
@Getter
@Entity
@Table(name = "consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elderly_id", nullable = false, foreignKey = @ForeignKey(name = "fk_consent_elderly"))
    private Elderly elderly;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType type;

    @Column(nullable = false)
    private boolean agreed;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsentGrantor grantor;

    @Column(name = "granted_by_member_id", nullable = false)
    private Long grantedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_basis", nullable = false, length = 20)
    private LegalBasis legalBasis;

    @Builder
    private Consent(Elderly elderly, ConsentType type, boolean agreed, int version, ConsentGrantor grantor,
                    Long grantedByMemberId, LegalBasis legalBasis) {
        this.elderly = elderly;
        this.type = type;
        this.agreed = agreed;
        this.version = version;
        this.grantor = grantor;
        this.grantedByMemberId = grantedByMemberId;
        this.legalBasis = legalBasis;
    }
}
