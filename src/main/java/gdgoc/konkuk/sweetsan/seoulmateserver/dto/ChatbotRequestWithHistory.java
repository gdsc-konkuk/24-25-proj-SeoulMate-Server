package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO for chatbot requests with conversation history and input
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for chatbot interaction with history and input")
public class ChatbotRequestWithHistory {

    /**
     * Conversation history
     */
    @Schema(description = "Conversation history", nullable = true)
    private List<HistoryItem> history;

    /**
     * User input
     */
    @Schema(description = "User input", nullable = true)
    private String input;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "History item")
    public static class HistoryItem {
        @Schema(description = "Role of the message", example = "human", nullable = true)
        private String role;

        @Schema(description = "Content of the message", example = "Hello!", nullable = true)
        private String content;
    }
}
