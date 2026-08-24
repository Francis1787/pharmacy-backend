package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.DrugControlledStatusUpdateRequest;
import com.app.pharmacy.domain.dtos.request.DrugCorrectionRequest;
import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.request.DrugPriceUpdateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;
import com.app.pharmacy.domain.entity.Drug;
import com.app.pharmacy.domain.entity.enums.DosageForm;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.DrugRepository;
import com.app.pharmacy.service.impl.DrugServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drug catalog. The Admin-only restriction on unitPrice and
 * isControlledSubstance (Rule 12) lives at the controller layer and is
 * covered in {@code DrugControllerSecurityTest}; what is checked here is
 * that each narrow update touches only the field it is meant to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DrugService — catalog")
class DrugServiceImplTest {

    @Mock private DrugRepository drugRepository;
    @InjectMocks private DrugServiceImpl drugService;

    private Drug existing;

    @BeforeEach
    void setUp() {
        existing = TestFixtures.drug("Amoxicillin", new BigDecimal("4.50"), false);
        when(drugRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(drugRepository.save(any(Drug.class))).thenAnswer(inv -> {
            Drug d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    @Test
    @DisplayName("defaults reorderThreshold to 10 when the request omits it")
    void defaultsReorderThreshold() {
        DrugResponse response = drugService.createDrug(new DrugCreateRequest(
                "Loratadine", "loratadine", DosageForm.Tablet, "10mg", new BigDecimal("1.20"), false, null));

        assertThat(response.reorderThreshold()).isEqualTo(10);
    }

    @Test
    @DisplayName("honours an explicit reorderThreshold, including zero")
    void honoursExplicitThreshold() {
        assertThat(drugService.createDrug(new DrugCreateRequest(
                "Loratadine", "loratadine", DosageForm.Tablet, "10mg", new BigDecimal("1.20"), false, 250))
                .reorderThreshold()).isEqualTo(250);

        assertThat(drugService.createDrug(new DrugCreateRequest(
                "Loratadine", "loratadine", DosageForm.Tablet, "10mg", new BigDecimal("1.20"), false, 0))
                .reorderThreshold()).isZero();
    }

    @Test
    @DisplayName("a price update changes only the price")
    void priceUpdateIsNarrow() {
        boolean controlledBefore = existing.getIsControlledSubstance();
        String nameBefore = existing.getName();

        DrugResponse response = drugService.updatePrice(existing.getId(), new DrugPriceUpdateRequest(new BigDecimal("6.75")));

        assertThat(response.unitPrice()).isEqualByComparingTo("6.75");
        assertThat(existing.getIsControlledSubstance()).isEqualTo(controlledBefore);
        assertThat(existing.getName()).isEqualTo(nameBefore);
    }

    @Test
    @DisplayName("a controlled-status update changes only that flag")
    void controlledStatusUpdateIsNarrow() {
        BigDecimal priceBefore = existing.getUnitPrice();

        DrugResponse response = drugService.updateControlledStatus(
                existing.getId(), new DrugControlledStatusUpdateRequest(true));

        assertThat(response.isControlledSubstance()).isTrue();
        assertThat(existing.getUnitPrice()).isEqualByComparingTo(priceBefore);
    }

    @Test
    @DisplayName("a name correction never touches the price or controlled flag (Rule 12)")
    void correctionCannotReachAdminOnlyFields() {
        BigDecimal priceBefore = existing.getUnitPrice();
        Boolean controlledBefore = existing.getIsControlledSubstance();

        DrugResponse response = drugService.correctDrug(
                existing.getId(), new DrugCorrectionRequest("Amoxicillin Trihydrate", "amoxicillin"));

        assertThat(response.name()).isEqualTo("Amoxicillin Trihydrate");
        assertThat(existing.getUnitPrice()).isEqualByComparingTo(priceBefore);
        assertThat(existing.getIsControlledSubstance()).isEqualTo(controlledBefore);
    }

    /**
     * OBS-05 (observation, not a defect). PATCH /drugs/{id} replaces
     * genericName with whatever the body carries, so omitting the field
     * blanks it rather than leaving it alone. That is "replace" semantics
     * under a PATCH verb; this test pins the current behaviour so any
     * future change to true partial-update semantics is a deliberate,
     * visible decision rather than an accident.
     */
    @Test
    @DisplayName("PATCH replaces genericName rather than merging — current behaviour pinned")
    void correctionOverwritesGenericNameWithNull() {
        existing.setGenericName("amoxicillin");

        drugService.correctDrug(existing.getId(), new DrugCorrectionRequest("Amoxicillin", null));

        assertThat(existing.getGenericName()).isNull();
    }

    @Test
    @DisplayName("delegates the below-threshold report to the repository query (Rule 8)")
    void delegatesBelowThresholdQuery() {
        when(drugRepository.findDrugsBelowReorderThreshold()).thenReturn(java.util.List.of(existing));

        assertThat(drugService.getDrugsBelowReorderThreshold()).hasSize(1);
        verify(drugRepository).findDrugsBelowReorderThreshold();
    }

    @Test
    @DisplayName("passes the search term through to a case-insensitive name search")
    void delegatesNameSearch() {
        when(drugRepository.findByNameContainingIgnoreCase("amox")).thenReturn(java.util.List.of(existing));

        ArgumentCaptor<String> term = ArgumentCaptor.forClass(String.class);
        drugService.searchDrugsByName("amox");
        verify(drugRepository).findByNameContainingIgnoreCase(term.capture());

        assertThat(term.getValue()).isEqualTo("amox");
    }

    @Test
    @DisplayName("404s on an unknown drug")
    void unknownDrug() {
        UUID unknown = UUID.randomUUID();
        when(drugRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> drugService.getDrugById(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Drug not found");
    }
}
