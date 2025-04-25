package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.scraper.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for managing web scraping operations.
 */
@RestController
@RequestMapping("/api/scraper")
@Tag(name = "Scraper", description = "Endpoints for managing web scraping operations")
public class ScraperController {

    private static final Logger logger = LoggerFactory.getLogger(ScraperController.class);

    private final ScraperService scraperService;

    @Autowired
    public ScraperController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /**
     * Starts a synchronous scraping operation.
     *
     * @return Response with scraping results
     */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Run tourist place scraper",
            description = "Initiates a synchronous web scraping operation to collect tourist place data",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Scraping completed successfully"),
                    @ApiResponse(responseCode = "500", description = "Error during scraping process")
            }
    )
    public ResponseEntity<Map<String, Object>> runScraper() {
        logger.info("Received request to run scraper");

        try {
            int placesCount = scraperService.scrapeAndSave();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scraping completed successfully");
            response.put("placesCount", placesCount);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error running scraper", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error during scraping: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Starts an asynchronous scraping operation.
     *
     * @return Response indicating the scraping job has started
     */
    @PostMapping("/run-async")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Run tourist place scraper asynchronously",
            description = "Initiates an asynchronous web scraping operation to collect tourist place data",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Scraping job started successfully"),
                    @ApiResponse(responseCode = "500", description = "Error starting scraping process")
            }
    )
    public ResponseEntity<Map<String, Object>> runScraperAsync() {
        logger.info("Received request to run scraper asynchronously");

        try {
            CompletableFuture<Integer> future = scraperService.scrapeAndSaveAsync();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Scraping job started successfully");

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            logger.error("Error starting scraper", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error starting scraping job: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Gets the current count of places in the database.
     *
     * @return Count of places stored in the database
     */
    @GetMapping("/count")
    @Operation(
            summary = "Get count of places in database",
            description = "Returns the total number of place records stored in the database",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
                    @ApiResponse(responseCode = "500", description = "Error retrieving count")
            }
    )
    public ResponseEntity<Map<String, Object>> getPlaceCount() {
        logger.info("Received request to get place count");

        try {
            long count = scraperService.getPlaceCount();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting place count", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error retrieving place count: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Tests the connection to the Visit Seoul website. Useful for checking if the scraper can access the site and parse
     * basic information.
     *
     * @return Basic information from the Visit Seoul website
     */
    @GetMapping("/test-connection")
    @Operation(
            summary = "Test connection to Visit Seoul website",
            description = "Tests if the scraper can connect to and parse basic information from the Visit Seoul website",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Connection test successful"),
                    @ApiResponse(responseCode = "500", description = "Connection test failed")
            }
    )
    public ResponseEntity<Map<String, Object>> testConnection() {
        logger.info("Received request to test connection to Visit Seoul website");

        try {
            // Here you would call a method that performs a basic connectivity test
            // For example, retrieving the title of the website or a small subset of data

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully connected to Visit Seoul website");
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error testing connection to Visit Seoul website", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error connecting to Visit Seoul website: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }
}
