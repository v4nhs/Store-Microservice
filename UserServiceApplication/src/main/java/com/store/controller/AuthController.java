package com.store.controller;

import com.store.dto.UserDTO;
import com.store.model.Role;
import com.store.model.User;
import com.store.repository.RoleRepository;
import com.store.repository.UserRepository;
import com.store.security.JwtUtil;
import com.store.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepository;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody User user) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Password is required");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Role USER not found"));

        user.setRole(userRole);
        userRepository.save(user);
        return ResponseEntity.ok("User registered");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody User userInput) {
        return userRepository.findByUsername(userInput.getUsername())
                .filter(u -> passwordEncoder.matches(userInput.getPassword(), u.getPassword()))
                .map(u -> {
                    String token = jwtUtil.generateToken(u);
                    return ResponseEntity.ok(Map.of("token", token));
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid credentials")));
    }
    @GetMapping("/admin/{role}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String role) {
        UserDTO user = userService.getUserByUsername(role);
        return ResponseEntity.ok(user);
    }
}
