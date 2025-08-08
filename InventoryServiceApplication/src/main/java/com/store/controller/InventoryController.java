package com.store.controller;

import com.store.dto.ReserveStockRequest;
import com.store.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<Integer> getAvailableQuantity(@PathVariable String productId) {
        log.info("📦 [GET] Kiểm tra số lượng tồn kho cho productId: {}", productId);
        int quantity = inventoryService.getAvailableQuantity(productId);
        log.info("✅ Số lượng tồn kho hiện tại: {}", quantity);
        return ResponseEntity.ok(quantity);
    }

    @PostMapping("/{productId}/decrease")
    public ResponseEntity<Void> decreaseStock(
            @PathVariable("productId") String productId,
            @RequestParam("quantity") int quantity) {

        log.info("📦 [POST] Giảm số lượng tồn kho cho productId: {}, quantity yêu cầu: {}", productId, quantity);
        boolean success = inventoryService.decreaseStock(productId, quantity);

        if (success) {
            log.info("✅ Giảm tồn kho thành công cho productId: {}, số lượng giảm: {}", productId, quantity);
            return ResponseEntity.ok().build();
        } else {
            log.warn("❌ Giảm tồn kho thất bại cho productId: {}, yêu cầu giảm: {}, không đủ hàng.", productId, quantity);
            return ResponseEntity.badRequest().build();
        }
    }
}
