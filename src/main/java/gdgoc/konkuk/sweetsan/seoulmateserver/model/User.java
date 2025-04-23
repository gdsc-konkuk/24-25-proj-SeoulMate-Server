package gdgoc.konkuk.sweetsan.seoulmateserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String name;

    @Builder.Default
    private AuthProvider provider = AuthProvider.GOOGLE;

    private String providerId;

    private String refreshToken;
    private LocalDateTime refreshTokenExpireDate;

    @Builder.Default
    private List<String> roles = List.of("ROLE_USER");

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    public enum AuthProvider {
        GOOGLE
    }
}
