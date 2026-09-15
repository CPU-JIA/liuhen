package cn.liuhen.account;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.liuhen.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String loginNo, @NotBlank String password) { }

    public record PasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) { }

    private final AccountService accounts;
    private final CurrentUser currentUser;

    public AuthController(AccountService accounts, CurrentUser currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public AccountService.LoginResult login(@Valid @RequestBody LoginRequest req) {
        return accounts.login(req.loginNo().trim(), req.password(), LocalDateTime.now());
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordRequest req) {
        accounts.changePassword(currentUser.require(), req.oldPassword(), req.newPassword());
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AppUser u = currentUser.require();
        return Map.of("id", u.getId(), "loginNo", u.getLoginNo(), "name", u.getName(), "role", u.getRole().name(),
                "mustChangePassword", u.isMustChangePassword());
    }
}
