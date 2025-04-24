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
@Schema(description = "User information retrieved from Google API")
public class GoogleUserInfo {

    @Schema(description = "Google user ID", example = "123456789012345678901")
    private String id;

    @Schema(description = "User's email address", example = "user@example.com")
    private String email;

    @Schema(description = "Email verification status", example = "true")
    @JsonProperty("verified_email")
    private boolean verifiedEmail;

    @Schema(description = "User's full name", example = "John Doe")
    private String name;

    @Schema(description = "User's first name", example = "John")
    @JsonProperty("given_name")
    private String givenName;

    @Schema(description = "User's last name", example = "Doe")
    @JsonProperty("family_name")
    private String familyName;

    @Schema(description = "URL to user's profile picture", example = "https://lh3.googleusercontent.com/a/...")
    private String picture;

    @Schema(description = "User's locale setting", example = "en")
    private String locale;
}
