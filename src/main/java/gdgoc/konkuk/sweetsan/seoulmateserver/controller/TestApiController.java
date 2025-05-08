package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceRecommendationResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test API controller for simulating ML server responses This controller is excluded from Swagger documentation as it's
 * for testing purposes only
 */
@RestController
@RequestMapping("/test-client/api")
public class TestApiController {
    @GetMapping("/recommend")
    public ResponseEntity<PlaceRecommendationResponse> getTestPlaceRecommendations() {
        return ResponseEntity.ok(PlaceRecommendationResponse.builder()
                .places(List.of(
                        PlaceRecommendationResponse.PlaceRecommendation.builder()
                                .placeId("ChIJN1t_tDeuEmsRUsoyG83frY4")
                                .description("A trendy cafe offering special coffee and desserts.")
                                .reason("Matches user's cafe preferences and has high ratings.")
                                .build(),
                        PlaceRecommendationResponse.PlaceRecommendation.builder()
                                .placeId("ChIJ7cv00DwsDogRAMDACa2m4K8")
                                .description("A traditional Korean restaurant serving authentic Korean cuisine.")
                                .reason("Belongs to user's preferred Korean food category and is popular among locals.")
                                .build()))
                .build());
    }

    @GetMapping("/chatbot/{chatType}")
    public ResponseEntity<MLChatbotResponse> getTestChatbotResponse(@PathVariable String chatType) {
        return ResponseEntity.ok(MLChatbotResponse.builder()
                .reply("This cafe is one of the most popular cafes in Seoul. It offers special coffee and desserts, and you can enjoy a relaxing time in a cozy atmosphere.")
                .build());
    }
}
