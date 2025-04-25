package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.PlaceHistoryResponse;
import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
import gdgoc.konkuk.sweetsan.seoulmateserver.exception.GlobalExceptionHandler;
import gdgoc.konkuk.sweetsan.seoulmateserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing user-related operations.
 * Provides endpoints for retrieving and updating user information,
 * and accessing user's place visit history and favorites.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User related API")
public class UserController {

    private final UserService userService;

    /**
     * Retrieves information about the currently authenticated user.
     * 
     * @param email The email of the authenticated user (injected by Spring Security)
     * @return User information
     */
    @Operation(summary = "Get my information", description = "Get information of currently logged in user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved information", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = UserInfoDto.class))}),
        @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "User information not found",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUserInfo(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getUserInfo(email));
    }

    /**
     * Updates information for the currently authenticated user.
     * If this is the first time the user is setting their information,
     * this acts as a registration endpoint.
     * 
     * @param email The email of the authenticated user (injected by Spring Security)
     * @param userInfoDto The user information to update
     * @return Updated user information
     */
    @Operation(summary = "Register/Update my information", description = "Register or update information of currently logged in user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully registered/updated information", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = UserInfoDto.class))}),
        @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "User information not found",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @PostMapping("/me")
    public ResponseEntity<UserInfoDto> updateCurrentUserInfo(
            @AuthenticationPrincipal String email,
            @RequestBody UserInfoDto userInfoDto) {
        return ResponseEntity.ok(userService.updateUserInfo(email, userInfoDto));
    }
    
    /**
     * Retrieves the authenticated user's place history or liked places.
     * Returns detailed place information for each entry in the history.
     * 
     * @param email The email of the authenticated user (injected by Spring Security)
     * @param like Optional filter flag to only return liked places
     * @return List of places in the user's history or favorites
     */
    @Operation(summary = "Get current user's place histories", 
               description = "Get the logged-in user's place history or liked places with detailed place information.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved place history", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = PlaceHistoryResponse.class))}),
        @ApiResponse(responseCode = "401", description = "Authentication failed",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))}),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))})
    })
    @GetMapping("/me/histories")
    public ResponseEntity<PlaceHistoryResponse> getCurrentUserPlaceHistories(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) Boolean like) {
        return ResponseEntity.ok(userService.getUserPlaceHistories(email, like));
    }
}
