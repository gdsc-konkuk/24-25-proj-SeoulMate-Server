package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for making requests to the ML server's place recommendation API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPlaceRecommendationRequest {
    /**
     * User's unique identifier
     */
    private String userId;

    /**
     * List of Google Place IDs that the user has liked
     */
    private List<String> likedPlaceIds;

    /**
     * List of categories preferred by the user
     */
    private List<String> styles;
}
