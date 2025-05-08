package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

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
public class MLChatbotResponse {
    /**
     * Chatbot's response message
     */
    private String reply;
}
