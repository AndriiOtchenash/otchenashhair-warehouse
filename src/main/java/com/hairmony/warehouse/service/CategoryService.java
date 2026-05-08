package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.repository.CategoryRepository;
import com.hairmony.warehouse.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Set<Long> getCategoryIdsWithProducts() {
        return productRepository.findAllCategoryIdsInUse();
    }

    public Category save(String name) {
        Category category = Category.builder()
                .name(name)
                .sortOrder(categoryRepository.findAllByOrderBySortOrderAsc().size() + 1)
                .build();
        return categoryRepository.save(category);
    }

    public Category rename(Long id, String newName) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        category.setName(newName);
        return category;
    }

    public String delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        if (productRepository.existsByCategory_Id(id)) {
            throw new IllegalStateException(messageSource.getMessage(
                    "category.delete.error.hasProducts", null, LocaleContextHolder.getLocale()));
        }
        String name = category.getName();
        categoryRepository.deleteById(id);
        return name;
    }
}
