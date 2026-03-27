package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.RiskInput;
import com.taskimed.dto.RiskResult;

import java.io.Serializable;

import org.primefaces.PrimeFaces;

@Data
@Named
@SessionScoped
@EqualsAndHashCode(callSuper = false)
public class RiskBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();

    private RiskInput input = new RiskInput();
    private RiskResult result;

    @PostConstruct
    public void init() {
        result = new RiskResult(); // inicializar vacío
    }

    public void evaluateRisk() {
        try {
            // Obtener token si tu backend lo requiere
            String token = util.obtenerToken();

            // Hacer POST al backend
            result = util.postDataToService(
                    "/api/risk",
                    input,
                    new TypeReference<RiskResult>() {}, // ✅ aquí usamos TypeReference
                    token
            );

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Éxito",
                            "Riesgo calculado correctamente: " + result.getRisk()));

            // Actualizar componentes PrimeFaces si quieres mostrar resultados dinámicamente
            PrimeFaces.current().ajax().update("form:resultPanel");

        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Error",
                            "No se pudo calcular el riesgo."));
            e.printStackTrace();
        }
    }
}
