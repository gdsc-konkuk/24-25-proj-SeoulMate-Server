package gdgoc.konkuk.sweetsan.seoulmate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
    
    @NotBlank(message = "Access token is required")
    private String accessToken;
}
