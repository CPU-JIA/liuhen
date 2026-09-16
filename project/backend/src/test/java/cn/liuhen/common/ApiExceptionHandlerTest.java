package cn.liuhen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;

/** 统一错误体。403 固定文案不解释原因（设计说明第 6 节），业务错误把人话原样带回。 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void forbiddenNeverExplainsWhy() {
        ResponseEntity<Map<String, Object>> ours = handler.forbidden(new ForbiddenException());
        ResponseEntity<Map<String, Object>> springs = handler.forbidden(new AccessDeniedException("内部原因不能外泄"));
        assertEquals(403, ours.getStatusCode().value());
        assertEquals("没有权限", ours.getBody().get("message"));
        assertEquals("没有权限", springs.getBody().get("message"));
    }

    @Test
    void businessErrorsCarryTheirOwnMessageAndCode() {
        assertEquals(400, handler.badRequest(new BadRequestException("课程名不能为空")).getStatusCode().value());
        assertEquals("课程名不能为空", handler.badRequest(new BadRequestException("课程名不能为空")).getBody().get("message"));
        assertEquals(409, handler.conflict(new ConflictException("已在课程中")).getStatusCode().value());
        assertEquals(404, handler.notFound(new NotFoundException("课程码不存在")).getStatusCode().value());
        assertEquals(404, handler.notFound(new NotFoundException("x")).getBody().get("code"));
    }

    @Test
    void unreadableBodyIsAFourHundredWithAHint() {
        ResponseEntity<Map<String, Object>> r = handler.unreadable(null);
        assertEquals(400, r.getStatusCode().value());
        assertEquals("请求体格式不正确", r.getBody().get("message"));
        assertTrue(r.getBody().containsKey("at"));
    }

    @Test
    void messagesThemselvesContainNoJudgementWords() {
        for (String m : new String[] {"没有权限", "请求体格式不正确", "参数不合法"}) {
            assertFalse(ForbiddenWords.scan(m).size() > 0, m);
        }
    }
}
