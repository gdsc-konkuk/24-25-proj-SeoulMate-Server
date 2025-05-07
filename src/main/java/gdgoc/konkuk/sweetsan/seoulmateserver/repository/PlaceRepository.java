package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Place entity operations. Provides methods to interact with the place data in the MongoDB
 * database.
 */
@Repository
public interface PlaceRepository extends MongoRepository<Place, ObjectId> {

    /**
     * Finds a place by Google Place ID.
     *
     * @param googlePlaceId the Google Place ID to search for
     * @return an Optional containing the Place object if found
     */
    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    /**
     * Finds all places by Google Place ID.
     *
     * @param googlePlaceId the Google Place ID to search for
     * @return a List of Place objects with the given Google Place ID
     */
    List<Place> findAllByGooglePlaceId(String googlePlaceId);
}
