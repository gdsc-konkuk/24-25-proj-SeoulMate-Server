package gdgoc.konkuk.sweetsan.seoulmateserver.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * User model representing a registered user in the application. This class is mapped to the 'users' collection in
 * MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    /**
     * Unique identifier for the user
     */
    @Id
    private ObjectId id;

    /**
     * User's email address, must be unique
     */
    @Indexed(unique = true)
    private String email;

    /**
     * User's display name
     */
    private String name;

    /**
     * User's date of birth
     */
    private LocalDate birthYear;

    /**
     * User's companion for travel
     */
    private String companion;

    /**
     * User's travel purposes
     */
    private List<String> purpose;

    /**
     * Authentication provider (e.g., GOOGLE)
     */
    @Builder.Default
    private AuthProvider provider = AuthProvider.GOOGLE;

    /**
     * Unique identifier from the authentication provider
     */
    private String providerId;

    /**
     * Current refresh token for JWT authentication
     */
    private String refreshToken;

    /**
     * Expiration date for the current refresh token
     */
    private LocalDateTime refreshTokenExpireDate;

    /**
     * Last issued access token
     */
    private String lastIssuedAccessToken;

    /**
     * User roles for authorization
     */
    @Builder.Default
    private List<String> roles = List.of("ROLE_USER");

    /**
     * Timestamp when the user was created
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user was last updated
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Map of place interactions with user Key: Place ID (ObjectId) Value: PlaceInteraction object containing visited
     * status and like status
     */
    @Builder.Default
    private Map<ObjectId, PlaceInteraction> placeInteractions = new HashMap<>();

    /**
     * Inner class representing a user's interaction with a place
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceInteraction {
        /**
         * Whether the user has visited/searched for this place
         */
        private boolean visited;

        /**
         * User's preference for this place (true: liked, false: disliked, null: no preference)
         */
        private Boolean preference;
    }
}
