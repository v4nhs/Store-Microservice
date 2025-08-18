package com.store.controller;

import com.store.service.ProductExcelService;
import com.store.dto.ImportReport;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/excel")
@RequiredArgsConstructor
public class ProductExcelController {

    private final ProductExcelService excelService;

    // Tải file excel tất cả sản phẩm
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> exportAll() {
        byte[] data = excelService.exportAll();
        ByteArrayResource res = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=products.xlsx")
                .contentLength(data.length)
                .body(res);
    }

    // Tải template (chỉ header)
    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() {
        byte[] data = com.store.excel.ProductExcel.write(java.util.List.of());
        ByteArrayResource res = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=products_template.xlsx")
                .contentLength(data.length)
                .body(res);
    }

    // Import file excel
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportReport> importXlsx(@RequestPart("file") MultipartFile file) {
        ImportReport report = excelService.importXlsx(file);
        return ResponseEntity.ok(report);
    }
}
