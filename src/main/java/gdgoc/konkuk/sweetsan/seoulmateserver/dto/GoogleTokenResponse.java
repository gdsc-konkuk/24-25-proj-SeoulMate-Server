package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from Google OAuth2 token endpoint")
public class GoogleTokenResponse {

    @Schema(description = "Google OAuth2 access token", example = "ya29.a0AVvZVsrf1...")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "Token expiration time in seconds", example = "3599")
    @JsonProperty("expires_in")
    private Integer expiresIn;

    @Schema(description = "Token type (usually Bearer)", example = "Bearer")
    @JsonProperty("token_type")
    private String tokenType;

    @Schema(description = "OAuth2 scopes granted", example = "email profile openid")
    @JsonProperty("scope")
    private String scope;

    @Schema(description = "JWT ID token containing user information", example = "eyJhbGciOiJSUzI1...")
    @JsonProperty("id_token")
    private String idToken;
}
