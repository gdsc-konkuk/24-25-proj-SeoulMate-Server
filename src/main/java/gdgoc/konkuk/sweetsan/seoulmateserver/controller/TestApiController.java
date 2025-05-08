package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test API controller for simulating ML server responses This controller is excluded from Swagger documentation as it's
 * for testing purposes only
 */
@Hidden
@RestController
@RequestMapping("/test-client/api")
public class TestApiController {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/recommend")
    public ResponseEntity<String> getTestPlaceRecommendations() {
        try {
            Map<String, List<Map<String, String>>> response = Map.of(
                    "recommendations", List.of(
                            Map.of(
                                    "id", "ChIJqWqOqFeifDURpYJ5LnxX-Fw",
                                    "category", "cafe",
                                    "reason",
                                    "Matches user's cafe preferences and has high ratings."),
                            Map.of(
                                    "id", "ChIJu5Gg2hWkfDURl7NN7FpFnis",
                                    "category", "restaurant",
                                    "reason",
                                    "Belongs to user's preferred Korean food category and is popular among locals.")));
            return ResponseEntity.ok(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error generating test response");
        }
    }

    @PostMapping("/chatbot/{chatType}")
    public ResponseEntity<String> getTestChatbotResponse(@PathVariable String chatType) {
        try {
            Map<String, String> response = Map.of(
                    "reply",
                    "This cafe is one of the most popular cafes in Seoul. It offers special coffee and desserts, and you can enjoy a relaxing time in a cozy atmosphere.");
            return ResponseEntity.ok(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error generating test response");
        }
    }
}
