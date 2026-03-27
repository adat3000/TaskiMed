package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

import org.primefaces.PrimeFaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.CategoryDTO;

/**
 * Bean responsible for managing categories (Category entity) in the JSF interface.
 * Includes CRUD methods connected to the backend through the REST service.
 */
@Data
@Named
@SessionScoped
@EqualsAndHashCode(callSuper = false)
public class CategoryBean extends EntityLazyBean<CategoryDTO> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();

    private CategoryDTO selectedCategory = new CategoryDTO();
    private List<CategoryDTO> categories;
    private String action;

    @Override
    public String getEndpoint() {
        return "/api/categories/paginated";
    }

    @Override
    public Class<CategoryDTO> getEntityClass() {
        return CategoryDTO.class;
    }

    @PostConstruct
    public void initBean() {
        super.init();
        try {
            String token = util.obtenerToken();
            categories = util.getDataFromService("/api/categories", new TypeReference<List<CategoryDTO>>() {}, token);
        } catch (Exception e) {
            e.printStackTrace();
            categories = List.of();
        }
    }

    public List<CategoryDTO> getCategories() {
        return categories;
    }

    public void ejecutar() {
        if ("Create".equals(action))
            create();
        else if ("Update".equals(action))
            update();
    }

    public void ejecutar(String accion) throws Exception {
        if ("eliminar".equals(accion)) {
            util.deleteDataFromService("/api/categories/", selectedCategory.getId(), token);
        } else if ("crear".equals(accion) || "actualizar".equals(accion)) {

        	CategoryDTO category = util.postDataToService(
                "/api/categories",
                selectedCategory,
                new TypeReference<CategoryDTO>() {},
                token
            );
            selectedCategory.setId(category.getId());
        }
    }

    public void clear() {
        selectedCategory = new CategoryDTO();
        action = null;
    }

    private void create() {
        try {
            ejecutar("crear");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Category created successfully. Status: " + util.getStatus()));
            PrimeFaces.current().ajax().update("form:datatable");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The category could not be created."));
            e.printStackTrace();
        }
    }

    private void update() {
        try {
            ejecutar("actualizar");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Category updated successfully. Status: " + util.getStatus()));
            PrimeFaces.current().ajax().update("form:datatable");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The category could not be updated."));
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            ejecutar("eliminar");
            if (util.getStatus() == 403) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN,
                        "Warning", "The authenticated category cannot be deleted."));
                return;
            }
            if (util.getStatus() < 400) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Success", "Category deleted successfully. Status: " + util.getStatus()));
                adjustPaginatorAfterDeletion("tableEntity", lazyModel);
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "There was a category deleting the category."));
            e.printStackTrace();
        }
    }
}