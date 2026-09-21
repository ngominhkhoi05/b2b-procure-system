package com.b2bprocure.system.product.repository;

import com.b2bprocure.system.product.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    List<ProductPrice> findByProductIdOrderByMinQuantityAsc(Long productId);

    Optional<ProductPrice> findByIdAndProductId(Long id, Long productId);

    List<ProductPrice> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product.id IN :productIds ORDER BY pp.minQuantity ASC")
    List<ProductPrice> findByProductIdInOrderByMinQuantityAsc(@Param("productIds") Collection<Long> productIds);

    void deleteByProductId(Long productId);

}
