package com.entidaddesconocida.monte_erp.viewmodel;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseUrl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewModelTest {

    @Test
    @DisplayName("La pantalla principal no revela secretos heredados en la URL JDBC")
    void databaseLabelRedactsSensitiveUrlParameters() {
        MainViewModel viewModel = new MainViewModel(
                new User("Ana", "ana", "hash", Role.ADMIN, true),
                new DatabaseUrl("jdbc:mysql://db/erp?password=hidden&serverTimezone=UTC"));

        String databaseLabel = viewModel.databaseProperty().get();

        assertTrue(databaseLabel.contains("password=***"));
        assertTrue(databaseLabel.contains("serverTimezone=UTC"));
        assertFalse(databaseLabel.contains("hidden"));
    }
}
