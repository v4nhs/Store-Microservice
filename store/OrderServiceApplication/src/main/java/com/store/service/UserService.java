package com.store.service;

import com.store.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class UserService {

    private final RestTemplate restTemplate;

    public UserDto getUserById(String userId) {
        return restTemplate.getForObject(
                "http://localhost:8081/users/" + userId,
                UserDto.class
        );
    }
}
