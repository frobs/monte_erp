package com.entidaddesconocida.monte_erp.viewmodel;

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
}
