package com.taskimed.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Properties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.faces.context.FacesContext;

public class Util {

    private String BASE_URL;
    private int status = 0;
    private final ObjectMapper mapper = new ObjectMapper();

    // Constructor that loads the URL from application.properties
    public Util() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            Properties properties = new Properties();
            if (input != null) {
                properties.load(input);
                BASE_URL = properties.getProperty("backend.url");
            } else {
                throw new RuntimeException("Error: The file application.properties not found");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading configuration", e);
        }
    }
    
    public void setStatus(int status) {
    	this.status = status;
    }
    
    public int getStatus() {
    	return status;
    }

    /**
     * NUEVO MÉTODO: Ejecuta una acción vía POST que no espera contenido en la respuesta.
     * Ideal para evitar errores de Jackson (end-of-input) con ResponseEntity.ok().build()
     */
    public boolean executeAction(String endpoint, Object requestData, String token) {
        try {
            String url = BASE_URL + endpoint;
            HttpClient client = HttpClient.newHttpClient();
            String json = requestData != null ? mapper.writeValueAsString(requestData) : "";

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");

            if (tokenValido(token)) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            setStatus(response.statusCode());

            return response.statusCode() == 200 || response.statusCode() == 201;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public <T> List<T> getDataFromServicePaginated(String endpoint, int first, int pageSize, String filtro, TypeReference<List<T>> typeRef) {
        try {
            String filtroCodificado = filtro != null ? URLEncoder.encode(filtro, StandardCharsets.UTF_8) : "";
            String url = BASE_URL + endpoint + "?first=" + first + "&pageSize=" + pageSize + "&filtro=" + filtroCodificado;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), typeRef);
            } else if (response.statusCode() == 401) {
                System.err.println("ERROR JWT - Code1: Credenciales inválidas.");
                return null;
            } else {
                System.err.println("Error getting paginated data. Code: " + response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public int getTotalCountFromService(String endpoint, String filtro) {
        try {
            String hardcodedFilter = filtro != null ? URLEncoder.encode(filtro, StandardCharsets.UTF_8) : "";
            String url = BASE_URL + endpoint + "?filtro=" + hardcodedFilter;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() == 200) {
                return Integer.parseInt(response.body());
            } else if (response.statusCode() == 401) {
                System.err.println("ERROR JWT - Code2: Credenciales inválidas.");
                return -1;
            } else {
                System.err.println("Error getting total. Code: " + response.statusCode());
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

 // Method to obtain data with JWT authentication
    public <T> T getDataFromService(String endpoint,  TypeReference<T> typeReference, String token) {
        try {
            String url = BASE_URL + endpoint;
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");

            // Añadir header Authorization SOLO si token está presente y no es "null"
            if (tokenValido(token)) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            setStatus(response.statusCode());

            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), typeReference);
            } else if (response.statusCode() == 401) {
                System.err.println("ERROR JWT - Code3: Credenciales inválidas.");
                return null;
            } else {
                System.out.println("Error getting data. Status Code: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Method for sending data with JWT authentication
    public <T> T postDataToService(String endpoint, Object requestData, TypeReference<T> responseType, String token) {
        try {
            String url = BASE_URL + endpoint;
            HttpClient client = HttpClient.newHttpClient();
            String json = mapper.writeValueAsString(requestData);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");

            if (tokenValido(token)) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            setStatus(response.statusCode());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return mapper.readValue(response.body(), responseType);
            } else if (response.statusCode() == 401) {
                if (response != null) {
                	Map<String, Object> map = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
                	if (map.containsKey("error")) {
                        String msg = (String) map.get("error");
        				if (msg != null && msg.contains("inactive")) {
        					return mapper.readValue(response.body(), responseType);
        				}
                	}
                }
                System.err.println("ERROR JWT - Code4: Credenciales inválidas.");
                return null;
            } else {
                System.out.println("========== POST Request Debug ==========");
                System.out.println("URL: " + url);
                System.out.println("Object: " + requestData);
                System.out.println("Request Data: " + json);
                System.out.println("Token: " + (token != null ? "Present" : "Not present"));
                System.out.println("HTTP Status Code: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
 // Método interno genérico para centralizar TODA la lógica repetida
    private <T> T executePut(String endpoint, Object requestData, String token, TypeReference<T> typeReference) {
        try {
            String url = BASE_URL + endpoint;
            HttpClient client = HttpClient.newHttpClient();
            String json = mapper.writeValueAsString(requestData);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            if (tokenValido(token)) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder
                    .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            setStatus(response.statusCode());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return mapper.readValue(response.body(), typeReference);
            } else {
                System.out.println("========== PUT Request Debug ==========");
                System.out.println("URL: " + url);
                System.out.println("Object: " + requestData);
                System.out.println("Request Data: " + json);
                System.out.println("Token: " + (token != null ? "Present" : "Not present"));
                System.out.println("HTTP Status Code: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public Map<String, Object> putDataToService(String endpoint, Object requestData, String token) {
        return executePut(endpoint, requestData, token, new TypeReference<Map<String, Object>>() {});
    }
    public <T> T putDataToService(String endpoint, Object requestData, String token, TypeReference<T> typeReference) {
        return executePut(endpoint, requestData, token, typeReference);
    }

    public <T> T postDataToService(String endpoint, Object requestData, TypeReference<T> responseType) {
        return postDataToService(endpoint, requestData, responseType, null);
    }

 // Method to verify if the token is valid
    public boolean verificarToken(String token) {
        try {
            String url = BASE_URL + "/api/users/verify-token"; // endpoint para validar token
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET() // solo validamos el token, sin body
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            setStatus(response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public void deleteDataFromService(String endpoint, Long id, String token) throws Exception {
        String url = BASE_URL + endpoint + id;
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");

        if (tokenValido(token)) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = requestBuilder.DELETE().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        setStatus(response.statusCode());

        if (response.statusCode() == 403) {
            return;
        }
        if (response.statusCode() >= 400) {
            String errorMsg = "Error deleting record with ID: " + id;
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> errorMap = mapper.readValue(response.body(), new TypeReference<Map<String,Object>>() {});
                String details = (String) errorMap.get("details");
                if (details != null && !details.isEmpty()) {
                    errorMsg += " - " + details;
                }
            } catch (Exception parseEx) {
                errorMsg += " - Server response: " + response.body();
            }
            throw new Exception(errorMsg);
        }
    }

    public <T> T copy(T object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        try {
            return mapper.readValue(mapper.writeValueAsBytes(object), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error copying object", e);
        }
    }

    public <T> List<T> copyList(List<T> list, Class<T> clazz) {
        if (list == null) {
            return null;
        }
        return list.stream()
            .map(item -> copy(item, clazz))
            .collect(Collectors.toList());
    }

    public String obtenerToken() {
    	String token =  (String) FacesContext.getCurrentInstance()
            .getExternalContext()
            .getSessionMap()
            .get("token");
    	return token != null ? token.toString() : null;
    }
    public <T> List<T> getDataFromServicePaginatedWithFiltro(
            String endpoint,
            int first,
            int pageSize,
            String filtro,
            String token,
            TypeReference<List<T>> typeRef
    ) {
        try {
            String hardcodedFilter = filtro != null ? URLEncoder.encode(filtro, StandardCharsets.UTF_8) : "";
            String url = BASE_URL + endpoint +
                         "?first=" + first +
                         "&pageSize=" + pageSize +
                         "&filtro=" + (hardcodedFilter != null ? hardcodedFilter : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), typeRef);
            } else {
                System.err.println("Error pagination: " + response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public int getTotalCountFromServiceWithFiltro(String endpoint, String filtro, String token) {
        try {
            String hardcodedFilter = filtro != null ? URLEncoder.encode(filtro, StandardCharsets.UTF_8) : "";
            String url = BASE_URL + endpoint + "?filtro=" + (hardcodedFilter != null ? hardcodedFilter : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() == 200) {
                return Integer.parseInt(response.body());
            } else {
                System.err.println("Error getting count: " + response.statusCode());
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    public <T> Map<String, Object> getDataFromServiceWithPagination(
            String endpoint,
            int pageNumber,
            int pageSize,
            String sortField,
            String sortDir,
            Map<String, Object> filters,
            String token,
            Class<T> clazz
    ) {
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL + endpoint);
            urlBuilder.append("?pageNumber=").append(pageNumber);
            urlBuilder.append("&pageSize=").append(pageSize);

            if (sortField != null && !sortField.isEmpty()) {
                urlBuilder.append("&sortField=").append(sortField);
            }
            if (sortDir != null && !sortDir.isEmpty()) {
                urlBuilder.append("&sortDir=").append(sortDir);
            }

            // 🔥 Agregar filtros dinámicos (globalFilter, personalizados, etc.)
            if (filters != null) {
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    String encoded = entry.getValue() != null
                            ? URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8)
                            : "";
                    urlBuilder.append("&")
                              .append(entry.getKey())
                              .append("=")
                              .append(encoded);
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() == 200) {
                Map<String, Object> rawMap = mapper.readValue(response.body(), new TypeReference<>() {});
                String jsonArray = mapper.writeValueAsString(rawMap.get("data"));
                JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
                List<T> dataList = mapper.readValue(jsonArray, listType);

                return Map.of(
                        "data", dataList,
                        "total", ((Number) rawMap.get("total")).intValue()
                );
            }

            return Map.of("data", List.of(), "total", 0);

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("data", List.of(), "total", 0);
        }
    }

    public <T> Map<String, Object> getDataFromServiceWithPagination(
            String endpoint,
            int pageNumber,
            int pageSize,
            String sortField,
            String sortDir,
            String filtro,
            String token,
            Class<T> clazz
    ) {
        Map<String, Object> filters = new HashMap<>();

        // Mantener comportamiento antiguo
        if (filtro != null && !filtro.isEmpty()) {
            filters.put("globalFilter", filtro);
        }

        // Delegar a la verdadera implementación
        return getDataFromServiceWithPagination(
                endpoint,
                pageNumber,
                pageSize,
                sortField,
                sortDir,
                filters,
                token,
                clazz
        );
    }
    
    public <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
        return mapper.convertValue(map, clazz);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> safeCast(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }
    private boolean tokenValido(String token) {
        return token != null
                && !token.isBlank()
                && !"null".equalsIgnoreCase(token);
    }
}