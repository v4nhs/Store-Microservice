package com.store.service;

import com.store.dto.ImportReport;
import com.store.dto.ProductDTO;
import com.store.excel.ProductExcel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductExcelService {

    private final ProductService productService;

    // EXPORT
    public byte[] exportAll() {
        var products = productService.getAllProducts();
        var rows = products.stream()
                .map(p -> new ProductExcel.RowData(
                        p.getId(), p.getName(), p.getPrice(), p.getQuantity()
                ))
                .toList();
        return ProductExcel.write(rows);
    }

    // IMPORT (upsert theo id; nếu id trống -> create mới)
    public ImportReport importXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ImportReport.error("File is empty");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return ImportReport.error("Only .xlsx is supported");
        }

        try {
            var parsed = ProductExcel.read(file.getInputStream());
            List<String> warnings = new ArrayList<>(parsed.errors());
            int created = 0, updated = 0, failed = 0;

            for (var r : parsed.rows()) {
                try {
                    BigDecimal price = r.price() == null ? BigDecimal.valueOf(0.0) : r.price();
                    int qty = r.quantity() == null ? 0 : r.quantity();

                    if (r.id() == null || r.id().isBlank()) {
                        // CREATE
                        ProductDTO dto = new ProductDTO(null, r.name(), price, qty);
                        productService.createProduct(dto);
                        created++;
                    } else {
                        // UPDATE
                        ProductDTO dto = new ProductDTO(r.id(), r.name(), price, qty);
                        productService.updateProduct(r.id(), dto);
                        updated++;
                    }
                } catch (Exception ex) {
                    failed++;
                    warnings.add("Row(id=" + r.id() + "): " + ex.getMessage());
                }
            }

            return new ImportReport(created, updated, failed, warnings);
        } catch (Exception e) {
            return ImportReport.error("Parse/Import failed: " + e.getMessage());
        }
    }
}
