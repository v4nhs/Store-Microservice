package com.store.controller;

import com.store.dto.OrderDTO;
import com.store.dto.PaymentRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping("/ping") public String ping(){ return "ok"; }
    @GetMapping
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

    @PostMapping("/orders")
    public ResponseEntity<String> placeOrder(@RequestBody OrderRequest orderRequest, HttpServletRequest request) {
        OrderDTO dto = new OrderDTO();
        dto.setProductId(orderRequest.getProductId());
        dto.setQuantity(orderRequest.getQuantity());

        String result = userService.placeOrder(dto, request);
        return ResponseEntity.ok(result);
    }
    @PostMapping("/orders/multi")
    public ResponseEntity<String> placeOrderMulti(@RequestBody List<OrderDTO> items, HttpServletRequest request) {
        String result = userService.placeOrderMulti(items, request);
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

    @PostMapping("/orders/pay")
    public ResponseEntity<String> payOrder(@RequestBody PaymentRequest requestBody,
                                           HttpServletRequest httpRequest) {

        String orderId = requestBody.getOrderId();
        String idempotencyKey = requestBody.getIdempotencyKey();

        return ResponseEntity.ok(
                userService.payOrder(orderId, idempotencyKey, httpRequest)
        );
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
