package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlarmEventRepositoryImpl implements AlarmEventRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<AlarmEvent> search(Long farmId, AlarmEventStatus status, AlarmSeverity severity,
                                    Pageable pageable) {
        List<AlarmEvent> content = queryContent(farmId, status, severity, pageable);
        long total = queryCount(farmId, status, severity);
        return new PageImpl<>(content, pageable, total);
    }

    private List<AlarmEvent> queryContent(Long farmId, AlarmEventStatus status, AlarmSeverity severity,
                                           Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AlarmEvent> query = cb.createQuery(AlarmEvent.class);
        Root<AlarmEvent> root = query.from(AlarmEvent.class);

        query.where(buildPredicates(cb, root, farmId, status, severity));
        // occurredAt 내림차순, 동일 시각은 id 내림차순으로 안정 정렬(FarmLog 선례와 동일 원칙).
        query.orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));

        return entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    private long queryCount(Long farmId, AlarmEventStatus status, AlarmSeverity severity) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AlarmEvent> root = countQuery.from(AlarmEvent.class);

        countQuery.select(cb.count(root));
        countQuery.where(buildPredicates(cb, root, farmId, status, severity));

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb, Root<AlarmEvent> root, Long farmId,
                                         AlarmEventStatus status, AlarmSeverity severity) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("farmId"), farmId));
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (severity != null) {
            predicates.add(cb.equal(root.get("severity"), severity));
        }
        return predicates.toArray(new Predicate[0]);
    }
}
