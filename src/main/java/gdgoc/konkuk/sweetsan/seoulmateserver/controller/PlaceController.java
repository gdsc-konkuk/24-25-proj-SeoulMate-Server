package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.GlobalExceptionHandler;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing places.
 */
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Tag(name = "Place API", description = "Place related API")
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "Create a new place", description = "Add a new place to the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Place successfully created",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlaceDto.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid place data provided",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping
    public ResponseEntity<PlaceDto> createPlace(
            @AuthenticationPrincipal String email,
            @RequestBody PlaceDto placeDto) {
        PlaceDto createdPlace = placeService.createPlace(placeDto, email);
        return new ResponseEntity<>(createdPlace, HttpStatus.CREATED);
    }

    @Operation(summary = "Get places by name search", description = "Retrieve places by searching in their names")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved places",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlaceDto.class))})
    })
    @GetMapping("/search")
    public ResponseEntity<List<PlaceDto>> searchPlacesByName(@RequestParam String name) {
        return ResponseEntity.ok(placeService.searchPlacesByName(name));
    }

    @Operation(summary = "Update a place", description = "Update an existing place")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Place successfully updated",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlaceDto.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid place data provided",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Place not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping("/{id}")
    public ResponseEntity<PlaceDto> updatePlace(
            @AuthenticationPrincipal String email,
            @PathVariable String id,
            @RequestBody PlaceDto placeDto) {
        placeDto.setId(id);
        return ResponseEntity.ok(placeService.updatePlace(placeDto, email));
    }

    @Operation(summary = "Like or dislike a place", description = "Record a user's preference for a place")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Preference successfully recorded",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = PlaceDto.class))}),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Place not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping("/{id}/like")
    public ResponseEntity<PlaceDto> likeOrDislikePlace(
            @AuthenticationPrincipal String email,
            @PathVariable String id,
            @RequestParam Boolean like) {
        PlaceDto placeDto = placeService.recordPlacePreference(id, email, like);
        return ResponseEntity.ok(placeDto);
    }
}
