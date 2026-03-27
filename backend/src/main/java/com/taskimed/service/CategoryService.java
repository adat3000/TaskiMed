package com.taskimed.service;

import java.util.List;
import java.util.Map;

import com.taskimed.dto.CategoryDTO;
import com.taskimed.entity.Category;

import org.springframework.data.domain.Page;

public interface CategoryService {
	CategoryDTO saveCategory(CategoryDTO dto);
	List<Category> getCategories();
    CategoryDTO getCategoryById(Long id);
    void deleteCategory(Long id);
    Page<CategoryDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
    CategoryDTO convertToDTO(Category category);
}