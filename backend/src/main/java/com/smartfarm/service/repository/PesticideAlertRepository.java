package com.smartfarm.service.repository;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlert;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PesticideAlertRepository extends JpaRepository<PesticideAlert, Long> {

    /**
     * 유효기간 내(validFrom &lt;= now &lt;= validUntil) 경보만 — 방어선의 핵심(만료된 "이번 주
     * 발생 주의" 경보가 계속 뜨면 안 됨, handoff 명시). Pageable로 상한을 강제한다(#91 정책과 일관).
     */
    @Query("SELECT a FROM PesticideAlert a WHERE a.cropType = :cropType "
            + "AND a.validFrom <= :now AND a.validUntil >= :now "
            + "ORDER BY a.validFrom DESC")
    List<PesticideAlert> findActive(@Param("cropType") CropType cropType, @Param("now") LocalDateTime now,
                                     Pageable pageable);
}
