package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.AuthResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.GoogleTokenResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.GoogleUserInfo;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.InvalidTokenException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.AuthProvider;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.security.jwt.JwtTokenProvider;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Service for handling authentication-related operations. Provides methods for Google OAuth2 login and token refresh
 * functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Performs the Google OAuth2 login flow. Exchanges authorization code for tokens, retrieves user information, and
     * creates or updates the user in the database.
     *
     * @param authorizationCode the authorization code from Google OAuth2
     * @return AuthResponse containing access token, refresh token and first login status
     * @throws IOException if there is an error communicating with Google APIs
     */
    public AuthResponse loginWithGoogle(String authorizationCode) throws IOException {
        // Exchange code for token
        GoogleTokenResponse tokenResponse = getGoogleToken(authorizationCode);

        // Get user info from Google
        GoogleUserInfo userInfo = getGoogleUserInfo(tokenResponse.getAccessToken());

        // Check if user exists
        boolean isFirstLogin = !userRepository.existsByEmail(userInfo.getEmail());

        // Find or create user
        User user = findOrCreateUser(userInfo);

        // Generate refresh token
        String refreshToken = UUID.randomUUID().toString();

        // Generate JWT token
        String accessToken = jwtTokenProvider.createToken(
                user.getEmail(),
                user.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList())
        );

        // Update user with refresh token and last issued access token
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpireDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        user.setLastIssuedAccessToken(accessToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isFirstLogin(isFirstLogin)
                .userId(user.getId().toString())
                .build();
    }

    /**
     * Exchanges an authorization code for a Google access token.
     *
     * @param code the authorization code from Google OAuth2
     * @return GoogleTokenResponse containing access token and other token information
     * @throws IOException if there is an error communicating with Google APIs
     */
    private GoogleTokenResponse getGoogleToken(String code) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("code", code)
                .add("client_id", googleClientId)
                .add("client_secret", googleClientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build();

        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }

            assert response.body() != null;
            return objectMapper.readValue(response.body().string(), GoogleTokenResponse.class);
        }
    }

    /**
     * Retrieves user information from Google using an access token.
     *
     * @param accessToken the Google access token
     * @return GoogleUserInfo containing user details from Google
     * @throws IOException if there is an error communicating with Google APIs
     */
    private GoogleUserInfo getGoogleUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }

            assert response.body() != null;
            return objectMapper.readValue(response.body().string(), GoogleUserInfo.class);
        }
    }

    /**
     * Finds an existing user or creates a new one based on Google user information.
     *
     * @param userInfo user information from Google
     * @return User entity from the database
     */
    private User findOrCreateUser(GoogleUserInfo userInfo) {
        return userRepository.findByEmail(userInfo.getEmail())
                .orElseGet(() -> User.builder()
                        .email(userInfo.getEmail())
                        .name(userInfo.getName())
                        .provider(AuthProvider.GOOGLE)
                        .providerId(userInfo.getId())
                        .roles(java.util.List.of("ROLE_USER"))
                        .build());
    }

    /**
     * Refreshes an access token using a refresh token. Validates the refresh token and issues new access and refresh
     * tokens.
     *
     * @param refreshToken the refresh token
     * @param accessToken  the current (likely expired) access token
     * @return AuthResponse containing new access and refresh tokens
     * @throws InvalidTokenException if the refresh token is invalid or expired
     */
    public AuthResponse refreshToken(String refreshToken, String accessToken) {
        // Validate access token structure first
        if (!jwtTokenProvider.validateToken(accessToken)) {
            log.debug("Access token is invalid or expired as expected");
        }

        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // Verify that this is the most recently issued access token
        if (!accessToken.equals(user.getLastIssuedAccessToken())) {
            throw new InvalidTokenException("Access token is not the most recently issued token");
        }

        if (user.getRefreshTokenExpireDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token expired");
        }

        String newAccessToken = jwtTokenProvider.createToken(
                user.getEmail(),
                user.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList())
        );

        String newRefreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpireDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        user.setLastIssuedAccessToken(newAccessToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .isFirstLogin(false)  // Not first login during refresh
                .userId(user.getId().toString())
                .build();
    }
}
