package cn.liuhen.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.AppUserRepository;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;

/** 从安全上下文取当前用户。每次查库，保证锁定和改密立刻生效。 */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtService.Principal p)) {
            throw new ForbiddenException();
        }
        return users.findById(p.userId()).orElseThrow(ForbiddenException::new);
    }
}
