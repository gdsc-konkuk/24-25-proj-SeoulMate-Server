package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User information DTO")
public class UserInfoDto {

    @Schema(description = "User name", example = "John Doe")
    private String name;

    @Schema(description = "Date of birth", example = "1990-01-01")
    private LocalDate birthYear;
}
