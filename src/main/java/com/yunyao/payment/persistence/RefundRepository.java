package com.yunyao.payment.persistence;

import com.yunyao.payment.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {
    Optional<RefundEntity> findByIdempotencyKey(String key);

    @Query("select coalesce(sum(r.amount), 0) from RefundEntity r where r.checkoutId = :checkoutId and r.status = :status")
    BigDecimal sumAmountByCheckoutIdAndStatus(@Param("checkoutId") UUID checkoutId,
                                              @Param("status") RefundStatus status);
}
