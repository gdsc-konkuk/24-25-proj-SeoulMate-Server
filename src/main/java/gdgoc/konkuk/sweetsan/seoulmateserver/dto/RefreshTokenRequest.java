package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to refresh an expired access token")
public class RefreshTokenRequest {

    @Schema(description = "Refresh token to obtain new access token",
            example = "a1b2c3d4-e5f6-g7h8-i9j0-k1l2m3n4o5p6")
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @Schema(description = "Current (expired) access token",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @NotBlank(message = "Access token is required")
    private String accessToken;
}
