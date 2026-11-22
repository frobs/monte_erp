package com.entidaddesconocida.monte_erp.viewmodel;

import java.math.BigDecimal;

/**
 * User facing messages shared by every screen ViewModel (single source of
 * truth; they used to be copy-pasted constants in each ViewModel).
 *
 * <p>JDK-only utility: no JavaFX, no database, private constructor.</p>
 */
public final class ViewMessages {

    /** Shown when the database cannot be reached. */
    public static final String DATABASE_ERROR =
            "No se pudo conectar con la base de datos. Comprueba que MySQL esté arrancado.";

    /** Shown when the write succeeded but the list could not be reloaded. */
    public static final String RELOAD_ERROR =
            "Los cambios se guardaron, pero no se pudo recargar la lista. Pulsa 'Actualizar'.";

    /** Shown when the signed-in account no longer exists or was deactivated. */
    public static final String SESSION_EXPIRED =
            "La sesión ya no está activa. Inicia sesión de nuevo.";

    /** Shown when the "desde" date is after the "hasta" date. */
    public static final String PERIOD_ERROR =
            "La fecha «desde» no puede ser posterior a la fecha «hasta».";

    /** Largest total a {@code DECIMAL(10,2)} column can store. */
    public static final BigDecimal MAX_STORED_TOTAL = new BigDecimal("99999999.99");

    /** Quantity is empty, not an integer or not positive. */
    public static final String QUANTITY_INVALID =
            "La cantidad debe ser un número entero mayor que 0.";

    /** No product chosen when adding a line or a lot. */
    public static final String PRODUCT_REQUIRED = "Selecciona un producto.";

    private ViewMessages() {
        // Utility class: never instantiated.
    }

    /**
     * Joins the non-blank messages with a newline, for the screens that show
     * every field error at once (products, categories, orders, purchases).
     *
     * @return the joined messages, or the empty string when all are blank
     */
    public static String join(String... messages) {
        StringBuilder joined = new StringBuilder();
        for (String message : messages) {
            if (message == null || message.isBlank()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(message);
        }
        return joined.toString();
    }

    /** Message of a total that does not fit in the stored column ("de la compra"). */
    public static String totalLimitError(String document) {
        return String.format("El total %s no puede superar 99.999.999,99 €.", document);
    }

    /** Message of a product added twice ("la compra" / "el pedido"). */
    public static String duplicatedProduct(String document) {
        return String.format("El producto ya está en %s.", document);
    }

    /**
     * Transition rejected by the domain. The returned text keeps the two status
     * placeholders, so it can be handed to {@link StatusChanger.Messages} and
     * formatted with the origin and target status.
     */
    public static String invalidTransition(String subject) {
        return String.format("%s no puede pasar de \"%%s\" a \"%%s\".", subject);
    }

    /** The optimistic guard did not match; someone else moved the document. */
    public static String conflict(String subject, String history) {
        return String.format(
                "%s ha cambiado desde que se cargó; se ha actualizado el %s.", subject, history);
    }

    /** Saved, but the history could not be reloaded. */
    public static String saveReloadError(String document, String saved, String history) {
        return String.format("%s #%%d %s, pero no se pudo actualizar el %s. Pulsa 'Actualizar'.",
                document, saved, history);
    }

    /** Saved, but the active filter hides the new row. */
    public static String savedWithBadFilter(String document, String saved, String history,
                                            String pronoun) {
        return String.format(
                "%s #%%d %s correctamente. Corrige el filtro para ver%s en el %s.",
                document, saved, pronoun, history);
    }
}
