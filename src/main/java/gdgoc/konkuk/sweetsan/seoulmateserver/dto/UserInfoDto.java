package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Schema(description = "Travel companion", example = "family")
    private String companion;

    @Schema(description = "Travel purposes", example = "[\"sightseeing\", \"shopping\"]")
    private List<String> purpose;
}
