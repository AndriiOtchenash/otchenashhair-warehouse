package com.hairmony.warehouse.config;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CategoryRepository categoryRepository;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, Category>() {
            @Override
            public Category convert(String id) {
                if (id == null || id.isBlank()) return null;
                return categoryRepository.findById(Long.parseLong(id)).orElse(null);
            }
        });
    }
}
