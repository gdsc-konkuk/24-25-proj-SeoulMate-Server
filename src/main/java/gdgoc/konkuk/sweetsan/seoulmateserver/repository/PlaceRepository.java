package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Place entity operations. Provides methods to interact with the place data in the MongoDB
 * database.
 */
@Repository
public interface PlaceRepository extends MongoRepository<Place, ObjectId> {

    /**
     * Finds places by multiple IDs. Used when retrieving multiple places from a list of IDs.
     *
     * @param ids list of ObjectIds to search for
     * @return a List of Place objects matching the provided IDs
     */
    List<Place> findByIdIn(List<ObjectId> ids);

    /**
     * Finds places that contain the given name string.
     *
     * @param name the name substring to search for
     * @return a List of Place objects whose names contain the provided string
     */
    List<Place> findByNameContaining(String name);
}
