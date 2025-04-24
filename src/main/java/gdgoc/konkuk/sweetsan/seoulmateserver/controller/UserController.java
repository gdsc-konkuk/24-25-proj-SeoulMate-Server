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

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User related API")
public class UserController {

    private final UserService userService;

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
