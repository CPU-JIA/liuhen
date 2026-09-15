package cn.liuhen.timeline;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cn.liuhen.account.AppUser;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.RegistrationRepository;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.PasteEvent;
import cn.liuhen.work.PasteEventRepository;
import cn.liuhen.work.Snapshot;
import cn.liuhen.work.SnapshotService;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;

/**
 * 时间线：把快照、粘贴事件、登记三类条目按时间合并。
 * 学生端和教师端走同一个方法，只有权限判断不同，保证两端看到的一样多（AC-WRK-05-2）。
 * 只有三类条目，没有逐键记录，没有任何时长统计（AC-TCH-01-3、AC-TCH-03-3）。
 */
@Service
public class TimelineAssembler {

    public record Item(String type, LocalDateTime at, Map<String, Object> data) { }

    private final SnapshotService snapshots;
    private final PasteEventRepository pastes;
    private final RegistrationRepository registrations;
    private final WorkService works;
    private final AccessControl access;

    public TimelineAssembler(SnapshotService snapshots, PasteEventRepository pastes, RegistrationRepository registrations,
                             WorkService works, AccessControl access) {
        this.snapshots = snapshots;
        this.pastes = pastes;
        this.registrations = registrations;
        this.works = works;
        this.access = access;
    }

    public List<Item> build(AppUser actor, Long workId) {
        Work work = works.require(workId);
        access.requireWorkVisible(actor, work);
        List<Item> items = new ArrayList<>();
        Integer prevChars = null;
        for (Snapshot s : snapshots.listOf(workId)) {
            int delta = prevChars == null ? s.getCharCount() : s.getCharCount() - prevChars;
            items.add(new Item("SNAPSHOT", s.getCreatedAt(), Map.of(
                    "seqNo", s.getSeqNo(),
                    "trigger", s.getTrigger().name(),
                    "charCount", s.getCharCount(),
                    "charDelta", delta,
                    "keyframe", s.isKeyframe())));
            prevChars = s.getCharCount();
        }
        for (PasteEvent p : pastes.findByWorkIdOrderByOccurredAtAsc(workId)) {
            items.add(new Item("PASTE", p.getOccurredAt(), Map.of(
                    "id", p.getId(),
                    "charCount", p.getCharCount(),
                    "source", p.getSource().name(),
                    "registrationId", p.getRegistrationId() == null ? 0L : p.getRegistrationId())));
        }
        for (Registration r : registrations.findByWorkIdOrderByCreatedAtAsc(workId)) {
            items.add(new Item("REGISTRATION", r.getCreatedAt(), Map.of(
                    "id", r.getId(),
                    "stage", r.getStage().sceneName(),
                    "adoption", r.getAdoption().label(),
                    "status", r.getStatus().name(),
                    "toolId", r.getToolId() == null ? 0L : r.getToolId(),
                    "toolNameCustom", r.getToolNameCustom() == null ? "" : r.getToolNameCustom())));
        }
        items.sort(Comparator.comparing(Item::at));
        return items;
    }
}
