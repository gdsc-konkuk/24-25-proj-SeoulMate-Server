package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing user information. Provides methods for retrieving and updating user details.
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
     * Retrieves a list of Google Place IDs that the user has liked.
     *
     * @param email the user's email
     * @return List of Google Place IDs
     * @throws ResourceNotFoundException if the user is not found
     */
    public List<String> getUserLikes(String email) {
        User user = getUserByEmail(email);
        return user.getLikes().stream()
                .map(placeId -> {
                    Place place = placeRepository.findById(placeId)
                            .orElseThrow(() -> new ResourceNotFoundException("Place not found with id: " + placeId));
                    return place.getGooglePlaceId();
                })
                .collect(Collectors.toList());
    }

    /**
     * Updates a user's like status for a place.
     *
     * @param email         the user's email
     * @param googlePlaceId the Google Place ID
     * @param like          true to add to likes, false to remove from likes
     * @throws ResourceNotFoundException if the user or place is not found
     */
    public void updateUserLike(String email, String googlePlaceId, boolean like) {
        User user = getUserByEmail(email);
        Place place = placeRepository.findByGooglePlaceId(googlePlaceId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Place not found with Google Place ID: " + googlePlaceId));

        if (like) {
            user.getLikes().add(place.getId());
        } else {
            user.getLikes().remove(place.getId());
        }

        userRepository.save(user);
    }
}
