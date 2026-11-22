package com.entidaddesconocida.monte_erp.viewmodel;

import java.util.function.Function;

/**
 * User facing texts that name the contact of a screen (customer or supplier):
 * the required-name message, the duplicate-name message, the counter label and
 * the two form titles.
 *
 * <p>The field lengths, the phone/email shapes and the rest of the rules are
 * identical in both screens, so only the texts that depend on the business noun
 * live here. Grouped in a record, a subclass declares them once instead of
 * overriding eight methods.</p>
 */
public record ContactLabels(
        String nameRequiredMessage,
        Function<String, String> duplicateNameMessage,
        String countLabel,
        String newFormTitle,
        String editFormTitle) {
}
