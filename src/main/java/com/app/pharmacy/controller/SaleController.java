package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.SaleCreateRequest;
import com.app.pharmacy.domain.dtos.response.SaleResponse;
import com.app.pharmacy.security.CustomUserDetails;
import com.app.pharmacy.service.SaleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Sales", description = "Dispensing and payment")
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    /** Rule 15 — only a Pharmacist may complete a sale. principal.getStaffId() is the default cashier/dispensing pharmacist when the request omits them. */
    @PreAuthorize("hasRole('PHARMACIST')")
    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody SaleCreateRequest request
    ) {
        SaleResponse response = saleService.createSale(request, principal.getStaffId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Sale completed successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleResponse>>> getAllSales(
            @RequestParam(required = false) UUID pharmacistId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<SaleResponse> sales;
        if (pharmacistId != null) {
            sales = saleService.getSalesByDispensingPharmacist(pharmacistId);
        } else if (from != null && to != null) {
            sales = saleService.getSalesByDateRange(from, to);
        } else {
            sales = saleService.getAllSales();
        }
        return ResponseEntity.ok(ApiResponse.success(sales, "Sales retrieved successfully"));
    }

    /** Doubles as the receipt view. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(saleService.getSaleById(id), "Sale retrieved successfully"));
    }
}
