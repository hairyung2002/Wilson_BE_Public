package com.piuda.wilson.domain.notification.entity;

import com.piuda.wilson.domain.member.entity.Member;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원의 FCM 푸시 토큰. 회원당 마지막 기기 1개만 보관한다 (api-spec 4-5). */
@Getter
@Entity
@Table(name = "push_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_push_token_member"))
    private Member member;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @Builder
    private PushToken(Member member, String token, PushPlatform platform) {
        this.member = member;
        this.token = token;
        this.platform = platform;
    }

    public void change(String token, PushPlatform platform) {
        this.token = token;
        this.platform = platform;
    }
}
