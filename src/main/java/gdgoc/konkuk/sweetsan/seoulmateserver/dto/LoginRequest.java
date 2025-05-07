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
@Schema(description = "Login request containing Google OAuth2 authorization code")
public class LoginRequest {

    @Schema(description = "Google OAuth2 authorization code received after user consent",
            example = "4/0AY0e-g6_kS7KbxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxIpw")
    @NotBlank(message = "Authorization code is required")
    private String authorizationCode;
}
