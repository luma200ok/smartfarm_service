package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 회원 탈퇴 — soft delete + PII 즉시 익명화(contract 탈퇴 봉쇄 ④).
     * <ul>
     *   <li>email → {@code withdrawn-{id}@invalid}: PII 소거. partial unique index
     *       (WHERE deleted_at IS NULL) 밖이라 충돌 없음, 원 이메일 재가입은 그대로 허용.</li>
     *   <li>nickname → 탈퇴회원, password → BCrypt 형식이 아닌 고정값
     *       ({@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder#matches}가
     *       항상 false — 어떤 비밀번호로도 인증 불가).</li>
     * </ul>
     * <p><b>유일한 탈퇴 경로.</b> {@code userRepository.delete()}(@SQLDelete)는 익명화를
     * 건너뛴 soft delete가 되므로 호출 금지 — ArchitectureRulesTest 규칙③이 delete 계열
     * 호출을 정적으로 차단한다. @SQLDelete 어노테이션 자체는 유지한다(제거 시 미래의
     * delete 호출이 hard delete로 떨어져 더 위험).
     */
    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        this.email = "withdrawn-" + this.id + "@invalid";
        this.nickname = "탈퇴회원";
        this.password = "withdrawn";
    }
}
