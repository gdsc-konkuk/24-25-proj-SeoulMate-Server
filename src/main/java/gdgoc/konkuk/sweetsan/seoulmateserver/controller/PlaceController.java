package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.MLChatbotResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceRecommendationResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.ChatbotRequestWithHistory;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.GlobalExceptionHandler;
import gdgoc.konkuk.sweetsan.seoulmateserver.model.ChatType;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Tag(name = "Place API", description = "Place related API")
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "Get place recommendations", description = "Get personalized place recommendations based on user's location and preferences.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved recommendations", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = PlaceRecommendationResponse.class))}),
            @ApiResponse(responseCode = "401", description = "Authentication failed", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "404", description = "User not found", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @GetMapping("/recommendations")
    public ResponseEntity<PlaceRecommendationResponse> getPlaceRecommendations(
            @AuthenticationPrincipal String email,
            @RequestParam double x,
            @RequestParam double y) {
        return ResponseEntity.ok(placeService.getPlaceRecommendations(email, x, y));
    }

    @Operation(summary = "Get chatbot response for a place", description = "Get personalized chatbot response about a specific place.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved chatbot response", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = MLChatbotResponse.class))}),
            @ApiResponse(responseCode = "401", description = "Authentication failed", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "404", description = "User or place not found", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping({"/chat/{placeId}", "/chat"})
    public ResponseEntity<MLChatbotResponse> getChatbotResponse(
            @AuthenticationPrincipal String email,
            @PathVariable(required = false) String placeId,
            @RequestParam ChatType chatType,
            @RequestBody(required = false) ChatbotRequestWithHistory request) {
        return ResponseEntity.ok(placeService.getChatbotResponse(email, placeId, chatType, request));
    }
}
