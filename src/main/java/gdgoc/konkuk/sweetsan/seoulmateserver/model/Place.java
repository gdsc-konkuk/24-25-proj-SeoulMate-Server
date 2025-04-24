package gdgoc.konkuk.sweetsan.seoulmateserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Place model representing a travel destination in the application.
 * This class is mapped to the 'places' collection in MongoDB.
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
