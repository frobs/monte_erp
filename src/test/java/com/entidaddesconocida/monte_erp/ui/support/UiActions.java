package com.entidaddesconocida.monte_erp.ui.support;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import javafx.application.Platform;

import org.testfx.util.WaitForAsyncUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * UI actions and assertions shared by the TestFX tests.
 *
 * <p>Everything is expressed in terms of the controls the user manipulates
 * (click, type, select a combo option, confirm a dialog) and of what the screen
 * shows (labels, table rows), so the tests stay readable and do not reach into
 * the ViewModels.</p>
 */
public final class UiActions {

    private final Stage stage;

    public UiActions(Stage stage) {
        this.stage = stage;
    }

    // --------------------------------------------------------------- actions

    /**
     * Requests the focus for the window.
     *
     * <p>The interactions below do not depend on the window being focused (they
     * drive the controls and their event handlers directly), but asking for the
     * focus keeps the screen state close to what a user would see.</p>
     */
    public void activateWindow() {
        settle();
        FxRunner.run(stage::requestFocus);
        FxRunner.waitForFxEvents();
    }

    public void click(String id) {
        settle();
        fire(node(id));
        FxRunner.waitForFxEvents();
    }

    /** Clicks a visible button whose label is exactly {@code text}. */
    public void clickButton(String text) {
        settle();
        Button button = FxRunner.get(() -> findButton(text));
        fire(button);
        FxRunner.waitForFxEvents();
    }

    /** Selects a tab of a {@link TabPane} by index. */
    public void selectTab(String tabPaneId, int index) {
        settle();
        FxRunner.run(() -> tabPane(tabPaneId).getSelectionModel().select(index));
        FxRunner.waitForFxEvents();
    }

    /**
     * Whether the row renders an action button with the given label (terminal
     * rows render none).
     */
    public boolean hasTableAction(String tableId, String rowText, String buttonText) {
        selectRow(tableId, rowText);
        settle();
        return FxRunner.get(() -> findTableAction(tableId, rowText, buttonText) != null);
    }

    public void assertNoTableAction(String tableId, String rowText, String buttonText) {
        if (hasTableAction(tableId, rowText, buttonText)) {
            throw new AssertionError("The row with '" + rowText + "' still offers '"
                    + buttonText + "'");
        }
    }

    public void type(String id, String text) {
        settle();
        Node field = node(id);
        FxRunner.run(() -> {
            if (!(field instanceof TextInputControl input)) {
                throw new AssertionError("#'" + id + "' is not a text field");
            }
            // setText goes through the TextFormatter, so the input masks of the
            // Views are exercised too.
            input.requestFocus();
            input.setText(text);
            input.positionCaret(input.getText().length());
        });
        FxRunner.waitForFxEvents();
    }

    public void clear(String id) {
        type(id, "");
    }

    public void pressEnter() {
        FxRunner.run(() -> {
            Node target = stage.getScene().getFocusOwner();
            if (target == null) {
                target = stage.getScene().getRoot();
            }
            javafx.event.Event.fireEvent(target, new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.ENTER, false, false, false, false));
        });
        FxRunner.waitForFxEvents();
    }

    /**
     * Sends ESC to the screen, as if the focus were on the layout and not on a
     * field (the application deliberately ignores ESC while a field is being
     * edited).
     */
    public void pressEscape() {
        FxRunner.run(() -> {
            sceneRoot().requestFocus();
            javafx.event.Event.fireEvent(stage.getScene(), new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
        });
        FxRunner.waitForFxEvents();
    }

    /** Opens a {@link MenuButton} and fires the item with the given label. */
    public void clickMenuItem(String menuButtonId, String itemText) {
        FxRunner.run(() -> menuButton(menuButtonId).show());
        WaitForAsyncUtils.waitForFxEvents();
        MenuItem item = FxRunner.get(() -> menuButton(menuButtonId).getItems().stream()
                .filter(candidate -> itemText.equals(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No item '" + itemText + "' in #"
                        + menuButtonId)));
        FxRunner.run(item::fire);
        FxRunner.waitForFxEvents();
    }

    /**
     * Selects the first option whose visible label contains {@code labelPart}.
     * The selection fires the same listeners as a click on the popup, which is
     * what the filter/save logic observes.
     */
    public void selectCombo(String id, String labelPart) {
        settle();
        String selected = FxRunner.get(() -> {
            ComboBox<?> combo = combo(id);
            Object match = combo.getItems().stream()
                    .filter(item -> label(combo, item).contains(labelPart))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No option containing '" + labelPart
                            + "' in #" + id + ". Options: " + labels(combo)));
            setComboValue(combo, match);
            return label(combo, match);
        });
        FxRunner.waitForFxEvents();
        if (!selected.contains(labelPart)) {
            throw new AssertionError("Combo #" + id + " did not select '" + labelPart + "'");
        }
    }

    public void selectComboIndex(String id, int index) {
        settle();
        FxRunner.run(() -> combo(id).getSelectionModel().select(index));
        FxRunner.waitForFxEvents();
    }

    public void setDate(String id, LocalDate date) {
        settle();
        FxRunner.run(() -> datePicker(id).setValue(date));
        FxRunner.waitForFxEvents();
    }

    public void setChecked(String id, boolean checked) {
        settle();
        FxRunner.run(() -> checkBox(id).setSelected(checked));
        FxRunner.waitForFxEvents();
        if (FxRunner.get(() -> checkBox(id).isSelected()) != checked) {
            throw new AssertionError("Could not " + (checked ? "check" : "uncheck") + " #" + id);
        }
    }

    /**
     * Clicks a button rendered inside a table row (the "Acciones" column).
     *
     * @param tableId      table fx:id
     * @param rowText      text that identifies the row (any cell content)
     * @param buttonText   label of the action button ("Marcar enviado", ...)
     */
    public void clickTableAction(String tableId, String rowText, String buttonText) {
        selectRow(tableId, rowText);
        settle();
        Button button = FxRunner.get(() -> {
            Button found = findTableAction(tableId, rowText, buttonText);
            if (found == null) {
                throw new AssertionError("No action button '" + buttonText
                        + "' in the row containing '" + rowText + "' of #" + tableId
                        + ". Rows: " + rowTexts(tableId));
            }
            return found;
        });
        fire(button);
        FxRunner.waitForFxEvents();
    }

    /** Selects the first row whose visible text contains {@code rowText}. */
    public int selectRow(String tableId, String rowText) {
        settle();
        int index = rowIndex(tableId, rowText);
        FxRunner.run(() -> {
            TableView<?> table = table(tableId);
            table.getSelectionModel().select(index);
            table.scrollTo(index);
        });
        FxRunner.waitForFxEvents();
        return index;
    }

    // -------------------------------------------------------------- dialogs

    /** Confirms the modal dialog currently on screen ("Aceptar"). */
    public void confirmDialog() {
        clickDialogButton(ButtonType.OK);
    }

    /** Cancels the modal dialog currently on screen ("Cancelar"). */
    public void dismissDialog() {
        clickDialogButton(ButtonType.CANCEL);
    }

    public void clickDialogButton(ButtonType type) {
        Window dialog = waitForDialog();
        DialogPane pane = FxRunner.get(() -> {
            Parent root = dialog.getScene().getRoot();
            return root instanceof DialogPane dialogPane
                    ? dialogPane : (DialogPane) root.lookup(".dialog-pane");
        });
        Node button = FxRunner.get(() -> {
            Node candidate = pane.lookupButton(type);
            if (candidate == null) {
                throw new AssertionError("The dialog has no " + type + " button");
            }
            return candidate;
        });
        fire(button);
        FxRunner.waitForFxEvents();
        FxRunner.waitUntil("the dialog to close",
                () -> FxRunner.get(() -> !dialog.isShowing()));
    }

    /** Header and content text of the modal dialog currently on screen. */
    public String dialogText() {
        Window dialog = waitForDialog();
        return FxRunner.get(() -> {
            Parent root = dialog.getScene().getRoot();
            DialogPane pane = root instanceof DialogPane dialogPane
                    ? dialogPane : (DialogPane) root.lookup(".dialog-pane");
            String header = pane.getHeaderText() == null ? "" : pane.getHeaderText();
            String content = pane.getContentText() == null ? "" : pane.getContentText();
            return header + " " + content;
        });
    }

    public boolean isDialogShowing() {
        return FxRunner.get(() -> findDialog() != null);
    }

    // ----------------------------------------------------------- assertions

    public void assertLabel(String id, String expected) {
        FxRunner.waitUntil("label #" + id + " to be '" + expected + "'",
                () -> expected.equals(textOf(id)));
    }

    public void assertLabelContains(String id, String expected) {
        FxRunner.waitUntil("label #" + id + " to contain '" + expected + "'",
                () -> textOf(id) != null && textOf(id).contains(expected));
    }

    public void assertLabelEmpty(String id) {
        FxRunner.waitUntil("label #" + id + " to be empty",
                () -> textOf(id) == null || textOf(id).isEmpty());
    }

    public void assertVisible(String id, boolean expected) {
        FxRunner.waitUntil("#" + id + (expected ? " to be visible" : " to be hidden"),
                () -> visibleOf(id) == expected);
    }

    public void assertDisabled(String id, boolean expected) {
        FxRunner.waitUntil("#" + id + (expected ? " to be disabled" : " to be enabled"),
                () -> disabledOf(id) == expected);
    }

    public void assertRowCount(String tableId, int expected) {
        FxRunner.waitUntil("#" + tableId + " to have " + expected + " rows",
                () -> rowCount(tableId) == expected);
    }

    public void assertRowContains(String tableId, String expected) {
        FxRunner.waitUntil("#" + tableId + " to contain a row with '" + expected + "'",
                () -> rowTexts(tableId).stream().anyMatch(row -> row.contains(expected)));
    }

    public void assertNoRowContains(String tableId, String unexpected) {
        FxRunner.waitUntil("#" + tableId + " to have no row with '" + unexpected + "'",
                () -> rowTexts(tableId).stream().noneMatch(row -> row.contains(unexpected)));
    }

    public void assertTitle(String expected) {
        FxRunner.waitUntil("the window title to be '" + expected + "'",
                () -> FxRunner.get(() -> expected.equals(stage.getTitle())));
    }

    public void assertTitleContains(String expected) {
        FxRunner.waitUntil("the window title to contain '" + expected + "'",
                () -> FxRunner.get(() -> stage.getTitle() != null
                        && stage.getTitle().contains(expected)));
    }

    public void assertTrue(String description, boolean condition) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    // -------------------------------------------------------------- getters

    public String textOf(String id) {
        settle();
        return FxRunner.get(() -> {
            Node node = lookup(id);
            if (node instanceof TextInputControl textInput) {
                return textInput.getText();
            }
            if (node instanceof Labeled labeled) {
                return labeled.getText();
            }
            return null;
        });
    }

    public boolean visibleOf(String id) {
        settle();
        return FxRunner.get(() -> lookup(id).isVisible());
    }

    public boolean disabledOf(String id) {
        settle();
        return FxRunner.get(() -> lookup(id).isDisabled());
    }

    public int rowCount(String tableId) {
        settle();
        return FxRunner.get(() -> table(tableId).getItems().size());
    }

    /** Visible text of every row, column by column, joined with {@code " | "}. */
    public List<String> rowTexts(String tableId) {
        settle();
        return FxRunner.get(() -> {
            TableView<?> table = table(tableId);
            List<String> texts = new ArrayList<>();
            for (Object item : table.getItems()) {
                texts.add(rowText(table, item));
            }
            return texts;
        });
    }

    public int rowIndex(String tableId, String rowText) {
        List<String> rows = rowTexts(tableId);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(rowText)) {
                return i;
            }
        }
        throw new AssertionError("No row containing '" + rowText + "' in #" + tableId
                + ". Rows: " + rows);
    }

    // -------------------------------------------------------------- internal

    /**
     * Forces a layout pass on the current scene.
     *
     * <p>The stage is resized after the first scene is built and the shell
     * replaces the scene root on every navigation; without an explicit layout
     * the tables can still be showing the rows of the previous pass (the
     * virtualised rows are created during layout).</p>
     */
    public void settle() {
        FxRunner.run(() -> {
            if (stage.getScene() == null || stage.getScene().getRoot() == null) {
                return;
            }
            Parent root = stage.getScene().getRoot();
            // The scene grows after the first view is loaded, so the first
            // layout pass is computed for the old size. Requesting it again and
            // running two passes leaves the node positions consistent with the
            // current window size.
            for (int pass = 0; pass < 3 && root.isNeedsLayout(); pass++) {
                root.applyCss();
                root.requestLayout();
                root.layout();
            }
        });
        FxRunner.waitForFxEvents();
    }

    /**
     * Runs the control action on the FX thread without blocking on it: a button
     * may open a modal dialog, and the handler only returns when the dialog is
     * closed.
     */
    private void fire(Node node) {
        if (node instanceof Button button) {
            Platform.runLater(button::fire);
            return;
        }
        if (node instanceof javafx.scene.control.Toggle toggle) {
            Platform.runLater(() -> toggle.setSelected(!toggle.isSelected()));
            return;
        }
        throw new AssertionError("Node " + node + " cannot be clicked");
    }

    private Node node(String id) {
        FxRunner.waitUntil("#" + id + " to be rendered", () -> FxRunner.get(() -> {
            Node node = sceneRoot().lookup("#" + id);
            return node != null && node.isVisible() && node.getBoundsInLocal().getWidth() > 0;
        }));
        return FxRunner.get(() -> lookup(id));
    }

    private Node lookup(String id) {
        Node node = sceneRoot().lookup("#" + id);
        if (node == null) {
            throw new AssertionError("No node with fx:id '" + id + "' on screen");
        }
        return node;
    }

    private Parent sceneRoot() {
        if (stage.getScene() == null || stage.getScene().getRoot() == null) {
            throw new AssertionError("The window has no scene yet");
        }
        return stage.getScene().getRoot();
    }

    private Button findButton(String text) {
        Button fallback = null;
        for (Node node : sceneRoot().lookupAll(".button")) {
            if (node instanceof Button candidate && text.equals(candidate.getText())) {
                if (isTreeVisible(candidate)) {
                    return candidate;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new AssertionError("No button labelled '" + text + "' on screen");
    }

    private static boolean isTreeVisible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private Button findTableAction(String tableId, String rowText, String buttonText) {
        TableView<?> table = table(tableId);
        for (Node node : table.lookupAll(".table-action-button")) {
            if (node instanceof Button candidate && buttonText.equals(candidate.getText())
                    && belongsToRow(table, candidate, rowText)) {
                return candidate;
            }
        }
        return null;
    }

    private TabPane tabPane(String id) {
        return (TabPane) lookup(id);
    }

    private MenuButton menuButton(String id) {
        return (MenuButton) lookup(id);
    }

    private ComboBox<?> combo(String id) {
        return (ComboBox<?>) lookup(id);
    }

    private DatePicker datePicker(String id) {
        return (DatePicker) lookup(id);
    }

    private CheckBox checkBox(String id) {
        return (CheckBox) lookup(id);
    }

    private TableView<?> table(String tableId) {
        return (TableView<?>) lookup(tableId);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String label(ComboBox combo, Object item) {
        if (item == null) {
            return "";
        }
        StringConverter converter = combo.getConverter();
        return converter != null ? converter.toString(item) : String.valueOf(item);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComboValue(ComboBox combo, Object item) {
        combo.setValue(item);
    }

    private static String labels(ComboBox<?> combo) {
        List<String> labels = new ArrayList<>();
        for (Object item : combo.getItems()) {
            labels.add(label(combo, item));
        }
        return labels.toString();
    }

    private static String rowText(TableView<?> table, Object item) {
        StringBuilder builder = new StringBuilder();
        for (TableColumn<?, ?> column : table.getColumns()) {
            String value = cellText(column, item);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(value);
        }
        if (builder.length() == 0) {
            builder.append(item);
        }
        return builder.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String cellText(TableColumn column, Object item) {
        if (column.getCellValueFactory() == null) {
            return null;
        }
        ObservableValue value = column.getCellObservableValue(item);
        Object cell = value == null ? null : value.getValue();
        return cell == null ? null : String.valueOf(cell);
    }

    private static boolean belongsToRow(TableView<?> table, Node node, String rowText) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TableRow<?> row) {
                Object item = row.getItem();
                return item != null && rowText(table, item).contains(rowText);
            }
        }
        return false;
    }

    private Window waitForDialog() {
        FxRunner.waitUntil("a modal dialog to appear",
                () -> FxRunner.get(this::findDialog) != null);
        return FxRunner.get(this::findDialog);
    }

    private Window findDialog() {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window == stage || window.getScene() == null) {
                continue;
            }
            Parent root = window.getScene().getRoot();
            if (root instanceof DialogPane || root.lookup(".dialog-pane") != null) {
                return window;
            }
        }
        return null;
    }
}
