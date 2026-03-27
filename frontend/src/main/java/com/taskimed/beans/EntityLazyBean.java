package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;

import org.primefaces.PrimeFaces;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;

import com.taskimed.config.Util;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

@Getter
@Setter
public abstract class EntityLazyBean<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    protected LazyDataModel<T> lazyModel;
    protected String token;
    protected Util util = new Util();
    private int storedRowCount = 0;
    private List<T> currentData = new ArrayList<>();

    // ★★★★★ NUEVO: filtros personalizados aplicados por cada Bean ★★★★★
    private final Map<String, Object> customFilters = new HashMap<>();

    public void addCustomFilter(String key, Object value) {
        if (key == null) return;
        customFilters.put(key, value);
    }

    public void removeCustomFilter(String key) {
        if (key == null) return;
        customFilters.remove(key);
    }

    public void clearCustomFilters() {
        customFilters.clear();
    }

    public Map<String, Object> getCustomFilters() {
        return customFilters;
    }
    // ★★★★★ FIN CAMBIOS ★★★★★

    public abstract String getEndpoint(); 
    public abstract Class<T> getEntityClass(); 

    @PostConstruct
    public void init() {
        FacesContext context = FacesContext.getCurrentInstance();

        if (util == null) {
            context.addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Util has not been initialized"));
            return;
        }

        token = (String) context.getExternalContext().getSessionMap().get("token");
        if (token == null) {
            context.addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Invalid or expired session"));
            return;
        }

        lazyModel = new LazyDataModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public List<T> load(int first, int pageSize, Map<String, SortMeta> sortBy,
                                Map<String, FilterMeta> filterBy) {

                try {
                    int pageNumber = first / pageSize;

                    String globalFilter = Optional.ofNullable(filterBy)
                            .map(f -> f.get("globalFilter"))
                            .map(FilterMeta::getFilterValue)
                            .map(Object::toString)
                            .orElse("");

                    String sortField = null;
                    String sortOrder = "asc";

                    if (sortBy != null && !sortBy.isEmpty()) {
                        SortMeta sortMeta = sortBy.values().iterator().next();
                        sortField = sortMeta.getField();
                        if (sortMeta.getOrder() != null) {
                            sortOrder = sortMeta.getOrder().equals(SortOrder.ASCENDING) ? "asc" : "desc";
                        }
                    }

                    // ★★★★★ NUEVO: mezclar filtros globales con los personalizados ★★★★★
                    Map<String, Object> mergedFilters = new HashMap<>();
                    mergedFilters.put("globalFilter", globalFilter); 
                    mergedFilters.putAll(customFilters);
                    // ★★★★★ FIN CAMBIO ★★★★★

                    Map<String, Object> response = util.getDataFromServiceWithPagination(
                            getEndpoint(),
                            pageNumber,
                            pageSize,
                            sortField,
                            sortOrder,
                            mergedFilters,   // ← AHORA SE ENVÍAN AL BACKEND
                            token,
                            getEntityClass()
                    );

                    Object data = response.get("data");
                    List<T> entities = convertList(data);

                    Object totalObj = response.get("total");
                    int total = (totalObj instanceof Number) ? ((Number) totalObj).intValue() : 0;

                    this.setRowCount(total);
                    storedRowCount = total;

                    if (entities.isEmpty() && pageNumber > 0) {
                        pageNumber--;
                        Map<String, Object> previousResponse = util.getDataFromServiceWithPagination(
                                getEndpoint(),
                                pageNumber,
                                pageSize,
                                sortField,
                                sortOrder,
                                mergedFilters,
                                token,
                                getEntityClass()
                        );

                        entities = convertList(previousResponse.get("data"));
                        totalObj = previousResponse.get("total");
                        total = (totalObj instanceof Number) ? ((Number) totalObj).intValue() : 0;

                        this.setRowCount(total);
                        storedRowCount = total;
                    }

                    currentData = entities;
                    return entities;

                } catch (Exception e) {
                    context.addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Error loading data"));
                    return Collections.emptyList();
                }
            }

            @Override
            public int count(Map<String, FilterMeta> filterBy) {
                return storedRowCount;
            }

            @Override
            public String getRowKey(T entity) {
                try {
                    Method getId = getEntityClass().getMethod("getId");
                    Object id = getId.invoke(entity);
                    return id != null ? id.toString() : null;
                } catch (Exception e) {
                    throw new UnsupportedOperationException("Could not get object ID", e);
                }
            }

            @Override
            public T getRowData(String rowKey) {
                if (rowKey == null) return null;
                for (T entity : currentData) {
                    try {
                        Method getId = getEntityClass().getMethod("getId");
                        Object id = getId.invoke(entity);
                        if (id != null && rowKey.equals(id.toString())) {
                            return entity;
                        }
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
        };
    }

    protected void adjustPaginatorAfterDeletion(String idTableDatatable, LazyDataModel<T> lazyModel) {
        int pageSize = lazyModel.getPageSize();
        int totalBefore = lazyModel.getRowCount();
        int totalAfter = totalBefore - 1;
        int totalPages = (int) Math.ceil((double) totalAfter / pageSize);

        String js = """
            setTimeout(function() {
                var table = PF('%s');
                var currentPage = table.getPaginator().cfg.page;
                var totalPages = %d;
                var newPage = (currentPage >= totalPages && currentPage > 0) ? totalPages - 1 : currentPage;
                table.getPaginator().setPage(newPage);
            }, 100);
        """.formatted(idTableDatatable, totalPages);

        PrimeFaces.current().ajax().update("form:datatable");
        PrimeFaces.current().executeScript(js);
    }

    private List<T> convertList(Object data) {
        if (data instanceof List<?>) {
            List<?> originalList = (List<?>) data;
            List<T> convertedList = new ArrayList<>();
            for (Object item : originalList) {
                if (getEntityClass().isInstance(item)) {
                    convertedList.add(getEntityClass().cast(item));
                }
            }
            return convertedList;
        }
        return Collections.emptyList();
    }
}
