package gdgoc.konkuk.sweetsan.seoulmateserver.service;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLPlaceRecommendationRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLPlaceRecommendationResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceRecommendationResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.ResourceNotFoundException;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.ChatType;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.User;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.MLRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.PlaceRepository;
import gdgoc.konkuk.sweetsan.seoulmateserver.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final MLRepository mlRepository;

    /**
     * Get place recommendations for a user based on their location and preferences.
     *
     * @param email     User's email
     * @param latitude  User's latitude
     * @param longitude User's longitude
     * @return List of recommended places
     */
    public PlaceRecommendationResponse getPlaceRecommendations(String email, double latitude, double longitude) {
        // Get user and their liked places
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        List<String> likedPlaceIds = user.getLikes().stream()
                .map(placeId -> {
                    Place place = placeRepository.findById(placeId)
                            .orElseThrow(() -> new ResourceNotFoundException("Place not found with id: " + placeId));
                    return place.getGooglePlaceId();
                })
                .collect(Collectors.toList());

        // Prepare request for ML server
        MLPlaceRecommendationRequest request = MLPlaceRecommendationRequest.builder()
                .userId(user.getId().toString())
                .likedPlaceIds(likedPlaceIds)
                .styles(user.getPurpose())
                .build();

        // Call ML server for recommendations
        MLPlaceRecommendationResponse mlResponse = mlRepository.getPlaceRecommendations(request);

        if (mlResponse == null || mlResponse.getRecommendations() == null) {
            return PlaceRecommendationResponse.builder().places(List.of()).build();
        }

        // Convert ML response to our response format
        List<PlaceRecommendationResponse.PlaceRecommendation> recommendations = mlResponse.getRecommendations().stream()
                .map(rec -> {
                    Place place = placeRepository.findByGooglePlaceId(rec.getId())
                            .orElse(null);
                    if (place == null) {
                        return null;
                    }
                    return PlaceRecommendationResponse.PlaceRecommendation.builder()
                            .placeId(place.getGooglePlaceId())
                            .description(place.getDescription())
                            .reason(rec.getReason())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PlaceRecommendationResponse.builder()
                .places(recommendations)
                .build();
    }

    /**
     * Get chatbot response for a specific place.
     *
     * @param email    User's email
     * @param placeId  Google Place ID of the place
     * @param chatType Type of chat interaction
     * @return Chatbot's response
     */
    public MLChatbotResponse getChatbotResponse(String email, String placeId, ChatType chatType) {
        // Get user and their liked places
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        List<String> likedPlaceIds = user.getLikes().stream()
                .map(id -> {
                    Place place = placeRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Place not found with id: " + id));
                    return place.getGooglePlaceId();
                })
                .collect(Collectors.toList());

        // Verify that the requested place exists
        Place place = placeRepository.findByGooglePlaceId(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found with Google Place ID: " + placeId));

        // Prepare request for ML server
        MLChatbotRequest request = MLChatbotRequest.builder()
                .userId(user.getId().toString())
                .likedPlaceIds(likedPlaceIds)
                .styles(user.getPurpose())
                .placeId(placeId)
                .build();

        // Call ML server for chatbot response
        return mlRepository.getChatbotResponse(chatType, request);
    }
}
