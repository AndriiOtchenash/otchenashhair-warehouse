package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT DISTINCT p.brand FROM Product p WHERE p.brand IS NOT NULL AND p.brand <> '' ORDER BY p.brand")
    List<String> findDistinctBrands();

    List<Product> findAllByActiveTrueOrderByNameAsc();

    Optional<Product> findByBarcode(String barcode);

    List<Product> findAllByCategory(Category category);
    boolean existsByCategory_Id(Long categoryId);

    @Query("SELECT DISTINCT p.category.id FROM Product p WHERE p.category IS NOT NULL")
    Set<Long> findAllCategoryIdsInUse();

    List<Product> findAllByActiveTrueAndNameContainingIgnoreCase(String name);
}
