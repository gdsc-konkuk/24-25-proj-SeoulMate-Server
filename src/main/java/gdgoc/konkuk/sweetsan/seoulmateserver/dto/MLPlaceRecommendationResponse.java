package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for responses from the ML server's place recommendation API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPlaceRecommendationResponse {
    /**
     * List of recommended places with detailed information
     */
    private List<Recommendation> recommendations;

    /**
     * Detailed information about a recommended place
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        /**
         * Place ID of the recommended place
         */
        private String id;

        /**
         * List of categories for the place (comma-separated string)
         */
        private String category;

        /**
         * Reason for recommending this place
         */
        private String reason;
    }
}
