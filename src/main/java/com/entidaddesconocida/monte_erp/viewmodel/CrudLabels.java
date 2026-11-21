package com.entidaddesconocida.monte_erp.viewmodel;

import java.util.function.Function;

/**
 * User facing texts of a CRUD screen: the two labels of the activate/deactivate
 * button and the six messages the {@link CrudViewModel} templates show.
 *
 * <p>They are grouped in one value instead of being eight abstract hooks, so a
 * screen only declares them once, in a constant handed to the base constructor,
 * and the template reads them from {@link CrudViewModel} without a subclass
 * method per string. The two texts that name the item
 * ({@link #deactivatedMessage()}, {@link #activatedMessage()}) are functions
 * over the entity; the rest are fixed strings.</p>
 *
 * <p>The labels stay public constants in each concrete ViewModel (the views and
 * the tests compare against them), so this record does not replace them: it
 * gathers them.</p>
 *
 * @param <T> entity managed by the screen
 */
public record CrudLabels<T>(
        String deactivateLabel,
        String activateLabel,
        String notSelectedMessage,
        String alreadyActiveMessage,
        String alreadyInactiveMessage,
        Function<T, String> deactivatedMessage,
        Function<T, String> activatedMessage,
        String saveSuccessMessage) {
}
