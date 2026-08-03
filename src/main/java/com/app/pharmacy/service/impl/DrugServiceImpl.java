package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.DrugControlledStatusUpdateRequest;
import com.app.pharmacy.domain.dtos.request.DrugCorrectionRequest;
import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.request.DrugPriceUpdateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;
import com.app.pharmacy.domain.entity.Drug;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.DrugRepository;
import com.app.pharmacy.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    private final DrugRepository drugRepository;

    @Override
    @Transactional
    public DrugResponse createDrug(DrugCreateRequest request) {
        Drug drug = Drug.builder()
                .name(request.name())
                .genericName(request.genericName())
                .dosageForm(request.dosageForm())
                .strength(request.strength())
                .unitPrice(request.unitPrice())
                .isControlledSubstance(request.isControlledSubstance())
                .reorderThreshold(request.reorderThreshold() != null ? request.reorderThreshold() : 10)
                .build();

        return toResponse(drugRepository.save(drug));
    }

    @Override
    @Transactional
    public DrugResponse correctDrug(UUID id, DrugCorrectionRequest request) {
        Drug drug = getDrugOrThrow(id);
        drug.setName(request.name());
        drug.setGenericName(request.genericName());
        return toResponse(drugRepository.save(drug));
    }

    @Override
    @Transactional
    public DrugResponse updatePrice(UUID id, DrugPriceUpdateRequest request) {
        Drug drug = getDrugOrThrow(id);
        drug.setUnitPrice(request.unitPrice());
        return toResponse(drugRepository.save(drug));
    }

    @Override
    @Transactional
    public DrugResponse updateControlledStatus(UUID id, DrugControlledStatusUpdateRequest request) {
        Drug drug = getDrugOrThrow(id);
        drug.setIsControlledSubstance(request.isControlledSubstance());
        return toResponse(drugRepository.save(drug));
    }

    @Override
    public DrugResponse getDrugById(UUID id) {
        return toResponse(getDrugOrThrow(id));
    }

    @Override
    public List<DrugResponse> getAllDrugs() {
        return drugRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<DrugResponse> searchDrugsByName(String name) {
        return drugRepository.findByNameContainingIgnoreCase(name).stream().map(this::toResponse).toList();
    }

    @Override
    public List<DrugResponse> getDrugsBelowReorderThreshold() {
        return drugRepository.findDrugsBelowReorderThreshold().stream().map(this::toResponse).toList();
    }

    private Drug getDrugOrThrow(UUID id) {
        return drugRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drug not found: " + id));
    }

    private DrugResponse toResponse(Drug drug) {
        return new DrugResponse(
                drug.getId(),
                drug.getName(),
                drug.getGenericName(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnitPrice(),
                drug.getIsControlledSubstance(),
                drug.getReorderThreshold()
        );
    }
}
