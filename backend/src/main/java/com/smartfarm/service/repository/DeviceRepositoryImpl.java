package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepositoryCustom {

    private final EntityManager entityManager;

    /**
     * @SQLRestriction("deleted_at IS NULL")은 Criteria API로 만든 쿼리에도 Hibernate가 자동 적용하므로
     * 여기서 별도로 deletedAt 조건을 추가하지 않는다(다른 도메인의 파생 쿼리·JPQL과 동일한 보장).
     */
    @Override
    public List<Device> search(Long farmId, DeviceKind kind, DeviceStatus status, String q, Long zoneId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Device> query = cb.createQuery(Device.class);
        Root<Device> root = query.from(Device.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("farmId"), farmId));
        if (kind != null) {
            predicates.add(cb.equal(root.get("kind"), kind));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (zoneId != null) {
            predicates.add(cb.equal(root.get("zoneId"), zoneId));
        }
        if (q != null && !q.isBlank()) {
            String escaped = q.trim().toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\'));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("id")));
        return entityManager.createQuery(query).getResultList();
    }
}
