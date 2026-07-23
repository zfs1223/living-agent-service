package com.luohuo.flex.im.core.sensitive;

import com.luohuo.basic.annotation.log.WebLog;
import com.luohuo.basic.base.R;
import com.luohuo.flex.im.common.utils.sensitiveword.SensitiveWordBs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/sensitiveWord")
@Tag(name = "敏感词")
public class SensitiveWordController {

    private final SensitiveWordBs sensitiveWordBs;

    @PostMapping("/refresh")
    @Operation(summary = "刷新敏感词词库")
    @WebLog("刷新敏感词词库")
    public R<Boolean> refresh() {
        sensitiveWordBs.init();
        return R.success(true);
    }

    @PostMapping("/check")
    @Operation(summary = "检测并过滤文本")
    @WebLog("检测并过滤文本")
    public R<CheckResp> check(@RequestParam("text") String text) {
        boolean hit = sensitiveWordBs.hasSensitiveWord(text);
        String filtered = sensitiveWordBs.filter(text);
        return R.success(new CheckResp(hit, filtered));
    }

    public static class CheckResp {
        public final boolean hit;
        public final String filtered;

        public CheckResp(boolean hit, String filtered) {
            this.hit = hit;
            this.filtered = filtered;
        }
    }
}
