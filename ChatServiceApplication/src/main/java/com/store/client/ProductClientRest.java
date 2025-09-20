package com.store.client;

import com.store.dto.ProductDTO;
import jdk.jfr.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Log4j2
public class ProductClientRest {

    private final RestTemplate rt;

    @Value("${product.service.base-url}")
    String base;

    public ProductDTO getById(String id) {
        try {
            return rt.getForObject(base + "/api/products/{id}", ProductDTO.class, id);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            return null;
        }
    }

    public ProductDTO getByName(String name) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(base)
                    .path("/api/products/search")
                    .queryParam("name", name)
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            log.info("[product-client] GET {}", uri);
            return rt.getForObject(uri, ProductDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            return null;
        }
    }
}
