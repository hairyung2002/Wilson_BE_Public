package com.piuda.wilson.domain.elderly.entity;

import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아직 AI로 보내지 못한 파기 요청 (결정 J3, outbox). 노인 삭제와 같은 트랜잭션에 남기고,
 * 발행이 성공하면 지운다. 삭제는 법적 의무라 브로커 장애로 요청이 사라지면 안 된다.
 *
 * <p>노인과의 연결은 두지 않고 바꾸기 전의 가명 식별자만 둔다. 요청 시각은 createdAt이다.</p>
 */
@Getter
@Entity
@Table(name = "subject_erasure_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubjectErasure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    private SubjectErasure(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public static SubjectErasure of(UUID subjectId) {
        return new SubjectErasure(subjectId);
    }
}
