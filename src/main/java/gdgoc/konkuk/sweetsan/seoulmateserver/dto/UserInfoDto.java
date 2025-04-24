package gdgoc.konkuk.sweetsan.seoulmateserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 정보 DTO")
public class UserInfoDto {

    @Schema(description = "사용자 이름", example = "홍길동")
    private String name;

    @Schema(description = "생년월일", example = "1990-01-01")
    private LocalDate birthYear;
}
