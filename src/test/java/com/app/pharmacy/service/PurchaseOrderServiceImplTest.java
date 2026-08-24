package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.MarkDeliveredRequest;
import com.app.pharmacy.domain.dtos.request.PurchaseOrderCreateRequest;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.impl.PurchaseOrderServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Procurement: ordering, delivery marking, and the overdue / awaiting-verification
 * views (Rules 16, 17).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PurchaseOrderService — procurement")
class PurchaseOrderServiceImplTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PurchaseOrderServiceImpl purchaseOrderService;

    private Supplier supplier;
    private Staff admin;
    private Drug drug;

    @BeforeEach
    void setUp() {
        supplier = TestFixtures.supplier();
        admin = TestFixtures.admin();
        drug = TestFixtures.drug();

        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(staffRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(drugRepository.findById(drug.getId())).thenReturn(Optional.of(drug));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
        when(purchaseOrderItemRepository.save(any(PurchaseOrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderItemRepository.findByPurchaseOrderId(any())).thenReturn(List.of());
    }

    private PurchaseOrderCreateRequest request() {
        return new PurchaseOrderCreateRequest(supplier.getId(), LocalDate.now().plusDays(7),
                List.of(new PurchaseOrderCreateRequest.Item(drug.getId(), 500, new BigDecimal("1.80"))));
    }

    @Nested
    @DisplayName("Creating an order")
    class Creation {

        @Test
        @DisplayName("opens the order as Pending on today's date")
        void opensPending() {
            PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request(), admin.getId());

            assertThat(response.status()).isEqualTo(PurchaseOrderStatus.Pending);
            assertThat(response.orderDate()).isEqualTo(LocalDate.now());
            assertThat(response.actualDeliveryDate()).isNull();
        }

        @Test
        @DisplayName("persists every requested line item")
        void persistsLineItems() {
            Drug second = TestFixtures.drug("Metformin", new BigDecimal("5.20"), false);
            when(drugRepository.findById(second.getId())).thenReturn(Optional.of(second));

            purchaseOrderService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                    supplier.getId(), LocalDate.now().plusDays(3),
                    List.of(new PurchaseOrderCreateRequest.Item(drug.getId(), 100, new BigDecimal("2.00")),
                            new PurchaseOrderCreateRequest.Item(second.getId(), 50, new BigDecimal("5.00")))),
                    admin.getId());

            verify(purchaseOrderItemRepository, times(2)).save(any(PurchaseOrderItem.class));
        }

        @Test
        @DisplayName("audits the order creation against its creator (Rule 7)")
        void auditsCreation() {
            purchaseOrderService.createPurchaseOrder(request(), admin.getId());

            verify(auditLogService).logAction(eq(admin.getId()), eq(ActionType.PURCHASE_ORDER_CREATED),
                    any(UUID.class), eq("PurchaseOrder"), anyString());
        }

        @Test
        @DisplayName("404s on an unknown supplier")
        void unknownSupplier() {
            UUID unknown = UUID.randomUUID();
            when(supplierRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(new PurchaseOrderCreateRequest(
                    unknown, LocalDate.now().plusDays(1),
                    List.of(new PurchaseOrderCreateRequest.Item(drug.getId(), 1, BigDecimal.ONE))), admin.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Supplier not found");
        }
    }

    @Nested
    @DisplayName("Marking delivered")
    class Delivery {

        @Test
        @DisplayName("moves a Pending order to Received and records the delivery date")
        void marksReceived() {
            PurchaseOrder order = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Pending);
            when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            PurchaseOrderResponse response = purchaseOrderService.markDelivered(
                    order.getId(), new MarkDeliveredRequest(LocalDate.now()));

            assertThat(response.status()).isEqualTo(PurchaseOrderStatus.Received);
            assertThat(response.actualDeliveryDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("clears the overdue flag once the delivery is recorded")
        void clearsOverdueFlag() {
            PurchaseOrder order = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Pending);
            order.setExpectedDeliveryDate(LocalDate.now().minusDays(5));
            when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThat(purchaseOrderService.getPurchaseOrderById(order.getId()).isOverdue()).isTrue();

            PurchaseOrderResponse delivered = purchaseOrderService.markDelivered(
                    order.getId(), new MarkDeliveredRequest(LocalDate.now()));

            assertThat(delivered.isOverdue()).isFalse();
        }

        @Test
        @DisplayName("404s on an unknown order")
        void unknownOrder() {
            UUID unknown = UUID.randomUUID();
            when(purchaseOrderRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.markDelivered(unknown, new MarkDeliveredRequest(LocalDate.now())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        /**
         * DEFECT-04. markDelivered() writes status = Received unconditionally.
         * A Cancelled order can therefore be resurrected into Received, and an
         * already-Received order can have its delivery date silently rewritten,
         * with no audit entry for either. Every other state transition in this
         * codebase (prescription approve/reject) guards its starting state;
         * this one does not.
         */
        @Test
        @Tag("known-defect")
        @DisplayName("refuses to mark a Cancelled order as delivered")
        void refusesToDeliverCancelledOrder() {
            PurchaseOrder cancelled = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Cancelled);
            when(purchaseOrderRepository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> purchaseOrderService.markDelivered(
                    cancelled.getId(), new MarkDeliveredRequest(LocalDate.now())))
                    .as("a cancelled order must not transition to Received")
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("Rule 17 — overdue and awaiting-verification views")
    class Views {

        @Test
        @DisplayName("flags an undelivered order past its expected date as overdue")
        void flagsOverdue() {
            PurchaseOrder late = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Pending);
            late.setExpectedDeliveryDate(LocalDate.now().minusDays(1));
            when(purchaseOrderRepository.findById(late.getId())).thenReturn(Optional.of(late));

            assertThat(purchaseOrderService.getPurchaseOrderById(late.getId()).isOverdue()).isTrue();
        }

        @Test
        @DisplayName("does not flag an order due today as overdue")
        void dueTodayIsNotOverdue() {
            PurchaseOrder dueToday = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Pending);
            dueToday.setExpectedDeliveryDate(LocalDate.now());
            when(purchaseOrderRepository.findById(dueToday.getId())).thenReturn(Optional.of(dueToday));

            assertThat(purchaseOrderService.getPurchaseOrderById(dueToday.getId()).isOverdue()).isFalse();
        }

        @Test
        @DisplayName("does not flag a Cancelled order as overdue")
        void cancelledIsNotOverdue() {
            PurchaseOrder cancelled = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Cancelled);
            cancelled.setExpectedDeliveryDate(LocalDate.now().minusDays(30));
            when(purchaseOrderRepository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));

            assertThat(purchaseOrderService.getPurchaseOrderById(cancelled.getId()).isOverdue()).isFalse();
        }

        @Test
        @DisplayName("lists each order awaiting controlled-substance verification exactly once")
        void deduplicatesAwaitingVerification() {
            PurchaseOrder order = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Received);
            Drug controlled = TestFixtures.drug("Codeine", new BigDecimal("30.00"), true);

            // Two unverified controlled batches delivered against the SAME order.
            Batch first = TestFixtures.batch(controlled, 20);
            Batch second = TestFixtures.batch(controlled, 30);
            first.setPurchaseOrderItem(poItem(order, controlled));
            second.setPurchaseOrderItem(poItem(order, controlled));
            when(batchRepository.findUnverifiedControlledSubstanceBatches()).thenReturn(List.of(first, second));

            List<PurchaseOrderResponse> awaiting = purchaseOrderService.getPurchaseOrdersAwaitingVerification();

            assertThat(awaiting).hasSize(1);
            assertThat(awaiting.getFirst().id()).isEqualTo(order.getId());
        }

        @Test
        @DisplayName("returns an empty list when nothing is awaiting verification")
        void emptyWhenNothingAwaiting() {
            when(batchRepository.findUnverifiedControlledSubstanceBatches()).thenReturn(List.of());

            assertThat(purchaseOrderService.getPurchaseOrdersAwaitingVerification()).isEmpty();
        }

        @Test
        @DisplayName("orders the awaiting-verification list oldest order first")
        void sortsAwaitingByOrderDate() {
            PurchaseOrder older = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Received);
            older.setOrderDate(LocalDate.now().minusDays(30));
            PurchaseOrder newer = TestFixtures.purchaseOrder(supplier, admin, PurchaseOrderStatus.Received);
            newer.setOrderDate(LocalDate.now().minusDays(2));

            Drug controlled = TestFixtures.drug("Codeine", new BigDecimal("30.00"), true);
            Batch newBatch = TestFixtures.batch(controlled, 10);
            Batch oldBatch = TestFixtures.batch(controlled, 10);
            newBatch.setPurchaseOrderItem(poItem(newer, controlled));
            oldBatch.setPurchaseOrderItem(poItem(older, controlled));
            when(batchRepository.findUnverifiedControlledSubstanceBatches())
                    .thenReturn(List.of(newBatch, oldBatch));

            List<PurchaseOrderResponse> awaiting = purchaseOrderService.getPurchaseOrdersAwaitingVerification();

            assertThat(awaiting).extracting(PurchaseOrderResponse::id)
                    .containsExactly(older.getId(), newer.getId());
        }

        private PurchaseOrderItem poItem(PurchaseOrder order, Drug drug) {
            return PurchaseOrderItem.builder()
                    .id(UUID.randomUUID())
                    .purchaseOrder(order)
                    .drug(drug)
                    .quantityOrdered(100)
                    .unitCost(new BigDecimal("25.00"))
                    .build();
        }
    }
}
