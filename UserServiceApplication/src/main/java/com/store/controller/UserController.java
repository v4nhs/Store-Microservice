package com.store.controller;

import com.store.dto.OrderDto;
import com.store.dto.ProductDto;
import com.store.model.User;
import com.store.request.OrderRequest;
import com.store.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.jaxb.SpringDataJaxb;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping
    public List<User> getAllUser(){
        return userService.getAllUser();
    }

    @GetMapping("/products")
    public List<ProductDto> getAll() {
        return userService.getAllProducts();
    }

    @GetMapping("/products/{id}")
    public ProductDto get(@PathVariable String id) {
        return userService.getProductById(id);
    }

    @PostMapping("/products")
    public ProductDto create(@RequestBody ProductDto dto, HttpServletRequest request) {
        return userService.createProduct(dto, request);
    }

    @PutMapping("/products/{id}")
    public ProductDto update(@PathVariable String id, @RequestBody ProductDto dto) {
        return userService.updateProduct(id, dto);
    }

    @DeleteMapping("/products/{id}")
    public void delete(@PathVariable String id) {
        userService.deleteProduct(id);
    }

    @PostMapping("/orders")
    public ResponseEntity<String> placeOrder(@RequestBody OrderRequest orderRequest, HttpServletRequest request) {
        OrderDto dto = new OrderDto();
        dto.setProductId(orderRequest.getProductId());
        dto.setQuantity(orderRequest.getQuantity());

        String result = userService.placeOrder(dto, request);
        return ResponseEntity.ok(result);
    }
}
