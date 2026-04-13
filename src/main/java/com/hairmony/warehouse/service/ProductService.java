package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.repository.ProductRepository;
import com.hairmony.warehouse.repository.StockItemRepository;
import com.hairmony.warehouse.web.dto.ProductDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> findAll() {
        return productRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> findAllActive() {
        return productRepository.findAllByActiveTrue().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> findAllActiveWithStock() {
        return productRepository.findAllByActiveTrue().stream()
                .map(this::toDto)
                .filter(p -> p.getCurrentQuantity() != null
                        && p.getCurrentQuantity().compareTo(java.math.BigDecimal.ZERO) > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductDto> findById(Long id) {
        return productRepository.findById(id).map(this::toDto);
    }

    public ProductDto save(ProductDto dto) {
        Product product = toEntity(dto);
        product.setActive(true);
        return toDto(productRepository.save(product));
    }

    public ProductDto update(Long id, ProductDto dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        product.setName(dto.getName());
        product.setBrand(dto.getBrand());
        product.setCategory(dto.getCategory());
        product.setBarcode(dto.getBarcode());
        product.setUnit(dto.getUnit());
        product.setUnitSize(dto.getUnitSize());
        product.setMinStockLevel(dto.getMinStockLevel());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        return toDto(product); // no save() needed — dirty checking handles it
    }

    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        product.setActive(false);
        // no save() needed — dirty checking handles it
    }

    public void restore(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        product.setActive(true);
    }

    @Transactional(readOnly = true)
    public List<String> findAllBrands() {
        return productRepository.findDistinctBrands();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> search(String name) {
        return productRepository.findAllByActiveTrueAndNameContainingIgnoreCase(name).stream()
                .map(this::toDto)
                .toList();
    }

    private ProductDto toDto(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .barcode(product.getBarcode())
                .unit(product.getUnit())
                .unitSize(product.getUnitSize())
                .minStockLevel(product.getMinStockLevel())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .currentQuantity(stockItemRepository.getTotalQuantityByProductId(product.getId()))
                .build();
    }

    private Product toEntity(ProductDto dto) {
        return Product.builder()
                .id(dto.getId())
                .name(dto.getName())
                .brand(dto.getBrand())
                .category(dto.getCategory())
                .barcode(dto.getBarcode())
                .unit(dto.getUnit())
                .unitSize(dto.getUnitSize())
                .minStockLevel(dto.getMinStockLevel())
                .description(dto.getDescription())
                .imageUrl(dto.getImageUrl())
                .active(dto.isActive())
                .build();
    }
}
