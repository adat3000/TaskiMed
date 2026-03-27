package com.taskimed.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.MessageDTO;
import com.taskimed.dto.UserDTO;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.primefaces.PrimeFaces;
import org.primefaces.component.datatable.DataTable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Named
@ViewScoped
@EqualsAndHashCode(callSuper = false)
public class MessageBean extends EntityLazyBean<MessageDTO> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Util util = new Util();

	// Asumimos que currentUserId se inyecta o se establece correctamente
	private Long currentUserId = 1L;

	private List<MessageDTO> messages = new ArrayList<>();
	private List<MessageDTO> allInbox = new ArrayList<>();
	private List<MessageDTO> allSent = new ArrayList<>();

	private MessageDTO selectedMessage = new MessageDTO();
	private MessageDTO newMessage = new MessageDTO();

	private String currentFilter = "INBOX";
	private String searchText = "";

	private List<UserDTO> allUsers = new ArrayList<>();
	private String dialogTitle = "New Message";
	private boolean canEditRecipients = true;
	
	private List<UserDTO> selectedRecipients = new ArrayList<>();

	@Inject
	private LoginBean loginBean;

	/*
	 * =========================================== CONFIGURACIÓN LAZY
	 * ===========================================
	 */
	@Override
	public String getEndpoint() {
		// Añadimos los parámetros que espera el MessageController.getPage
		return "/api/messages/paginated";
	}

	@Override
	public Class<MessageDTO> getEntityClass() {
		return MessageDTO.class;
	}

	/*
	 * =========================================== INICIALIZACIÓN / CARGA DE DATOS
	 * ===========================================
	 */

	// ... (initBean, loadAllUsers, reloadInbox, reloadSent son iguales) ...
	@PostConstruct
	public void initBean() {
		if (loginBean != null && loginBean.getUser() != null) {
			currentUserId = loginBean.getUser().getId();
		}

		// CONFIGURACIÓN CRÍTICA: Llenar filtros antes de llamar a super.init()
		// Esto evita que la primera carga del LazyDataModel falle por falta de
		// parámetros
		this.addCustomFilter("userId", currentUserId);
		this.addCustomFilter("folder", currentFilter);

		super.init();

		try {
			String token = util.obtenerToken();
			loadAllUsers(token);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadAllUsers(String token) throws Exception {
		List<UserDTO> rawUsers = util.getDataFromService("/api/users", new TypeReference<List<UserDTO>>() {
		}, token);
		if (rawUsers != null) {
			this.allUsers = rawUsers.stream().filter(user -> !user.getId().equals(currentUserId))
					.collect(Collectors.toList());
		}
	}

	private void reloadInbox(String token) throws Exception {
		allInbox = util.getDataFromService("/api/messages/inbox/" + currentUserId,
				new TypeReference<List<MessageDTO>>() {
				}, token);
	}

	private void reloadSent(String token) throws Exception {
		allSent = util.getDataFromService("/api/messages/sent/" + currentUserId, new TypeReference<List<MessageDTO>>() {
		}, token);
	}

	/*
	 * =========================================== CARGAR MENSAJE POR ID (usado en
	 * openMessage) ===========================================
	 */
	public MessageDTO loadMessageById(Long id) {
		// ... (loadMessageById es igual) ...
		try {
			String token = util.obtenerToken();

			return util.getDataFromService("/api/messages/" + id + "/" + currentUserId,
					new TypeReference<MessageDTO>() {
					}, token);
		} catch (Exception e) {
			e.printStackTrace();
			FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo cargar el mensaje.");
			FacesContext.getCurrentInstance().addMessage(null, msg);
			return null;
		}
	}

	/**
	 * Al cambiar de carpeta (Inbox, Sent, Starred, etc.), reseteamos el modelo para
	 * que dispare una nueva petición al endpoint actualizado.
	 */
	public void applyFilter() {
		// 1. Limpiar y re-establecer parámetros para la URL del REST
		clearCustomFilters();
		addCustomFilter("userId", currentUserId);
		addCustomFilter("folder", currentFilter);

		if (searchText != null && !searchText.trim().isEmpty()) {
			addCustomFilter("filter", searchText.trim());
		}

		// 2. REINICIAR LA TABLA (Vital para que Lazy vuelva a cargar desde pág 0)
		DataTable dataTable = (DataTable) FacesContext.getCurrentInstance().getViewRoot()
				.findComponent("frm:data");
		if (dataTable != null) {
			dataTable.setFirst(0);
		}

		// El lazyModel automáticamente llamará al load() al actualizarse el componente
		PrimeFaces.current().ajax().update("frm:data");
	}

	public void search() {
		applyFilter();
	}

	/*
	 * =========================================== VER MENSAJE (ACTUALIZADO: Marca
	 * como leído en Backend) ===========================================
	 */
	public void openMessage(MessageDTO msg) {
		if (msg == null)
			return;

		// 1. Asignar el mensaje seleccionado
		this.selectedMessage = msg;

		// 2. TRADUCCIÓN DE NOMBRES PARA EL DIÁLOGO
		// Si el mensaje es de salida, preparamos la lista de nombres de destinatarios
		if ("sent".equalsIgnoreCase(selectedMessage.getFolder())) {
			// this.selectedMessageRecipientNames = getRecipientNames(selectedMessage);
			this.selectedMessage.setRecipientNames(getRecipientNames(selectedMessage));
		}
		// Si el mensaje es de entrada, nos aseguramos que el senderName esté disponible
		else {
			// Si el DTO no trae el senderName, lo calculamos usando tu método existente
			if (selectedMessage.getSenderName() == null || selectedMessage.getSenderName().isEmpty()) {
				selectedMessage.setSenderName(getSenderName(selectedMessage));
			}
		}

		// 3. Lógica de Marcado como Leído (se mantiene)
		if (selectedMessage.isUnread() && !"SENT".equalsIgnoreCase(currentFilter)) {
			try {
				selectedMessage.setRead(true);
				selectedMessage.setStatus("read");
				updateMessageStateOnServer(selectedMessage);
				// Opcional: PrimeFaces.current().ajax().update("frm:data");
				// Para que el sobre se vea abierto en la tabla sin recargar todo
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 4. Actualizar el diálogo y mostrar
		PrimeFaces.current().ajax().update("formDetail");
	}

	/**
	 * Marca el mensaje seleccionado como NO LEÍDO (unread) y lo persiste en el
	 * Backend.
	 */
	public void markAsUnread(MessageDTO msgToUpdate) { // <--- ACEPTA EL MENSAJE
		// Solo aplica si el mensaje está actualmente leído y no estamos en la carpeta
		// SENT
		if (msgToUpdate == null || msgToUpdate.isUnread() || "SENT".equalsIgnoreCase(currentFilter)) {
			return;
		}

		// Usaremos el DTO pasado para la actualización, NO el selectedMessage
		try {
			// 1. Alternar estado local usando el nuevo método encapsulado
			msgToUpdate.markAsUnread(); // 👈 ¡CLAVE!

			// 2. Persistir el nuevo estado en el Backend
			updateMessageStateOnServer(msgToUpdate); // <--- Usamos el mensaje actualizado

			// 3. Recargar solo las listas en memoria para reflejar el cambio (solo si fue
			// exitoso)
			reloadAll();

			// 4. Mostrar una notificación
			FacesMessage msgInfo = new FacesMessage(FacesMessage.SEVERITY_INFO, "Success",
					"Mensaje '" + msgToUpdate.getSubject() + "' marcado como no leído.");
			FacesContext.getCurrentInstance().addMessage(null, msgInfo);

		} catch (Exception e) {
			// Si falla, revertir el estado local
			msgToUpdate.setRead(true);
			msgToUpdate.setStatus("read");
			FacesMessage msgError = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error",
					"No se pudo marcar el mensaje como no leído.");
			FacesContext.getCurrentInstance().addMessage(null, msgError);
		}
	}

	/*
	 * =========================================== NUEVO MÉTODO: Persistir el estado
	 * del mensaje (PUT) ===========================================
	 */
	private void updateMessageStateOnServer(MessageDTO msg) throws Exception {
		String token = util.obtenerToken();
		util.putDataToService("/api/messages/" + msg.getId() + "/" + currentUserId, msg, token,
				new TypeReference<MessageDTO>() {
				});
	}

	/*
	 * =========================================== ACCIONES RÁPIDAS (Star/Archive)
	 * ===========================================
	 */

	/**
	 * Alternar el estado Destacado (Starred) del mensaje seleccionado y
	 * persistirlo.
	 */
	public void toggleStar() {
		if (selectedMessage == null || "SENT".equalsIgnoreCase(currentFilter))
			return;
		try {
			selectedMessage.setStarred(!selectedMessage.getStarred());
			updateMessageStateOnServer(selectedMessage);
			applyFilter();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Alternar el estado Archivado (Archived) del mensaje seleccionado y
	 * persistirlo.
	 */
	public void toggleArchived() {
		if (selectedMessage == null || "SENT".equalsIgnoreCase(currentFilter))
			return;
		try {
			selectedMessage.setArchived(!selectedMessage.getArchived());
			updateMessageStateOnServer(selectedMessage);
			applyFilter();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ... (prepareNew, sendNew, prepareReply, prepareForward son iguales) ...

	/*
	 * =========================================== NUEVO MENSAJE
	 * ===========================================
	 */
	public void prepareNew() {
		newMessage = new MessageDTO();
		selectedRecipients = new ArrayList<>();
		dialogTitle = "New Message";
		canEditRecipients = true;
	}

	public void sendNew() {
		try {
			// Mapeamos los objetos seleccionados a sus IDs antes de enviar
	        List<Long> ids = selectedRecipients.stream()
	                                           .map(UserDTO::getId)
	                                           .collect(Collectors.toList());
	        newMessage.setRecipientIds(ids);
	        
			String token = util.obtenerToken();
			newMessage.setSenderId(currentUserId);

			util.postDataToService("/api/messages", newMessage, new TypeReference<MessageDTO>() {
			}, token);

			PrimeFaces.current().executeScript("PF('dlgNewMessage').hide()");
			applyFilter();
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Mensaje enviado con éxito"));
		} catch (Exception e) {
			e.printStackTrace();
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo enviar"));
		}
	}

	/*
	 * =========================================== RESPONDER
	 * ===========================================
	 */
	public void prepareReply() {
		if (selectedMessage == null)
			return;

		newMessage = new MessageDTO();
		dialogTitle = "Reply Message";
		canEditRecipients = false;

		newMessage.setSubject("Re: " + selectedMessage.getSubject());
		newMessage.setRecipientIds(Arrays.asList(selectedMessage.getSenderId()));
	}

	
	/* ===========================================
	FORWARD MESSAGE
	=========================================== */
	public void prepareForward() {
		if (selectedMessage == null)
			return;

		newMessage = new MessageDTO();
		dialogTitle = "Forward Message";
		canEditRecipients = true;

		// Asunto estándar en inglés para reenvíos
		newMessage.setSubject("Fwd: " + selectedMessage.getSubject());

		// Encabezado estándar de reenvío
		String header = "\n\n\n" + "---------- Forwarded message ----------\n" + "From: "
				+ getSenderName(selectedMessage) + "\n" + "Date: " + selectedMessage.getFormattedCreatedAt() + "\n"
				+ "Subject: " + selectedMessage.getSubject() + "\n\n";

		newMessage.setBody(header + selectedMessage.getBody());

		// Si usas un diálogo para redactar, asegúrate de actualizarlo
		PrimeFaces.current().ajax().update("formCompose");
	}

	public String getMessageOriginLabel() {
		return "SENT".equalsIgnoreCase(currentFilter) ? "To" : "From";
	}

	public String getSelectedMessageRecipientNames() {
		if (selectedMessage == null || selectedMessage.getRecipientIds() == null) {
			return "";
		}

		return allUsers.stream().filter(u -> selectedMessage.getRecipientIds().contains(u.getId()))
				.map(UserDTO::getFullName).collect(Collectors.joining(", "));
	}

	// Traduce el ID del emisor a Nombre (Para la columna "From")
	public String getSenderName(MessageDTO msg) {
		if (msg == null || msg.getSenderId() == null)
			return "Unknown";

		// 1. Si el remitente soy YO (el usuario logueado)
		if (msg.getSenderId().equals(this.currentUserId)) {
			// Puedes retornar "Me" o buscar tu nombre en la sesión
			return loginBean.getUser().getFullName(); // O el valor de una variable 'currentUserName'
		}

		// 2. Si es otra persona, buscar en la lista cargada
		if (allUsers == null)
			return "User " + msg.getSenderId();

		return allUsers.stream().filter(u -> u.getId().equals(msg.getSenderId())).map(UserDTO::getFullName)
				.findFirst().orElse("User " + msg.getSenderId());
	}

	// Traduce la lista de IDs de destinatarios a nombres (Para la columna "To")
	public String getRecipientNames(MessageDTO msg) {
		if (msg == null || msg.getRecipientIds() == null || msg.getRecipientIds().isEmpty()) {
			return "No recipients";
		}

		// Usamos la lista allUsers que cargaste al inicio
		return allUsers.stream().filter(u -> msg.getRecipientIds().contains(u.getId())).map(UserDTO::getFullName)
				.collect(Collectors.joining(", "));
	}

	/*
	 * =========================================== RECARGAR TODO
	 * ===========================================
	 */
	public void reloadAll() {
		try {
			String token = util.obtenerToken();
			reloadInbox(token);
			reloadSent(token);
			applyFilter();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * =========================================== DTO INTERNO
	 * ===========================================
	 */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class UserOption {
		private Long id;
		private String fullName;
	}

	/*
	 * =========================================== ELIMINAR - VERSIÓN LAZY
	 * OPTIMIZADA ===========================================
	 */
	public void delete() {
		if (selectedMessage == null)
			return;

		try {
			String token = util.obtenerToken();
			// 1. Llamada al servicio REST (ID del mensaje / ID del usuario)
			// Asegúrate de que la URL termine correctamente según tu Controller
			String endpoint = "/api/messages/" + selectedMessage.getId() + "/";
			util.deleteDataFromService(endpoint, currentUserId, token); // Ajustado según tu firma de util

			if (util.getStatus() < 400) {
				// 2. Feedback al usuario
				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Message deleted"));

				// 3. Re-aplicar filtros (esto actualiza los customFilters en la clase padre)
				applyFilter();

				// 4. Ajustar el paginador y refrescar la tabla
				// Usamos el método heredado de EntityLazyBean para una transición suave
				adjustPaginatorAfterDeletion("tableEntity", this.lazyModel);

				// 5. Limpiar selección
				selectedMessage = null;
			} else {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
						"Error", "Could not delete message. Status: " + util.getStatus()));
			}

		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Problem communicating with the server."));
			e.printStackTrace();
		}
	}
}