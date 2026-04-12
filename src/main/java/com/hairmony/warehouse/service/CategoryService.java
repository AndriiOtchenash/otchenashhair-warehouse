package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
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
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Category not found: " + id));
        category.setName(newName);
        return category;
    }

    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
