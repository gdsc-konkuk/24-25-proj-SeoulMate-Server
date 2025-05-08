package gdgoc.konkuk.sweetsan.seoulmateserver.model;

/**
 * Enum defining the types of chat interactions available in the ML server's chatbot API
 */
public enum ChatType {
    /**
     * Fitness and health-related score
     */
    FITNESS_SCORE("fitness-score"),

    /**
     * Detailed information about the place
     */
    DETAIL_INFO("detail-info"),

    /**
     * Free-form conversation
     */
    FREE_CHAT("free-chat"),

    /**
     * Safety and location information
     */
    SAFE_LOCATION("safe-location");

    private final String value;

    ChatType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
