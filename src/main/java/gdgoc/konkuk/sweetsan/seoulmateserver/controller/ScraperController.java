package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.ScraperConnectionResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.ScraperCountResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.ScraperRunResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.GlobalExceptionHandler;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for managing web scraping operations. Provides endpoints for initiating scraping jobs, checking
 * scraper status, and retrieving information about scraped data.
 */
@RestController
@RequestMapping("/api/scraper")
@Tag(name = "Scraper", description = "Endpoints for managing web scraping operations")
public class ScraperController {

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
            description = "Initiates a synchronous web scraping operation to collect tourist place data"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scraping completed successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScraperRunResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Error during scraping process",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    public ResponseEntity<ScraperRunResponse> runScraper() {
        try {
            int placesCount = scraperService.scrapeAndSave();

            ScraperRunResponse response = ScraperRunResponse.builder()
                    .success(true)
                    .message("Scraping completed successfully")
                    .placesCount(placesCount)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ScraperRunResponse response = ScraperRunResponse.builder()
                    .success(false)
                    .message("Error during scraping: " + e.getMessage())
                    .build();

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
            description = "Initiates an asynchronous web scraping operation to collect tourist place data"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Scraping job started successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScraperRunResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Error starting scraping process",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    public ResponseEntity<ScraperRunResponse> runScraperAsync() {
        try {
            CompletableFuture<Integer> future = scraperService.scrapeAndSaveAsync();

            ScraperRunResponse response = ScraperRunResponse.builder()
                    .success(true)
                    .message("Scraping job started successfully")
                    .build();

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            ScraperRunResponse response = ScraperRunResponse.builder()
                    .success(false)
                    .message("Error starting scraping job: " + e.getMessage())
                    .build();

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
            description = "Returns the total number of place records stored in the database"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScraperCountResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Error retrieving count",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    public ResponseEntity<ScraperCountResponse> getPlaceCount() {
        try {
            long count = scraperService.getPlaceCount();

            ScraperCountResponse response = ScraperCountResponse.builder()
                    .success(true)
                    .count(count)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ScraperCountResponse response = ScraperCountResponse.builder()
                    .success(false)
                    .message("Error retrieving place count: " + e.getMessage())
                    .build();

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
            description = "Tests if the scraper can connect to and parse basic information from the Visit Seoul website"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Connection test successful",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScraperConnectionResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Connection test failed",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    public ResponseEntity<ScraperConnectionResponse> testConnection() {
        try {
            boolean success = scraperService.testConnection();

            ScraperConnectionResponse response = ScraperConnectionResponse.builder()
                    .success(success)
                    .message("Successfully connected to Visit Seoul website")
                    .timestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ScraperConnectionResponse response = ScraperConnectionResponse.builder()
                    .success(false)
                    .message("Error connecting to Visit Seoul website: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();

            return ResponseEntity.status(500).body(response);
        }
    }
}
