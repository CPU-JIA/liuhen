package cn.liuhen.common;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.AppUserRepository;
import cn.liuhen.account.Role;

/** 开发与演示环境的种子账号。生产环境不加载。密码可通过环境变量覆盖。 */
@Configuration
@Profile("dev")
public class DevSeed {

    @Bean
    CommandLineRunner seedAccounts(AppUserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            String pwd = System.getenv().getOrDefault("LIUHEN_SEED_PASSWORD", "Liuhen@2026");
            users.save(new AppUser("A0001", "管理员", Role.ADMIN, encoder.encode(pwd), false));
            users.save(new AppUser("T0001", "T老师", Role.TEACHER, encoder.encode(pwd), false));
            users.save(new AppUser("J0001", "J老师", Role.AFFAIRS, encoder.encode(pwd), false));
        };
    }
}
