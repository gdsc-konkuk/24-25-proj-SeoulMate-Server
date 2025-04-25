package gdgoc.konkuk.sweetsan.seoulmateserver.scraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of PlaceScraper that scrapes tourist place data from Visit Seoul website. Enhanced version to collect
 * maximum number of places possible.
 */
@Component
public class VisitSeoulScraper implements PlaceScraper {

    private static final Logger logger = LoggerFactory.getLogger(VisitSeoulScraper.class);
    private static final String BASE_URL = "https://korean.visitseoul.net";

    // 카테고리별 URL (더 많은 관광지 수집을 위해)
    private static final Map<String, String> CATEGORIES = Map.of(
            "전체", "/attractions",
            "랜드마크", "/attractions?categoryGroup=랜드마크",
            "고궁", "/attractions?categoryGroup=고궁",
            "역사적 장소", "/attractions?categoryGroup=역사적 장소",
            "오래가게", "/attractions?categoryGroup=오래가게",
            "미술관&박물관", "/attractions?categoryGroup=미술관&박물관"
    );

    // 최대 페이지 수 제한 없음 (모든 페이지 수집)
    private static final int MAX_PAGES_PER_CATEGORY = Integer.MAX_VALUE;

    @Override
    public CompletableFuture<List<Place>> scrapeAsync() {
        return CompletableFuture.supplyAsync(this::scrape);
    }

    @Override
    public List<Place> scrape() {
        // 장소 ID로 중복을 제거하기 위한 맵
        Map<String, Place> uniquePlaces = new HashMap<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(180000)); // 시간 초과 늘림

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36")
                    .setViewportSize(1920, 1080));

            // 모든 카테고리에 대해 스크래핑
            for (Map.Entry<String, String> category : CATEGORIES.entrySet()) {
                String categoryName = category.getKey();
                String categoryUrl = BASE_URL + category.getValue();

                logger.info("Scraping category: {}", categoryName);

                Page page = context.newPage();

                try (page) {
                    page.setDefaultTimeout(60000); // 타임아웃 늘림
                    logger.info("Navigating to category URL: {}", categoryUrl);
                    page.navigate(categoryUrl);

                    // 쿠키 동의 배너 처리 (있는 경우)
                    try {
                        if (page.isVisible("text=모두 허용")) {
                            page.click("text=모두 허용");
                            logger.info("Accepted cookies");
                        }
                    } catch (Exception e) {
                        logger.warn("No cookie consent banner found or unable to click it");
                    }

                    // 메인 콘텐츠 로드 대기
                    page.waitForSelector("main list", new Page.WaitForSelectorOptions().setTimeout(30000));

                    // 페이지 수 확인 (마지막 페이지 찾기)
                    String lastPageText = "1";
                    try {
                        ElementHandle lastPageElement = page.querySelector("link:has-text('마지막 페이지')");
                        if (lastPageElement != null) {
                            String href = lastPageElement.getAttribute("href");
                            if (href != null && href.contains("curPage=")) {
                                lastPageText = href.substring(href.indexOf("curPage=") + 8);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error finding last page, will assume default", e);
                    }

                    int lastPage;
                    try {
                        lastPage = Integer.parseInt(lastPageText);
                    } catch (NumberFormatException e) {
                        lastPage = 1;
                    }

                    // 최대 페이지 제한 적용
                    int pagesToScrape = lastPage;

                    logger.info("Category {} has {} pages, will scrape up to {}",
                            categoryName, lastPage, pagesToScrape);

                    // 각 페이지 탐색
                    for (int pageNum = 1; pageNum <= pagesToScrape; pageNum++) {
                        try {
                            // 특정 페이지로 이동
                            if (pageNum > 1) {
                                logger.info("Navigating to page {} of {}", pageNum, pagesToScrape);
                                String pageUrl =
                                        categoryUrl + (categoryUrl.contains("?") ? "&" : "?") + "curPage=" + pageNum;
                                page.navigate(pageUrl);
                                page.waitForLoadState(LoadState.NETWORKIDLE);
                            }

                            // 페이지 내 관광지 목록 가져오기
                            List<ElementHandle> placeElements = page.querySelectorAll("main list > listitem");

                            logger.info("Found {} tourist places on page {} of category {}",
                                    placeElements.size(), pageNum, categoryName);

                            for (int i = 0; i < placeElements.size(); i++) {
                                ElementHandle element = placeElements.get(i);

                                // 관광지 링크 요소 추출
                                ElementHandle linkElement = element.querySelector("link");
                                if (linkElement == null) {
                                    logger.warn("No link element found for place at index {}, skipping", i);
                                    continue;
                                }

                                // 링크에서 URL 가져오기
                                String detailUrl = linkElement.getAttribute("href");
                                if (detailUrl == null || detailUrl.isEmpty()) {
                                    logger.warn("Empty detail URL for place at index {}, skipping", i);
                                    continue;
                                }

                                // 중복 체크를 위한 ID 추출
                                String placeId = extractPlaceIdFromUrl(detailUrl);
                                if (placeId == null || placeId.isEmpty()) {
                                    logger.warn("Could not extract ID from URL: {}, will use full URL as ID",
                                            detailUrl);
                                    placeId = detailUrl;
                                }

                                // 이미 수집한 장소인지 확인
                                if (uniquePlaces.containsKey(placeId)) {
                                    logger.info("Skipping duplicate place with ID: {}", placeId);
                                    continue;
                                }

                                // 링크 URL 완성
                                if (!detailUrl.startsWith("http")) {
                                    detailUrl = BASE_URL + detailUrl;
                                }

                                // 관광지 이름 추출
                                String linkText = linkElement.textContent();
                                // 첫 번째 줄바꿈 또는 공백을 기준으로 이름과 설명 분리
                                String[] parts = linkText.split("[\\n\\r]", 2);
                                String name = parts[0].trim();
                                String shortDescription = parts.length > 1 ? parts[1].trim() : "";

                                logger.info("Processing place {}/{} on page {}: {} (ID: {})",
                                        (i + 1), placeElements.size(), pageNum, name, placeId);

                                // 상세 페이지로 이동
                                try (Page detailPage = context.newPage()) {
                                    detailPage.navigate(detailUrl);
                                    detailPage.waitForLoadState(LoadState.NETWORKIDLE);

                                    // 상세 설명 추출
                                    String description = shortDescription;
                                    ElementHandle descElement = detailPage.querySelector(
                                            "generic[ref^='s1e199'] paragraph, generic[ref^='s1e200'] paragraph, paragraph:has-text('조선')");
                                    if (descElement != null) {
                                        description = descElement.textContent().trim();
                                        // 긴 설명인 경우 첫 500자로 제한
                                        if (description.length() > 500) {
                                            description = description.substring(0, 497) + "...";
                                        }
                                    }

                                    // 주소 정보 추출
                                    String address = "";
                                    ElementHandle addressElement = detailPage.querySelector(
                                            "term:has-text('주소') + definition");
                                    if (addressElement != null) {
                                        address = addressElement.textContent().trim();
                                    }

                                    // 좌표 추출 (스크립트에서 정규식으로 찾기)
                                    Double latitude = null;
                                    Double longitude = null;

                                    // 지도 요소 찾기
                                    ElementHandle mapElement = detailPage.querySelector(
                                            "generic[ref^='s1e326'], generic[ref^='s1e327']");
                                    if (mapElement != null) {
                                        // 페이지 전체 콘텐츠에서 좌표 데이터 찾기
                                        String pageContent = detailPage.content();

                                        // 다양한 패턴으로 좌표 찾기 시도
                                        List<Pattern> patterns = Arrays.asList(
                                                Pattern.compile(
                                                        "lat\\s*[=:]\\s*([\\d.]+)\\s*,\\s*lng\\s*[=:]\\s*([\\d.]+)"),
                                                Pattern.compile(
                                                        "latitude\\s*[=:]\\s*([\\d.]+)\\s*,?\\s*longitude\\s*[=:]\\s*([\\d.]+)"),
                                                Pattern.compile(
                                                        "position\\s*[=:]\\s*\\{\\s*lat\\s*:\\s*([\\d.]+)\\s*,\\s*lng\\s*:\\s*([\\d.]+)"),
                                                Pattern.compile(
                                                        "center\\s*[=:]\\s*new\\s+google\\.maps\\.LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"),
                                                Pattern.compile("LatLng\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)")
                                        );

                                        for (Pattern pattern : patterns) {
                                            Matcher matcher = pattern.matcher(pageContent);
                                            if (matcher.find()) {
                                                try {
                                                    latitude = Double.parseDouble(matcher.group(1));
                                                    longitude = Double.parseDouble(matcher.group(2));
                                                    logger.info("Found coordinates: lat={}, lng={}", latitude,
                                                            longitude);
                                                    break;
                                                } catch (NumberFormatException e) {
                                                    logger.warn("Failed to parse coordinates", e);
                                                }
                                            }
                                        }
                                    }

                                    // 좌표가 없으면 주소를 통해 나중에 지오코딩 가능함을 로그로 남김
                                    if ((latitude == null || longitude == null) && !address.isEmpty()) {
                                        logger.info(
                                                "No coordinates found for {}, but address is available for geocoding: {}",
                                                name, address);
                                    }

                                    // Place 객체 생성
                                    Place place = Place.builder()
                                            .name(name)
                                            .description(description)
                                            .coordinate(Place.Coordinate.builder()
                                                    .latitude(latitude)
                                                    .longitude(longitude)
                                                    .build())
                                            .build();

                                    // 중복 없이 맵에 추가
                                    uniquePlaces.put(placeId, place);
                                } catch (Exception e) {
                                    logger.error("Error processing detail page for {}: {}", name, e.getMessage());
                                }

                                // 서버 부하 방지를 위한 딜레이 (짧게 유지)
                                page.waitForTimeout(200);
                            }

                        } catch (Exception e) {
                            logger.error("Error processing page {} of category {}: {}",
                                    pageNum, categoryName, e.getMessage());
                        }
                    }

                } catch (Exception e) {
                    logger.error("Error processing category {}: {}", categoryName, e.getMessage());
                }
            }

            browser.close();
        } catch (Exception e) {
            logger.error("Error while scraping Visit Seoul website", e);
        }

        List<Place> places = new ArrayList<>(uniquePlaces.values());
        logger.info("Completed scraping. Total unique places scraped: {}", places.size());
        return places;
    }

    /**
     * URL에서 장소 ID 추출
     *
     * @param url 장소 상세 페이지 URL
     * @return 장소 ID 또는 null
     */
    private String extractPlaceIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // URL 패턴: /attractions/경복궁/KOP000072
        try {
            String[] parts = url.split("/");
            // 마지막 부분이 ID (KOP...)
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.startsWith("KOP")) {
                    return lastPart;
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting place ID from URL: {}", url, e);
        }

        return null;
    }
}
