package com.store.controller;

import com.store.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    @GetMapping("/{productId}")
    public ResponseEntity<Integer> getAvailableQuantity(@PathVariable("productId") String productId) {
        int quantity = inventoryService.getAvailableQuantity(productId);
        return ResponseEntity.ok(quantity);
    }
}
