package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Authorization code is required")
    private String authorizationCode;
}
