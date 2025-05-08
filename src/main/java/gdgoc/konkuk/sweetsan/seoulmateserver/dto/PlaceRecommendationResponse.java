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
@Schema(description = "Response containing recommended places")
public class PlaceRecommendationResponse {
    @Schema(description = "List of recommended places", example = """
            [
                {
                    "placeId": "ChIJN1t_tDeuEmsRUsoyG83frY4",
                    "description": "A trendy cafe offering special coffee and desserts.",
                    "reason": "Matches user's cafe preferences and has high ratings."
                },
                {
                    "placeId": "ChIJ7cv00DwsDogRAMDACa2m4K8",
                    "description": "A traditional Korean restaurant serving authentic Korean cuisine.",
                    "reason": "Belongs to user's preferred Korean food category and is popular among locals."
                }
            ]
            """)
    private List<PlaceRecommendation> places;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Information about a recommended place")
    public static class PlaceRecommendation {
        @Schema(description = "Google Place ID of the recommended place", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
        private String placeId;

        @Schema(description = "Description of the place", example = "A trendy cafe offering special coffee and desserts.")
        private String description;

        @Schema(description = "Reason for recommending this place", example = "Matches user's cafe preferences and has high ratings.")
        private String reason;
    }
}
