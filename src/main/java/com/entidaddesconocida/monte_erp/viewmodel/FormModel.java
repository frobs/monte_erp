package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The form half of a CRUD screen: loads an entity into the fields, clears them
 * and owns the per-field validation.
 *
 * <p>Extracted from {@link CrudViewModel} (A3b) so a screen can <strong>compose</strong>
 * its form instead of inheriting a template. The screen keeps its concrete
 * properties (and its {@code *Error} wrappers), and hands this class two things
 * only: how to load an entity into those properties and how to reset them. The
 * rest — which rules exist, when the live re-validation runs, how the summary is
 * built — lives here once.</p>
 *
 * <p>The lifecycle mirrors what the five screens already had:</p>
 * <ul>
 *   <li>{@link #populate(Object)} / {@link #clear()} reset the "already
 *       validated" flag <em>before</em> touching the properties, so the live
 *       listener does not paint errors while a row is being loaded, and then
 *       drop the previous field errors.</li>
 *   <li>{@link #startValidation()} marks the first save attempt; from then on
 *       every change in one of the {@link #watch watched} sources re-runs
 *       {@link #refresh()} and updates the summary.</li>
 *   <li>{@link #watchLive live fields} (today the phone of suppliers and
 *       customers) validate on every change, even before the first save.</li>
 * </ul>
 *
 * <p>JavaFX-only and database-free, like the ViewModels it serves: plain JUnit
 * tests do not need the toolkit.</p>
 *
 * @param <T> entity whose values are loaded into the form
 */
public final class FormModel<T> {

    /** How the messages of several invalid fields are summarized. */
    public enum SummaryPolicy {
        /** Every non-blank message, separated by newlines (products, categories…). */
        JOINED,
        /** Only the first non-blank message (users). */
        FIRST
    }

    private record Field(ReadOnlyStringWrapper error, Supplier<String> rule) {
    }

    private final Feedback feedback;
    private final Consumer<T> populate;
    private final Runnable clear;
    private final List<Field> fields = new ArrayList<>();

    private SummaryPolicy summaryPolicy = SummaryPolicy.JOINED;
    private boolean validateOnEdit;

    /**
     * @param feedback reports the summary and the always-on field messages
     * @param populate loads one entity into the screen properties
     * @param clear    resets the screen properties to the "new item" defaults
     */
    public FormModel(Feedback feedback, Consumer<T> populate, Runnable clear) {
        this.feedback = feedback;
        this.populate = populate;
        this.clear = clear;
    }

    /** Registers a field: its error property and the rule that fills it. */
    public FormModel<T> field(ReadOnlyStringWrapper error, Supplier<String> rule) {
        fields.add(new Field(error, rule));
        return this;
    }

    /** Chooses how several field messages are summarized. */
    public FormModel<T> summaryPolicy(SummaryPolicy policy) {
        this.summaryPolicy = policy;
        return this;
    }

    /**
     * Registers the sources whose change re-runs the validation after the first
     * save attempt. Called from the screen constructor, once its properties exist.
     */
    public FormModel<T> watch(ObservableValue<?>... sources) {
        for (ObservableValue<?> source : sources) {
            source.addListener((observable, oldValue, newValue) -> {
                if (validateOnEdit) {
                    refresh();
                    feedback.error(hasErrors() ? summary() : "");
                }
            });
        }
        return this;
    }

    /**
     * Registers an always-on live validation for a single field: the error is
     * recomputed on every change, even before the first save attempt.
     *
     * <p>After the first save attempt the generic {@link #watch} listener already
     * refreshes the whole summary, so here only the field error is written.
     * Before that moment the summary is updated directly, but it is cleared only
     * when it still holds this field's previous message: a business or database
     * error shown in the meantime must survive.</p>
     */
    public FormModel<T> watchLive(ObservableValue<?> source,
            Supplier<String> rule, ReadOnlyStringWrapper error) {
        source.addListener((observable, oldValue, newValue) -> {
            String previous = error.get();
            String message = rule.get();
            error.set(message);
            if (validateOnEdit) {
                return;
            }
            if (!message.isBlank()) {
                feedback.fail(message);
            } else if (feedback.error().equals(previous)) {
                feedback.clearError();
            }
        });
        return this;
    }

    /** Loads {@code item} into the form and drops the previous field errors. */
    public void populate(T item) {
        validateOnEdit = false;
        populate.accept(item);
        clearErrors();
    }

    /** Resets the form to the "new item" defaults and drops the field errors. */
    public void clear() {
        validateOnEdit = false;
        clear.run();
        clearErrors();
    }

    /** Marks the first save attempt: from now on the fields re-validate live. */
    public void startValidation() {
        validateOnEdit = true;
    }

    /** {@code true} once the user has tried to save at least once. */
    public boolean isValidateOnEdit() {
        return validateOnEdit;
    }

    /** Recomputes every field error from its rule. */
    public void refresh() {
        for (Field field : fields) {
            field.error().set(field.rule().get());
        }
    }

    /** Whether any field is currently rejected. */
    public boolean hasErrors() {
        return fields.stream().anyMatch(field -> !field.error().get().isBlank());
    }

    /** Summary shown in the error label, per the configured {@link SummaryPolicy}. */
    public String summary() {
        if (summaryPolicy == SummaryPolicy.FIRST) {
            return fields.stream()
                    .map(field -> field.error().get())
                    .filter(message -> !message.isBlank())
                    .findFirst()
                    .orElse("");
        }
        return ViewMessages.join(fields.stream()
                .map(field -> field.error().get())
                .toArray(String[]::new));
    }

    private void clearErrors() {
        for (Field field : fields) {
            field.error().set("");
        }
    }
}
