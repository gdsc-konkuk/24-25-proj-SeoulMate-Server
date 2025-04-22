package gdgoc.konkuk.sweetsan.seoulmate.repository;

import gdgoc.konkuk.sweetsan.seoulmate.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByRefreshToken(String refreshToken);
    
    boolean existsByEmail(String email);
}
