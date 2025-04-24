package gdgoc.konkuk.sweetsan.seoulmateserver.model;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * User model representing a registered user in the application.
 * This class is mapped to the 'users' collection in MongoDB.
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
}
