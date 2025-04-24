package gdgoc.konkuk.sweetsan.seoulmateserver.controller;

import gdgoc.konkuk.sweetsan.seoulmateserver.dto.UserInfoDto;
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
@Tag(name = "User API", description = "사용자 관련 API")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 가져오기", description = "현재 로그인한 사용자의 정보를 가져옵니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "정보 조회 성공", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = UserInfoDto.class))}),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "사용자 정보 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUserInfo(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getUserInfo(email));
    }

    @Operation(summary = "내 회원정보 등록/수정", description = "현재 로그인한 사용자의 정보를 등록하거나 수정합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "정보 등록/수정 성공", 
                    content = {@Content(mediaType = "application/json", 
                    schema = @Schema(implementation = UserInfoDto.class))}),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "사용자 정보 없음")
    })
    @PostMapping("/me")
    public ResponseEntity<UserInfoDto> updateCurrentUserInfo(
            @AuthenticationPrincipal String email,
            @RequestBody UserInfoDto userInfoDto) {
        return ResponseEntity.ok(userService.updateUserInfo(email, userInfoDto));
    }
}
