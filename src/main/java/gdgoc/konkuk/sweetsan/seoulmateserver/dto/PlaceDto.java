package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Place information DTO")
public class PlaceDto {

    @Schema(description = "Place ID", example = "60a6e8e77c213e4cd0955f3c")
    private String id;

    @Schema(description = "Place name", example = "Gyeongbokgung Palace")
    private String name;

    @Schema(description = "Place description", example = "Gyeongbokgung Palace is the largest of the Five Grand Palaces built during the Joseon Dynasty.")
    private String description;

    @Schema(description = "Google Place ID", example = "ChIJyd1GTC2ifDUR27GuqZsZ1rA")
    private String googlePlaceId;

    @Schema(description = "Latitude coordinate", example = "37.5796")
    private Double latitude;

    @Schema(description = "Longitude coordinate", example = "126.9770")
    private Double longitude;

    /**
     * Creates a PlaceDto from a Place entity
     *
     * @param place the Place entity
     * @return a PlaceDto object
     */
    public static PlaceDto fromEntity(Place place) {
        if (place == null) {
            return null;
        }

        return PlaceDto.builder()
                .id(place.getId() != null ? place.getId().toString() : null)
                .name(place.getName())
                .description(place.getDescription())
                .googlePlaceId(place.getGooglePlaceId())
                .latitude(place.getCoordinate() != null ? place.getCoordinate().getLatitude() : null)
                .longitude(place.getCoordinate() != null ? place.getCoordinate().getLongitude() : null)
                .build();
    }

    /**
     * Converts this DTO to a Place entity
     *
     * @return a Place object
     */
    public Place toEntity() {
        ObjectId objectId = null;
        if (id != null && !id.isEmpty()) {
            try {
                objectId = new ObjectId(id);
            } catch (IllegalArgumentException e) {
                // 유효하지 않은 ID 형식이라면 null로 처리
                objectId = null;
            }
        }

        Place.Coordinate coordinate = null;
        if (latitude != null || longitude != null) {
            coordinate = Place.Coordinate.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
        }

        return Place.builder()
                .id(objectId)
                .name(name)
                .description(description)
                .googlePlaceId(googlePlaceId)
                .coordinate(coordinate)
                .build();
    }
}
