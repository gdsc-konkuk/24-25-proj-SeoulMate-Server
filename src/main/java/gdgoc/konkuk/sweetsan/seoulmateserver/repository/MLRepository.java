package gdgoc.konkuk.sweetsan.seoulmateserver.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLPlaceRecommendationRequest;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLPlaceRecommendationResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.ChatType;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Repository for communicating with the ML model serving server. Handles all interactions with the ML server's APIs.
 */
@Slf4j
@Repository
public class MLRepository {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper mlObjectMapper;

    public MLRepository(
            @Value("${ml.server.base-url}") String baseUrl,
            @Value("${ml.server.timeout-seconds}") int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .readTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .build();
        this.mlObjectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Requests place recommendations from the ML server
     *
     * @param request Recommendation request data
     * @return Recommended places
     * @throws RuntimeException if communication with ML server fails
     */
    public MLPlaceRecommendationResponse getPlaceRecommendations(MLPlaceRecommendationRequest request) {
        return executeRequest("/recommend", request, MLPlaceRecommendationResponse.class);
    }

    /**
     * Requests information about a place from the ML server's chatbot
     *
     * @param chatType Type of chat interaction
     * @param request  Chatbot request data
     * @return Chatbot's response
     * @throws RuntimeException if communication with ML server fails
     */
    public MLChatbotResponse getChatbotResponse(ChatType chatType, MLChatbotRequest request) {
        return executeRequest("/chatbot/" + chatType.getValue(), request, MLChatbotResponse.class);
    }

    /**
     * Executes an HTTP request to the ML server
     *
     * @param endpoint     API endpoint
     * @param request      Request data
     * @param responseType Type of response expected
     * @return Response from the ML server
     * @throws RuntimeException if communication with ML server fails
     */
    private <T> T executeRequest(String endpoint, Object request, Class<T> responseType) {
        try {
            String requestBody = mlObjectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(requestBody, JSON);
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    log.error("ML server request failed. Endpoint: {}, Status code: {}", endpoint, response.code());
                    throw new RuntimeException("ML server request failed");
                }

                assert response.body() != null;
                String responseBody = response.body().string();
                return mlObjectMapper.readValue(responseBody, responseType);
            }
        } catch (IOException e) {
            log.error("Error communicating with ML server: {}", e.getMessage());
            throw new RuntimeException("ML server communication failed", e);
        }
    }
}
