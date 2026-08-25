package com.smartfarm.service.repository;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideReference;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PesticideReferenceRepository extends JpaRepository<PesticideReference, Long> {

    /**
     * cropType 필수 + 병해충명 부분 검색(대소문자 무시). {@code likePattern}은 호출자
     * ({@code LocalPesticideReferenceProvider})가 <b>이미 {@code %}/{@code _}/{@code \}를
     * 이스케이프하고 앞뒤에 {@code %}를 덧붙인</b> 완성된 LIKE 패턴이어야 한다(리뷰 P3 — Spring Data
     * 파생 쿼리 {@code ContainingIgnoreCase}는 파라미터 값 자체의 LIKE 와일드카드 문자를 이스케이프
     * 하지 않아, 검색어에 {@code %}·{@code _}가 들어가면 의도보다 넓게 매칭된다). {@code q}가 빈
     * 문자열이면 패턴이 {@code "%%"}가 되어 모든 문자열이 매칭되므로 자연히 "전체 조회"가 된다
     * (handoff의 "q 생략 시 전체" 요구사항 — 별도 분기 불필요). Pageable로 상한을 강제한다(#91 정책과
     * 일관 — LocalPesticideReferenceProvider가 상한값을 넘김).
     */
    @Query("SELECT r FROM PesticideReference r WHERE r.cropType = :cropType "
            + "AND LOWER(r.pestName) LIKE LOWER(:likePattern) ESCAPE '\\' "
            + "ORDER BY r.pestName ASC")
    List<PesticideReference> searchByCropTypeAndPestNameLike(
            @Param("cropType") CropType cropType, @Param("likePattern") String likePattern, Pageable pageable);
}
