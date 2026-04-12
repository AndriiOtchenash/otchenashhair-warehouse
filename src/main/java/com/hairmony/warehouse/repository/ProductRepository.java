package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByActiveTrue();

    Optional<Product> findByBarcode(String barcode);

    List<Product> findAllByCategory(Category category);

    List<Product> findAllByActiveTrueAndNameContainingIgnoreCase(String name);
}
