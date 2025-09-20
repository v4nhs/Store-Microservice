package com.store.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SizeQty(String size, Integer quantity) {}