package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity operations.
 * Provides methods to interact with the user data in the MongoDB database.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Finds a user by their email address.
     * 
     * @param email the email address to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their refresh token.
     * Used during the token refresh process to validate refresh tokens.
     * 
     * @param refreshToken the refresh token to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByRefreshToken(String refreshToken);

    /**
     * Checks if a user with the specified email exists.
     * 
     * @param email the email address to check
     * @return true if a user with the email exists, false otherwise
     */
    boolean existsByEmail(String email);
}
