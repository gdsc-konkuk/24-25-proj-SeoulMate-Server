package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.AuthResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.InvalidTokenException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.AuthProvider;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for handling authentication-related operations. Provides methods for Google OpenID Connect (OIDC) login and
 * token refresh functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Authenticates a user using a Google ID token. Verifies the token with Google's tokeninfo endpoint, creates or
     * retrieves the user, and issues JWT tokens.
     *
     * @param idToken the ID token received from Google Sign-In
     * @return AuthResponse containing access token, refresh token and first login status
     * @throws IOException if there is an error communicating with Google's tokeninfo endpoint
     */
    public AuthResponse loginWithGoogle(String idToken) throws IOException {
        // Parse user info from id-token
        Map<String, Object> tokenInfo = verifyGoogleIdToken(idToken);
        String email = (String) tokenInfo.get("email");
        String name = (String) tokenInfo.get("name");
        String providerId = (String) tokenInfo.get("sub");

        // Check if user exists
        boolean isFirstLogin = !userRepository.existsByEmail(email);

        // Find or create user
        User user = findOrCreateUser(email, name, providerId);

        List<SimpleGrantedAuthority> authorities = convertRolesToAuthorities(user.getRoles());
        String accessToken = jwtTokenProvider.createToken(user.getEmail(), authorities);
        String refreshToken = UUID.randomUUID().toString();
        updateUserTokens(user, accessToken, refreshToken);

        return buildAuthResponse(accessToken, refreshToken, user, isFirstLogin);
    }

    /**
     * Refreshes the authentication tokens using a valid refresh token.
     *
     * @param refreshToken       the current refresh token
     * @param expiredAccessToken the expired access token
     * @return AuthResponse containing new access token and refresh token
     * @throws InvalidTokenException if the tokens are invalid or expired
     */
    public AuthResponse refreshToken(String refreshToken, String expiredAccessToken) {
        User user = validateAndGetUser(refreshToken, expiredAccessToken);

        List<SimpleGrantedAuthority> authorities = convertRolesToAuthorities(user.getRoles());
        String newAccessToken = jwtTokenProvider.createToken(user.getEmail(), authorities);
        String newRefreshToken = UUID.randomUUID().toString();
        updateUserTokens(user, newAccessToken, newRefreshToken);

        return buildAuthResponse(newAccessToken, newRefreshToken, user, false);
    }

    private Map<String, Object> verifyGoogleIdToken(String idToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Failed to verify ID token. Response code: {}", response.code());
                throw new IOException("Failed to verify ID token");
            }

            assert response.body() != null;
            String responseBody = response.body().string();
            Map<String, Object> tokenInfo = objectMapper.readValue(responseBody, Map.class);

            if (!googleClientId.equals(tokenInfo.get("aud"))) {
                log.error("Invalid audience in token. Expected: {}, Got: {}", googleClientId, tokenInfo.get("aud"));
                throw new IOException("Invalid audience");
            }

            return tokenInfo;
        } catch (IOException e) {
            log.error("Error during Google token verification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private User findOrCreateUser(String email, String name, String providerId) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    log.info("Creating new user for email: {}", email);
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .provider(AuthProvider.GOOGLE)
                            .providerId(providerId)
                            .roles(java.util.List.of("ROLE_USER"))
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private List<SimpleGrantedAuthority> convertRolesToAuthorities(List<String> roles) {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user, boolean isFirstLogin) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isFirstLogin(isFirstLogin)
                .userId(user.getId().toString())
                .build();
    }

    private User validateAndGetUser(String refreshToken, String expiredAccessToken) {
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!expiredAccessToken.equals(user.getLastIssuedAccessToken())) {
            throw new InvalidTokenException("Access token is not the most recently issued token");
        }

        if (user.getRefreshTokenExpireDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token expired");
        }

        return user;
    }

    private void updateUserTokens(User user, String newAccessToken, String newRefreshToken) {
        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpireDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        user.setLastIssuedAccessToken(newAccessToken);
        userRepository.save(user);
    }
}
