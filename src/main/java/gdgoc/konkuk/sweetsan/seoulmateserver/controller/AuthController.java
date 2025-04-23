package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.AuthResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.LoginRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.RefreshTokenRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Login with Google", description = "Login with Google OAuth2 authorization code. If user doesn't exist, registration is done automatically.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) throws IOException {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getAuthorizationCode()));
    }

    @Operation(summary = "Refresh Token", description = "Get new access and refresh tokens using current tokens")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), request.getAccessToken()));
    }
}
