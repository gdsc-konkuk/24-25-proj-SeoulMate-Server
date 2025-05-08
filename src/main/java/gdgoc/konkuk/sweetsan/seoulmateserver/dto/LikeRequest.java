package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Like request DTO")
public class LikeRequest {

    @Schema(description = "Google Place ID of the place to like/unlike", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
    private String placeId;

    @Schema(description = "True to add to likes, false to remove from likes", example = "true")
    private boolean like;
}
