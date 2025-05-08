package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for making requests to the ML server's chatbot API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for ML-based chatbot interaction")
public class MLChatbotRequest {
    /**
     * User's unique identifier
     */
    @Schema(description = "User's unique identifier", example = "user123")
    private String userId;

    /**
     * List of Google Place IDs that the user has liked
     */
    @Schema(description = "List of Google Place IDs that the user has liked", example = "[\"ChIJN1t_tDeuEmsRUsoyG83frY4\", \"ChIJ7cv00DwsDogRAMDACa2m4K8\"]")
    private List<String> likedPlaceIds;

    /**
     * List of categories preferred by the user
     */
    @Schema(description = "List of categories preferred by the user", example = "[\"cafe\", \"restaurant\", \"museum\"]")
    private List<String> styles;

    /**
     * Google Place ID of the place to get information about
     */
    @Schema(description = "Google Place ID of the place to get information about", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
    private String placeId;
}
