package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User's liked places response DTO")
public class LikesResponse {

    @Schema(description = "List of Google Place IDs that the user has liked", example = "[\"ChIJN1t_tDeuEmsRUsoyG83frY4\", \"ChIJ7cv00DwsDogRAMDACa2m4K8\", \"ChIJ2eUgeAK6j4ARVGe8I_EWjZc\"]")
    private List<String> placeIds;
}
