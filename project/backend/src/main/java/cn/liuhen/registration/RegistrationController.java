package cn.liuhen.registration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.liuhen.security.CurrentUser;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService registrations;
    private final CurrentUser currentUser;

    public RegistrationController(RegistrationService registrations, CurrentUser currentUser) {
        this.registrations = registrations;
        this.currentUser = currentUser;
    }

    @GetMapping("/ai-tools")
    public List<Map<String, Object>> tools() {
        return registrations.presetTools().stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "name", t.getName()))
                .toList();
    }

    @GetMapping("/works/{workId}/registrations")
    public List<Registration> list(@PathVariable Long workId) {
        return registrations.listOf(currentUser.require(), workId);
    }

    @PostMapping("/works/{workId}/registrations")
    public Registration create(@PathVariable Long workId, @RequestBody RegistrationService.Input in) {
        return registrations.create(currentUser.require(), workId, in, LocalDateTime.now());
    }

    /** "修改"实际是新建一条并作废旧条，所以用 PUT 到旧条上语义仍然成立。 */
    @PutMapping("/works/{workId}/registrations/{id}")
    public Registration supersede(@PathVariable Long workId, @PathVariable Long id, @RequestBody RegistrationService.Input in) {
        return registrations.supersede(currentUser.require(), workId, id, in, LocalDateTime.now());
    }

    /** 作废，不是删除。数据库层的 DELETE 会被触发器拒绝。 */
    @DeleteMapping("/works/{workId}/registrations/{id}")
    public Map<String, Object> voidOne(@PathVariable Long workId, @PathVariable Long id) {
        registrations.voidRegistration(currentUser.require(), workId, id);
        return Map.of("id", id, "status", RegStatus.VOIDED.name());
    }
}
