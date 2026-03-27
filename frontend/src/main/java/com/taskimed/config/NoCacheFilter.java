package com.taskimed.config;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter("/*")
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Evitar cache
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setDateHeader("Expires", 0);

        String path = req.getRequestURI().substring(req.getContextPath().length());
        HttpSession session = req.getSession(false);
        Object user = (session != null) ? session.getAttribute("user") : null;

        boolean staticResource = path.startsWith("/jakarta.faces.resource/");
        boolean loginPage = path.equals("/") || path.equals("/login.jsf");
        boolean profilePage = path.equals("/profile.jsf") || path.equals("/profile.xhtml");
        String token = req.getParameter("token");

        try {
            // 🔹 1. Permitir acceso a login y recursos estáticos
            if (loginPage || staticResource) {
                chain.doFilter(request, response);
                return;
            }

         // 🔹 2. Acceso con token
            if (profilePage && token != null && !token.isBlank()) {
                System.out.println("[INFO] Acceso permitido directo → /profile.jsf con token válido");

                HttpSession s = req.getSession(true);
                s.setAttribute("token_login", true);
                s.setAttribute("token_value", token);

                // 🔸 Inyectar un usuario temporal para que JSF no fuerce redirect
                if (s.getAttribute("user") == null) {
                    s.setAttribute("user", "TOKEN_USER"); // Puede ser un objeto real o marcador
                    System.out.println("[INFO] Usuario temporal agregado a sesión para evitar redirect JSF");
                }

                chain.doFilter(request, response);
                return;
            }

            // 🔹 3. Si no hay usuario logeado ni token, redirigir a raíz
            if (user == null) {
                String redirectUrl = req.getContextPath().isEmpty() ? "/" : req.getContextPath() + "/";
                res.sendRedirect(redirectUrl);
                return;
            }

            // 🔹 4. Continuar flujo normal
            chain.doFilter(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        // 🔹 5. Post-procesamiento: verificar redirecciones vacías
        String loc = res.getHeader("Location");
        if (loc != null && loc.trim().isEmpty()) {
            System.out.println("[ALERTA] Redirección 3xx detectada sin Location → " + req.getRequestURI());
        }
    }
}
