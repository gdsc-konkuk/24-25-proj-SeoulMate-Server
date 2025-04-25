package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for Google Places API operations. Used to find Google Place IDs for tourist places.
 */
@Component
public class GooglePlaceUtil {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlaceUtil.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.maps.api.key:}")
    private String googleApiKey;

    /**
     * 장소 이름과 좌표를 기반으로 Google Place ID를 검색
     *
     * @param name  장소 이름
     * @param place 장소 객체 (좌표 정보 포함)
     * @return 해당 장소의 Google Place ID 또는 null
     */
    public String findGooglePlaceId(String name, Place place) {
        // API 키가 설정되지 않은 경우
        if (googleApiKey == null || googleApiKey.isEmpty() || googleApiKey.equals("${google.maps.api.key}")) {
            logger.warn("Google API key is not configured. Skipping Google Place ID lookup.");
            return null;
        }

        try {
            // 서울 위치 특화 검색을 위한 키워드 최적화
            String searchQuery = name;
            if (!name.contains("서울") && !name.contains("Seoul")) {
                searchQuery = name + " 서울";
            }

            // 장소 좌표가 있으면 좌표 기반 검색, 없으면 이름만으로 검색
            String requestUrl;
            if (place.getCoordinate() != null &&
                    place.getCoordinate().getLatitude() != null &&
                    place.getCoordinate().getLongitude() != null) {

                requestUrl = String.format(
                        "https://maps.googleapis.com/maps/api/place/findplacefromtext/json" +
                                "?input=%s" +
                                "&inputtype=textquery" +
                                "&locationbias=point:%f,%f" +
                                "&fields=place_id,name,formatted_address" +
                                "&language=ko" +
                                "&key=%s",
                        URLEncoder.encode(searchQuery, StandardCharsets.UTF_8),
                        place.getCoordinate().getLatitude(),
                        place.getCoordinate().getLongitude(),
                        googleApiKey
                );
            } else {
                requestUrl = String.format(
                        "https://maps.googleapis.com/maps/api/place/findplacefromtext/json" +
                                "?input=%s" +
                                "&inputtype=textquery" +
                                "&locationbias=circle:50000@37.5665,126.9780" + // 서울 중심 기준 50km 반경
                                "&fields=place_id,name,formatted_address" +
                                "&language=ko" +
                                "&key=%s",
                        URLEncoder.encode(searchQuery, StandardCharsets.UTF_8),
                        googleApiKey
                );
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                JsonNode root = objectMapper.readTree(responseBody);

                if (root.has("candidates") && root.get("candidates").isArray() &&
                        !root.get("candidates").isEmpty()) {

                    String placeId = root.get("candidates").get(0).get("place_id").asText();

                    // 첫 번째 결과가 얼마나 일치하는지 확인
                    if (root.get("candidates").get(0).has("name")) {
                        String returnedName = root.get("candidates").get(0).get("name").asText();

                        // 일치도 검사 (이름이 포함 관계인지)
                        boolean nameMatch = returnedName.contains(name) || name.contains(returnedName);

                        if (!nameMatch) {
                            logger.warn("Returned place name '{}' doesn't match query '{}'", returnedName, name);
                            // 그래도 결과는 반환 (검증은 애플리케이션에서 진행)
                        }
                    }

                    logger.info("Found Google Place ID for {}: {}", name, placeId);
                    return placeId;
                } else {
                    logger.warn("No candidates found for {}", name);
                }
            } else {
                logger.error("Error calling Google Places API: {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error looking up Google Place ID for {}", name, e);
            Thread.currentThread().interrupt();
        }

        return null;
    }
}
