package com.store.service;

import com.store.dto.ImportReport;
import com.store.dto.ProductDTO;
import com.store.dto.ProductSizeDTO;
import com.store.excel.ProductExcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class ProductExcelService {

    private final ProductService productService;
    private static List<String> toSizeStringsWithQty(Collection<ProductSizeDTO> sizes) {
        if (sizes == null) return List.of();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (ProductSizeDTO ps : sizes) {
            if (ps == null || ps.getSize() == null) continue;
            String size = ps.getSize().trim();
            if (size.isEmpty()) continue;
            int qty = Math.max(0, ps.getQuantity() == null ? 0 : ps.getQuantity());
            map.merge(size, qty, Integer::sum);
        }
        return map.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.toList());
    }

    private static int sumQuantity(Collection<ProductSizeDTO> sizes) {
        if (sizes == null) return 0;
        return sizes.stream()
                .filter(Objects::nonNull)
                .mapToInt(ps -> Math.max(0, Optional.ofNullable(ps.getQuantity()).orElse(0)))
                .sum();
    }

    private static List<ProductSizeDTO> toSizeDTOs(Collection<String> sizes) {
        if (sizes == null) return List.of();

        Map<String, Integer> map = new LinkedHashMap<>();
        for (String s : sizes) {
            if (s == null || s.isBlank()) continue;
            String[] parts = s.split(":");
            String size = parts[0].trim();
            int qty = (parts.length > 1) ? Integer.parseInt(parts[1].trim()) : 0;
            map.merge(size, Math.max(0, qty), Integer::sum);
        }

        return map.entrySet().stream()
                .map(e -> new ProductSizeDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public ImportReport importXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ImportReport.error("File is empty");
        }
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return ImportReport.error("Only .xlsx is supported");
        }

        try {
            var parsed = ProductExcel.read(file.getInputStream());
            List<String> warnings = new ArrayList<>(parsed.errors());
            int created = 0, updated = 0, failed = 0;

            for (var r : parsed.rows()) {
                try {
                    BigDecimal price = Optional.ofNullable(r.price()).orElse(BigDecimal.ZERO);
                    List<ProductSizeDTO> sizeDtos = toSizeDTOs(r.sizes());
                    int totalQty = sumQuantity(sizeDtos);
                    boolean creating = (r.id() == null || r.id().isBlank());

                    ProductDTO dto = new ProductDTO(
                            creating ? null : r.id(),
                            r.name(),
                            r.image(),
                            sizeDtos,
                            price,
                            totalQty
                    );

                    if (creating) {
                        productService.createProduct(dto);
                        created++;
                    } else {
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
    public byte[] exportAll() {
        var products = productService.getAllProducts(); // List<ProductDTO>
        var rows = products.stream()
                .map(p -> {
                    List<String> sizeStrs = toSizeStringsWithQty(p.getSizes());
                    int totalQty = sumQuantity(p.getSizes());
                    return new ProductExcel.RowData(
                            p.getId(),
                            p.getName(),
                            p.getImage(),
                            sizeStrs,
                            p.getPrice(),
                            totalQty
                    );
                })
                .toList();
        return ProductExcel.write(rows);
    }
//    public void clearAllVectors() {
//        qdrantVectorService.clearAllVectors();
//        log.info("All vectors have been cleared from Qdrant.");
//    }
}
