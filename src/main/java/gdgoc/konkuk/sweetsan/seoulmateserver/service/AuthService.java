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
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;

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
        // 1. Fetch Google's public keys
        Request jwkRequest = new Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/certs")
                .get()
                .build();

        try (Response jwkResponse = okHttpClient.newCall(jwkRequest).execute()) {
            if (!jwkResponse.isSuccessful()) {
                log.error("Failed to fetch Google public keys. Response code: {}", jwkResponse.code());
                throw new IOException("Failed to fetch Google public keys");
            }

            assert jwkResponse.body() != null;
            String jwkSetJson = jwkResponse.body().string();
            Map<String, Object> jwkSet = objectMapper.readValue(jwkSetJson, Map.class);
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwkSet.get("keys");

            // 2. Initialize JWT parser
            JwtParserBuilder parserBuilder = Jwts.parserBuilder();

            // 3. Extract kid(Key ID) from token header
            String[] parts = idToken.split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");

            // 4. Find matching public key for the kid
            Map<String, Object> key = keys.stream()
                    .filter(k -> kid.equals(k.get("kid")))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No matching key found for kid: " + kid));

            // 5. Create public key
            String modulus = (String) key.get("n");
            String exponent = (String) key.get("e");
            RSAPublicKey publicKey = createRSAPublicKey(modulus, exponent);

            // 6. Verify JWT signature and parse claims
            Claims claims = parserBuilder
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();

            // 7. Additional validations
            if (!"accounts.google.com".equals(claims.getIssuer())) {
                throw new IOException("Invalid issuer");
            }

            if (!googleClientId.equals(claims.getAudience())) {
                throw new IOException("Invalid audience");
            }

            if (claims.getExpiration().before(new Date())) {
                throw new IOException("Token expired");
            }

            return claims;
        } catch (Exception e) {
            log.error("Error during Google token verification: {}", e.getMessage(), e);
            throw new IOException("Failed to verify ID token", e);
        }
    }

    private RSAPublicKey createRSAPublicKey(String modulus, String exponent) throws IOException {
        try {
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(spec);
        } catch (Exception ex) {
            throw new IOException("Error creating RSA public key", ex);
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

    /**
     * Deletes the currently logged-in user.
     *
     * @param email email of the user to delete
     * @throws IllegalArgumentException if user is not found
     */
    public void deleteUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userRepository.delete(user);
        log.info("User deleted successfully: {}", email);
    }
}
