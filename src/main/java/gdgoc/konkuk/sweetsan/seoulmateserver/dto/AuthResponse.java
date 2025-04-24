package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing tokens and user information")
public class AuthResponse {

    @Schema(description = "JWT access token for authentication", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Refresh token to obtain new access tokens", example = "a1b2c3d4-e5f6-g7h8-i9j0-k1l2m3n4o5p6")
    private String refreshToken;

    @Schema(description = "Flag indicating if this is the user's first login", example = "true")
    private Boolean isFirstLogin;

    @Schema(description = "User identifier", example = "user123")
    private String userId;
}
