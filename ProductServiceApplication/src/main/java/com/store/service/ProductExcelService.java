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

    // ================= Helpers theo DTO =================

    /** List<ProductSizeDTO> -> List<String> "size:qty" (vd: "S:10"), gộp trùng size */
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

    /** Tổng quantity theo DTO */
    private static int sumQuantity(Collection<ProductSizeDTO> sizes) {
        if (sizes == null) return 0;
        return sizes.stream()
                .filter(Objects::nonNull)
                .mapToInt(ps -> Math.max(0, ps.getQuantity() == null ? 0 : ps.getQuantity()))
                .sum();
    }

    /** List<String> -> List<ProductSizeDTO> (hỗ trợ "size:qty", gộp trùng size, qty>=0) */
    private static List<ProductSizeDTO> toSizeDTOs(Collection<String> sizes) {
        if (sizes == null) return List.of();

        LinkedHashMap<String, Integer> merged = new LinkedHashMap<>();
        for (String s : sizes) {
            if (s == null) continue;
            String v = s.trim();
            if (v.isEmpty()) continue;

            String size = v;
            int qty = 0;
            int idx = v.indexOf(':');
            if (idx >= 0) {
                size = v.substring(0, idx).trim();
                try {
                    qty = Integer.parseInt(v.substring(idx + 1).trim());
                } catch (NumberFormatException ignore) {
                    qty = 0;
                }
            }
            if (size.isEmpty()) continue;
            merged.merge(size, Math.max(0, qty), Integer::sum);
        }

        List<ProductSizeDTO> list = new ArrayList<>(merged.size());
        merged.forEach((size, qty) -> list.add(new ProductSizeDTO(size, qty)));
        return list;
    }

    // ============================ EXPORT ============================

    public byte[] exportAll() {
        var products = productService.getAllProducts(); // List<ProductDTO>
        var rows = products.stream()
                .map(p -> {
                    List<String> sizeStrs = toSizeStringsWithQty(p.getSizes()); // DTO -> "size:qty"
                    int totalQty = sumQuantity(p.getSizes());                   // tổng theo DTO
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

    // ============================ IMPORT ============================

    /** Import .xlsx: id trống => tạo mới (để JPA tự sinh ID); có id => update.
     *  product.quantity = SUM(quantity theo từng size trong DTO) */
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
                    BigDecimal price = (r.price() == null) ? BigDecimal.ZERO : r.price();

                    // Map List<String> (Excel) -> List<ProductSizeDTO> (DTO), có quantity theo size
                    List<ProductSizeDTO> sizeDtos = toSizeDTOs(r.sizes());

                    // ✅ tổng product.quantity = SUM(size.quantity) (DTO)
                    int totalQty = sumQuantity(sizeDtos);

                    boolean creating = (r.id() == null || r.id().isBlank());

                    ProductDTO dto = new ProductDTO(
                            creating ? null : r.id(),    // để null khi create => JPA persist (không merge)
                            r.name(),
                            r.image(),
                            sizeDtos,
                            price,
                            totalQty
                    );

                    if (creating) {
                        productService.createProduct(dto);
                        created++;
                        log.info("[IMPORT] published event for product {}", dto.getName());
                    } else {
                        productService.updateProduct(r.id(), dto);
                        updated++;
                        log.info("[IMPORT] updated & published event for product {}", dto.getName());
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
