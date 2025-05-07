package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 * Service for managing place information. Provides methods for creating, retrieving, updating, and searching places.
 */
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    /**
     * Searches for places by name.
     *
     * @param name the search term
     * @return List of PlaceDto objects matching the search
     */
    public List<PlaceDto> searchPlacesByName(String name) {
        List<Place> places = placeRepository.findByNameContaining(name);
        return places.stream()
                .map(PlaceDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Records a user's preference (like/dislike) for a place.
     *
     * @param placeId the place ID as string
     * @param email   the user's email
     * @param like    true if the user likes the place, false if disliked
     * @return PlaceDto containing the place information
     * @throws ResourceNotFoundException if the place or user is not found or if placeId format is invalid
     */
    public PlaceDto recordPlacePreference(String placeId, String email, Boolean like) {
        try {
            ObjectId objectId = new ObjectId(placeId);
            // Verify place exists
            Place place = placeRepository.findById(objectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Place not found with id: " + placeId));

            // Update user's interaction with the place
            updateUserPlaceInteraction(email, objectId, true, like);

            // Return the place DTO
            return PlaceDto.fromEntity(place);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Invalid place ID format: " + placeId);
        }
    }

    /**
     * Updates a user's interaction with a place.
     *
     * @param email      the user's email
     * @param placeId    the place ID as ObjectId
     * @param visited    whether the user has visited/searched for this place
     * @param preference the user's preference for this place (liked/disliked)
     */
    private void updateUserPlaceInteraction(String email, ObjectId placeId, boolean visited, Boolean preference) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        Map<ObjectId, User.PlaceInteraction> interactions = user.getPlaceInteractions();

        // Get existing interaction or create new one
        User.PlaceInteraction interaction = interactions.getOrDefault(placeId,
                User.PlaceInteraction.builder().visited(false).preference(null).build());

        // Check if anything needs to be updated
        boolean needsUpdate = false;

        if (visited && !interaction.isVisited()) {
            interaction.setVisited(true);
            needsUpdate = true;
        }

        if (preference != null && !preference.equals(interaction.getPreference())) {
            interaction.setPreference(preference);
            needsUpdate = true;
        }

        // Only save if there were changes
        if (needsUpdate) {
            // Save updated interaction
            interactions.put(placeId, interaction);
            user.setPlaceInteractions(interactions);
            userRepository.save(user);
        }
    }
}
