package com.taskimed.config;

import jakarta.el.PropertyNotFoundException;
import jakarta.faces.application.NavigationHandler;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CustomExceptionHandler extends ExceptionHandlerWrapper {

    private static final Logger LOGGER = Logger.getLogger(CustomExceptionHandler.class.getName());
    private final ExceptionHandler wrapped;

    public CustomExceptionHandler(ExceptionHandler wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public ExceptionHandler getWrapped() {
        return wrapped;
    }

    @Override
    public void handle() throws RuntimeException {
        Iterator<ExceptionQueuedEvent> events = getUnhandledExceptionQueuedEvents().iterator();

        while (events.hasNext()) {
            ExceptionQueuedEvent event = events.next();
            ExceptionQueuedEventContext context = (ExceptionQueuedEventContext) event.getSource();

            Throwable exception = context.getException();

            try {
                FacesContext facesContext = FacesContext.getCurrentInstance();
                ExternalContext externalContext = facesContext.getExternalContext();

                // Session and response cleanup
                externalContext.invalidateSession();
                externalContext.getSessionMap().clear();
                externalContext.responseReset();
                externalContext.setResponseHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                externalContext.setResponseHeader("Pragma", "no-cache");
                externalContext.setResponseHeader("Expires", "0");

                // Custom log (clean)
                String cleanMessage;
                if (exception instanceof PropertyNotFoundException) {
                    cleanMessage = String.format("⚠ Binding error in view [%s]: %s",
                            externalContext.getRequestServletPath(),
                            exception.getMessage());
                } else {
                    cleanMessage = String.format("⚠ Unhandled exception (%s) en [%s]: %s",
                            exception.getClass().getSimpleName(),
                            externalContext.getRequestServletPath(),
                            exception.getMessage());
                }

                LOGGER.log(Level.SEVERE, cleanMessage);

                // Save error details in request
                externalContext.getRequestMap().put("jakarta.servlet.error.exception", exception);
                externalContext.getRequestMap().put("jakarta.servlet.error.message", exception.getMessage());
                externalContext.getRequestMap().put("jakarta.servlet.error.exception_type", exception.getClass());
                externalContext.getRequestMap().put("jakarta.servlet.error.request_uri",
                        externalContext.getRequestServletPath());

                // Redirect to error page.xhtml
                NavigationHandler nav = facesContext.getApplication().getNavigationHandler();
                nav.handleNavigation(facesContext, null, "/WEB-INF/error.xhtml");
                facesContext.renderResponse();

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "⚠ Error in exception handler", e);
            } finally {
                events.remove();
            }
        }

        // Important: Do not call super.handle() to prevent JSF from printing its stack trace
        // getWrapped().handle();
    }
}
