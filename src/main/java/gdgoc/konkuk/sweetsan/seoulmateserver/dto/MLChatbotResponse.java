package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for responses from the ML server's chatbot API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from ML-based chatbot")
public class MLChatbotResponse {
    /**
     * Chatbot's response message
     */
    @Schema(description = "Chatbot's response message", example = "This cafe is one of the most popular cafes in Seoul. It offers special coffee and desserts, and you can enjoy a relaxing time in a cozy atmosphere.")
    private String reply;
}
