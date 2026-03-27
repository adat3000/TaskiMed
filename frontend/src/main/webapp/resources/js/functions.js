/**
 * Actualiza el contador de caracteres y el color de advertencia.
 * @param {HTMLTextAreaElement} textarea - El elemento textarea.
 * @param {string} counterId - El ID del elemento span del contador.
 * @param {number} limit - El límite máximo de caracteres permitido.
 */
function updateDescCounterColor(textarea, counterId, limit) {
    const counter = document.getElementById(counterId);
    if (!textarea || !counter || typeof limit !== 'number') return;

    const length = textarea.value.length;
    
    // 1. Actualizar el texto del contador (Longitud actual / Límite)
    counter.textContent = length + " / " + limit;

    // 2. Calcular el umbral de advertencia (Ejemplo: 90% del límite)
    // Usamos Math.floor para asegurar que sea un número entero.
    const warningThreshold = Math.floor(limit * 0.90); 

    // 3. Aplicar color de advertencia
    counter.style.color = (length >= warningThreshold) ? "red" : "#555";
}

/**
 * Inicializa el contador al abrir el diálogo/página.
 * @param {string} textareaId - El ID del elemento textarea.
 * @param {string} counterId - El ID del elemento span del contador.
 * @param {number} limit - El límite máximo de caracteres permitido.
 */
function initDescCounter(textareaId, counterId, limit) {
    const textarea = document.getElementById(textareaId);
    // Llamar a la función principal con el límite
    updateDescCounterColor(textarea, counterId, limit);
}