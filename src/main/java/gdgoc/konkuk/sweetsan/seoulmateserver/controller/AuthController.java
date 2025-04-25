package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.AuthResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.LoginRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.RefreshTokenRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.GlobalExceptionHandler;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST controller for handling authentication operations.
 * Provides endpoints for user login through Google OAuth2 and token refresh.
 * This controller is the main entry point for authentication flows in the application.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API")
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates a user using a Google OAuth2 authorization code.
     * If the user doesn't exist in the system, they will be automatically registered.
     * 
     * @param request The login request containing the authorization code
     * @return Authentication tokens (access token and refresh token)
     * @throws IOException If there's an error communicating with Google's OAuth service
     */
    @Operation(summary = "Login with Google", description = "Login with Google OAuth2 authorization code. If user doesn't exist, registration is done automatically.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully logged in", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AuthResponse.class))}),
        @ApiResponse(responseCode = "400", description = "Invalid authorization code",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
        @ApiResponse(responseCode = "500", description = "Error with Google OAuth service",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) throws IOException {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getAuthorizationCode()));
    }

    /**
     * Refreshes the authentication tokens using a valid refresh token.
     * This endpoint is used when the access token has expired but the refresh token is still valid.
     * 
     * @param request The refresh request containing the current refresh token and expired access token
     * @return New authentication tokens (access token and refresh token)
     */
    @Operation(summary = "Refresh Token", description = "Get new access and refresh tokens using current tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully refreshed tokens", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AuthResponse.class))}),
        @ApiResponse(responseCode = "400", description = "Invalid refresh token",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Expired or invalid tokens",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), request.getAccessToken()));
    }
}
