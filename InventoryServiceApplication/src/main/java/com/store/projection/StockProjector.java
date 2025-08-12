package com.store.projection;

import com.store.model.StockLedger;
import com.store.repository.InventoryRepository;
import com.store.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockProjector {

    private final InventoryRepository inventoryRepository;
    private final StockLedgerRepository ledgerRepository;

    @Transactional
    public void projectReserved(Long outboxId, String productId, int qty) {
        // idempotent: nếu đã chiếu outbox này thì bỏ qua
        if (ledgerRepository.existsById(outboxId)) return;

        // Cập nhật DB (projection)
        int ok = inventoryRepository.applyReserve(productId, qty);
        // Dù ok=0 (DB không đủ vì lệch so với Redis), projection vẫn ghi ledger để tránh loop vô hạn
        ledgerRepository.save(StockLedger.builder()
                .outboxId(outboxId)
                .eventType("STOCK_RESERVED")
                .productId(productId)
                .quantity(qty)
                .build());
    }

    @Transactional
    public void projectReleased(Long outboxId, String productId, int qty) {
        if (ledgerRepository.existsById(outboxId)) return;

        inventoryRepository.applyRelease(productId, qty);
        ledgerRepository.save(StockLedger.builder()
                .outboxId(outboxId)
                .eventType("STOCK_RELEASED")
                .productId(productId)
                .quantity(qty)
                .build());
    }
}