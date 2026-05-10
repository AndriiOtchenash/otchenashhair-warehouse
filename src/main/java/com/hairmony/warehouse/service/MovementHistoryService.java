package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.repository.StockMovementRepository;
import com.hairmony.warehouse.web.dto.MovementFilterDto;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovementHistoryService {

    public static final int PAGE_SIZE = 100;

    private final StockMovementRepository movementRepository;

    public Page<StockMovement> findFiltered(MovementFilterDto filter) {
        Specification<StockMovement> spec = Specification.where(null);

        if (filter.getDateFrom() != null) {
            spec = spec.and((root, q, cb) ->
                cb.greaterThanOrEqualTo(root.get("createdAt"),
                    filter.getDateFrom().atStartOfDay()));
        }
        if (filter.getDateTo() != null) {
            spec = spec.and((root, q, cb) ->
                cb.lessThan(root.get("createdAt"),
                    filter.getDateTo().plusDays(1).atStartOfDay()));
        }
        if (filter.getMovementType() != null) {
            spec = spec.and((root, q, cb) ->
                cb.equal(root.get("movementType"), filter.getMovementType()));
        }
        if (filter.getProductId() != null) {
            spec = spec.and((root, q, cb) ->
                cb.equal(root.get("product").get("id"), filter.getProductId()));
        }
        if (filter.getProductName() != null && !filter.getProductName().isBlank()) {
            spec = spec.and((root, q, cb) -> {
                String pattern = "%" + filter.getProductName().toLowerCase() + "%";
                return cb.like(cb.lower(root.get("product").get("name")), pattern);
            });
        }
        if (filter.getWriteOffReason() != null) {
            spec = spec.and((root, q, cb) ->
                cb.equal(root.get("writeOffReason"), filter.getWriteOffReason()));
        }
        if (filter.getCounterparty() != null && !filter.getCounterparty().isBlank()) {
            spec = spec.and((root, q, cb) -> {
                var client = root.join("client", JoinType.LEFT);
                var supplier = root.join("supplier", JoinType.LEFT);
                String pattern = "%" + filter.getCounterparty().toLowerCase() + "%";
                return cb.or(
                    cb.like(cb.lower(client.get("name")), pattern),
                    cb.like(cb.lower(supplier.get("name")), pattern)
                );
            });
        }

        spec = spec.and((root, q, cb) -> { q.distinct(true); return null; });

        return movementRepository.findAll(spec,
                PageRequest.of(filter.getPage(), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
