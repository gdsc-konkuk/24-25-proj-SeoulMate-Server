package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceHistoryResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 * Service for managing user information. Provides methods for retrieving and
 * updating user details.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;

    /**
     * Retrieves user information.
     *
     * @param email the user's email
     * @return UserInfoDto containing the user's information
     * @throws ResourceNotFoundException if the user is not found
     */
    public UserInfoDto getUserInfo(String email) {
        User user = getUserByEmail(email);
        return UserInfoDto.builder()
                .name(user.getName())
                .birthYear(user.getBirthYear())
                .companion(user.getCompanion())
                .purpose(user.getPurpose())
                .build();
    }

    /**
     * Updates a user's information.
     *
     * @param email       the user's email
     * @param userInfoDto the updated user information
     * @return UserInfoDto containing the updated user information
     * @throws ResourceNotFoundException if the user is not found
     */
    public UserInfoDto updateUserInfo(String email, UserInfoDto userInfoDto) {
        User user = getUserByEmail(email);

        if (userInfoDto.getName() != null) {
            user.setName(userInfoDto.getName());
        }
        if (userInfoDto.getBirthYear() != null) {
            user.setBirthYear(userInfoDto.getBirthYear());
        }
        if (userInfoDto.getCompanion() != null) {
            user.setCompanion(userInfoDto.getCompanion());
        }
        if (userInfoDto.getPurpose() != null) {
            user.setPurpose(userInfoDto.getPurpose());
        }

        User savedUser = userRepository.save(user);

        return UserInfoDto.builder()
                .name(savedUser.getName())
                .birthYear(savedUser.getBirthYear())
                .companion(savedUser.getCompanion())
                .purpose(savedUser.getPurpose())
                .build();
    }

    /**
     * Retrieves a user by their email address.
     *
     * @param email the user's email
     * @return User entity
     * @throws ResourceNotFoundException if the user is not found
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /**
     * Retrieves a user's place history or liked places.
     *
     * @param email the user's email
     * @param like  if true, returns liked places; if false, returns disliked
     *              places; if null, returns search history
     * @return PlaceHistoryResponse containing the list of place DTOs
     * @throws ResourceNotFoundException if the user is not found
     */
    public PlaceHistoryResponse getUserPlaceHistories(String email, Boolean like) {
        User user = getUserByEmail(email);
        Map<ObjectId, User.PlaceInteraction> interactions = user.getPlaceInteractions();
        List<ObjectId> objectIds;

        if (like == null) {
            // Return search/visit history (all places the user has interacted with)
            objectIds = new ArrayList<>(interactions.keySet());
        } else {
            // Return liked or disliked places
            objectIds = interactions.entrySet().stream()
                    .filter(entry -> entry.getValue().getPreference() != null &&
                            entry.getValue().getPreference().equals(like))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        // Skip DB call if there are no places to fetch
        if (objectIds.isEmpty()) {
            return PlaceHistoryResponse.builder().places(List.of()).build();
        }

        // Fetch places from repository
        List<Place> places = placeRepository.findByIdIn(objectIds);

        // Convert to DTOs
        List<PlaceDto> placeDtos = places.stream()
                .map(PlaceDto::fromEntity)
                .collect(Collectors.toList());

        return PlaceHistoryResponse.builder()
                .places(placeDtos)
                .build();
    }
}
