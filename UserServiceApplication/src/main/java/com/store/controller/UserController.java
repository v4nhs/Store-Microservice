package com.store.controller;

import com.store.dto.OrderDTO;
import com.store.dto.request.PaymentRequest;
import com.store.dto.ProductDTO;
import com.store.model.User;
import com.store.dto.request.OrderRequest;
import com.store.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.util.Map;
import java.util.List;


@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping("/ping")
    public String ping(){ return "ok"; }
    @GetMapping("/allUser")
    public List<User> getAllUser(){
        return userService.getAllUser();
    }

    @GetMapping("/products")
    public List<ProductDTO> getAll() {
        return userService.getAllProducts();
    }

    @GetMapping("/products/{id}")
    public ProductDTO get(@PathVariable("id") String id) {
        return userService.getProductById(id);
    }
    @PostMapping("/products")
    public ProductDTO create(@RequestBody ProductDTO dto, HttpServletRequest request) {
        return userService.createProduct(dto, request);
    }

    @PutMapping("/products/{id}")
    public ProductDTO update(@PathVariable("id") String id, @RequestBody ProductDTO dto) {
        return userService.updateProduct(id, dto);
    }


    @DeleteMapping("/products/{id}")
    public void delete(@PathVariable("id") String id) {
        userService.deleteProduct(id);
    }

    @PostMapping("/orders/create")
    public ResponseEntity<String> placeOrder(@RequestBody List<OrderDTO> items, HttpServletRequest request) {
        String result = userService.placeOrder(items, request);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/orders")
    public List<OrderDTO> getAllOrder() {
        return userService.getAllOrder();
    }

    @GetMapping("/orders/{id}")
    public OrderDTO getOrderById(@PathVariable("id") String id) {
        return userService.getOrderById(id);
    }

    // COD
    @PostMapping("/orders/pay/cod")
    public ResponseEntity<String> payOrderCod(@RequestBody PaymentRequest requestBody,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                userService.payOrderWithMethod(
                        requestBody.getOrderId(),
                        requestBody.getIdempotencyKey(),
                        "COD",
                        null,
                        httpRequest
                )
        );
    }

    // PAYPAL
    @PostMapping("/orders/pay/paypal")
    public ResponseEntity<?> payOrderPaypal(@RequestBody PaymentRequest requestBody,
                                            HttpServletRequest httpRequest) {
        String result = userService.payOrderWithMethod(
                requestBody.getOrderId(),
                requestBody.getIdempotencyKey(),
                "PAYPAL",
                "PAYPAL",
                httpRequest
        );

        // 1) Nếu service đã trả URL thuần -> redirect luôn
        if (result != null) {
            String trimmed = result.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return ResponseEntity.status(302).location(URI.create(trimmed)).build();
            }
        }
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> map = om.readValue(result, new TypeReference<Map<String, Object>>() {});
            Object direct = map.get("approvalUrl");
            if (direct instanceof String s && (s.startsWith("http://") || s.startsWith("https://"))) {
                return ResponseEntity.status(302).location(URI.create(s)).build();
            }
            // Trường hợp trả kiểu PayPal body có links[]
            Object linksObj = map.get("links");
            if (linksObj instanceof List<?> links) {
                for (Object o : links) {
                    if (o instanceof Map<?, ?> lnk) {
                        Object rel = lnk.get("rel");
                        Object href = lnk.get("href");
                        if (href instanceof String h && (h.startsWith("http://") || h.startsWith("https://"))) {
                            if (rel == null) {
                                return ResponseEntity.status(302).location(URI.create(h)).build();
                            }
                            String r = String.valueOf(rel);
                            if ("approve".equalsIgnoreCase(r) || "payer-action".equalsIgnoreCase(r)) {
                                return ResponseEntity.status(302).location(URI.create(h)).build();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // Không phải JSON hoặc không parse được -> rơi xuống nhánh trả body để debug
        }

        // 3) Không tìm thấy URL -> trả body để kiểm tra
        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    @GetMapping("/products/excel/export")
    public ResponseEntity<ByteArrayResource> exportProductsExcel(HttpServletRequest request) {
        byte[] data = userService.exportProductsExcelBytes(request);
        ByteArrayResource res = new ByteArrayResource(data != null ? data : new byte[0]);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=products.xlsx")
                .contentLength(data == null ? 0 : data.length)
                .body(res);
    }

    @GetMapping("/products/excel/template")
    public ResponseEntity<ByteArrayResource> templateProductsExcel(HttpServletRequest request) {
        byte[] data = userService.templateProductsExcelBytes(request);
        ByteArrayResource res = new ByteArrayResource(data != null ? data : new byte[0]);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product-template.xlsx")
                .contentLength(data == null ? 0 : data.length)
                .body(res);
    }

    @PostMapping(value = "/products/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importProductsExcel(@RequestPart("file") MultipartFile file,
                                                      HttpServletRequest request) {
        String reportJson = userService.importProductsExcelJson(file, request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(reportJson);
    }
}
