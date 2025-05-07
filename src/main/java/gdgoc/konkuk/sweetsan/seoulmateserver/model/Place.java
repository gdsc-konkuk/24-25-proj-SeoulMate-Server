package gdgoc.konkuk.sweetsan.seoulmateserver.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Place model representing a travel destination in the application. This class is mapped to the 'places' collection in
 * MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "places")
public class Place {

    /**
     * Unique identifier for the place
     */
    @Id
    private ObjectId id;

    /**
     * Geographic coordinates (latitude, longitude) of the place
     */
    private Coordinate coordinate;

    /**
     * Google Place ID for integration with Google Places API
     */
    private String googlePlaceId;

    /**
     * Name of the travel destination
     */
    private String name;

    /**
     * Detailed description of the travel destination (approximately 500 characters)
     */
    private String description;

    /**
     * Timestamp when the place was created
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Timestamp when the place was last updated
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Checks if the place has a valid name (non-null and non-empty).
     *
     * @return true if the name is valid
     */
    public boolean hasValidName() {
        return name != null && !name.isEmpty();
    }

    /**
     * Checks if the place has a valid Google Place ID (non-null and non-empty).
     *
     * @return true if the Google Place ID is valid
     */
    public boolean hasValidGooglePlaceId() {
        return googlePlaceId != null && !googlePlaceId.isEmpty();
    }

    /**
     * Checks if the place has a valid description (non-null, non-empty, and meaningful length). A meaningful
     * description is considered to be at least 20 characters.
     *
     * @return true if the description is valid
     */
    public boolean hasValidDescription() {
        return description != null && !description.isEmpty() && description.length() >= 20;
    }

    /**
     * Checks if the place has valid coordinates (non-null and both latitude and longitude present).
     *
     * @return true if the coordinates are valid
     */
    public boolean hasValidCoordinates() {
        return coordinate != null &&
                coordinate.getLatitude() != null &&
                coordinate.getLongitude() != null;
    }

    /**
     * Checks if the place is completely valid with all required fields. A completely valid place must have a valid
     * name, Google Place ID, coordinates, and description.
     *
     * @return true if the place has all required fields
     */
    public boolean isValid() {
        return hasValidName() && hasValidGooglePlaceId() && hasValidCoordinates() && hasValidDescription();
    }

    /**
     * Inner class representing geographic coordinates
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        /**
         * Latitude of the location
         */
        private Double latitude;

        /**
         * Longitude of the location
         */
        private Double longitude;
    }
}
