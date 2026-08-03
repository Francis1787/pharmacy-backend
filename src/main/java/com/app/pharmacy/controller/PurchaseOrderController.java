package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.MarkDeliveredRequest;
import com.app.pharmacy.domain.dtos.request.PurchaseOrderCreateRequest;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderResponse;
import com.app.pharmacy.security.CustomUserDetails;
import com.app.pharmacy.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Rule 16 — Admin owns procurement end to end; the one exception is awaiting-verification, shared with Pharmacist (Rule 11, 17). */
@Tag(name = "Purchase Orders", description = "Procurement and delivery tracking")
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PurchaseOrderCreateRequest request
    ) {
        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request, principal.getStaffId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Purchase order created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getAllPurchaseOrders() {
        List<PurchaseOrderResponse> orders = purchaseOrderService.getAllPurchaseOrders();
        return ResponseEntity.ok(ApiResponse.success(orders, "Purchase orders retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrderById(@PathVariable UUID id) {
        PurchaseOrderResponse response = purchaseOrderService.getPurchaseOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Purchase order retrieved successfully"));
    }

    @PatchMapping("/{id}/mark-delivered")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> markDelivered(
            @PathVariable UUID id, @Valid @RequestBody MarkDeliveredRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.markDelivered(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Purchase order marked as delivered"));
    }

    /** Rule 17 — overdue: past expected date, not yet delivered, not cancelled. */
    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getOverduePurchaseOrders() {
        List<PurchaseOrderResponse> orders = purchaseOrderService.getOverduePurchaseOrders();
        return ResponseEntity.ok(ApiResponse.success(orders, "Overdue purchase orders retrieved successfully"));
    }

    /** Rule 11, 17 — delivered orders with a controlled-substance batch still unverified. Shared with Pharmacist. */
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    @GetMapping("/awaiting-verification")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPurchaseOrdersAwaitingVerification() {
        List<PurchaseOrderResponse> orders = purchaseOrderService.getPurchaseOrdersAwaitingVerification();
        return ResponseEntity.ok(ApiResponse.success(orders, "Purchase orders awaiting verification retrieved successfully"));
    }
}
