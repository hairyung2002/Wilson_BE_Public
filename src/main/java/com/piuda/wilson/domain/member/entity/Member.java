package com.piuda.wilson.domain.member.entity;

import com.piuda.wilson.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "member",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    private static final String WITHDRAWN_NAME = "탈퇴한 회원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기기 연결 코드로 생성된 노인 회원은 이메일·비밀번호가 없다. */
    private String email;

    private String password;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 20)
    private MemberType memberType;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private Member(String email, String password, String name, String phone, MemberType memberType) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.memberType = memberType;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** null인 항목은 변경하지 않는다. 이메일은 변경할 수 없다. */
    public void updateProfile(String name, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    public boolean isCaregiver() {
        return memberType == MemberType.CAREGIVER;
    }

    public boolean hasPassword() {
        return password != null;
    }

    /** 탈퇴 즉시 개인정보를 파기한다. 같은 이메일로 다시 가입할 수 있다. */
    public void withdraw() {
        this.name = WITHDRAWN_NAME;
        this.email = null;
        this.password = null;
        this.phone = null;
        this.deleted = true;
    }
}
