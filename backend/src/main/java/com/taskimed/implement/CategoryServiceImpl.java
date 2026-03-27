package com.taskimed.implement;

import com.taskimed.dto.CategoryDTO;
import com.taskimed.entity.Category;
import com.taskimed.repository.CategoryRepository;
import com.taskimed.service.CategoryService;

import jakarta.persistence.PersistenceException;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class CategoryServiceImpl implements CategoryService {

	private final CategoryRepository categoryRepository;
	
	public CategoryServiceImpl(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Override
    @Transactional(readOnly = true)
	public List<Category> getCategories() {
		return categoryRepository.findAll();
	}
	
	@Override
    @Transactional
	public CategoryDTO saveCategory(CategoryDTO dto) {
        if (dto == null) throw new RuntimeException("CategoryDTO no puede ser null");

        Category category;
        if (dto.getId() != null) {
            category = categoryRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Category not found with ID: " + dto.getId()));
        } else {
            category = new Category();
        }

        // Mapear campos simples...
        category.setName(dto.getName());
        category.setAlias(dto.getAlias());

        Category saved = categoryRepository.saveAndFlush(category);
        return convertToDTO(saved);
	}

	@Override
	public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return convertToDTO(category);
	}

	@Override
	public void deleteCategory(Long id) {
		categoryRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<CategoryDTO> getPage(
			int pageNumber,
			int pageSize,
			String filtro,
			String sortField,
			String sortDir,
            Map<String, String> customFilters
		) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

            Specification<Category> spec = (root, query, builder) -> {
                if (filtro == null || filtro.trim().isEmpty()) {
                    return builder.conjunction();
                }

                String pattern = "%" + filtro.toLowerCase() + "%";

                return builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("alias")), pattern)
                );
            };
            Page<Category> page = categoryRepository.findAll(spec, pageable);

            List<CategoryDTO> dtoList = page.getContent()
                    .stream()
                    .map(this::convertToDTO)
                    .toList();

            return new PageImpl<>(dtoList, pageable, page.getTotalElements());
        } catch (DataAccessException | PersistenceException e) {
            throw new RuntimeException("Error getting paginated categories: " + e.getMessage(), e);
        }
	}

	@Override
	public CategoryDTO convertToDTO(Category category) {
        if (category == null) return null;
        return CategoryDTO.builder()
        		.id(category.getId())
        		.name(category.getName())
        		.alias(category.getAlias())
        		.build();
	}
}