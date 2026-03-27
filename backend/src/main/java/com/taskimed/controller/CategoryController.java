package com.taskimed.controller;

import com.taskimed.dto.CategoryDTO;
import com.taskimed.entity.Category;
import com.taskimed.service.CategoryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.*;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService){
		this.categoryService = categoryService;
	}

	@PostMapping
	public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryDTO dto) {
	    CategoryDTO saved = categoryService.saveCategory(dto);
	    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@GetMapping("/{id}")
	public ResponseEntity<CategoryDTO> getCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
	    dto.setId(id);
	    CategoryDTO updated = categoryService.saveCategory(dto);
	    return ResponseEntity.ok(updated);
	}

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getCategories());
    }

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
		categoryService.deleteCategory(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/paginated")
	public ResponseEntity<Map<String, Object>> getCategoriesPaginated(
			@RequestParam int pageNumber,
			@RequestParam int pageSize,
			@RequestParam(required = false) String filtro,
			@RequestParam(defaultValue = "id") String sortField,
			@RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam Map<String, String> allParams     // 👈 Nuevo
			) {
        if (allParams.containsKey("globalFilter")) {
            filtro = allParams.get("globalFilter");
        }
        // remover parámetros normales para quedarnos solo con filtros personalizados
        allParams.remove("pageNumber");
        allParams.remove("pageSize");
        allParams.remove("filtro");
        allParams.remove("sortField");
        allParams.remove("sortDir");
        allParams.remove("globalFilter");

		Page<CategoryDTO> page = categoryService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

		Map<String, Object> response = new HashMap<>();
		response.put("data", page.getContent());
		response.put("total", page.getTotalElements());

		return ResponseEntity.ok(response);
	}
}