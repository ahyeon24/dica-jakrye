package com.dicajakrye.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신고가 접수되면 운영 메일로 알린다. 신고가 실제로 저장된 뒤(커밋 후)에만, 응답을 늦추지 않게 따로 보낸다.
 * 메일 설정(spring.mail.*)이 없으면 로그만 남긴다 (로컬 개발).
 * 메일에는 신고자 정보를 넣지 않는다. 자세한 내용은 관리자 화면에서 본다.
 */
@Component
public class ReportNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReportNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String to;
    private final String from;
    private final String frontUrl;

    public ReportNotifier(ObjectProvider<JavaMailSender> mailSender,
                          @Value("${app.notify.admin-email}") String to,
                          @Value("${spring.mail.username:}") String from,
                          @Value("${app.front-url}") String frontUrl) {
        this.mailSender = mailSender;
        this.to = to;
        this.from = from;
        this.frontUrl = frontUrl.endsWith("/") ? frontUrl.substring(0, frontUrl.length() - 1) : frontUrl;
    }

    @Async
    @TransactionalEventListener
    public void onReportCreated(ReportCreatedEvent e) {
        String subject = "[디카작례] 신고 접수: " + e.reason().label() + (e.hidden() ? " (작례 가림)" : "");
        String body = """
                신고가 접수됐어요.

                사유: %s
                작례: %d%s
                처리: %s/admin/reports

                초상권·도용 신고는 30일 안에 삭제하거나 기각해야 해요. (정보통신망법 제44조의2)
                """.formatted(e.reason().label(), e.photoId(), e.hidden() ? " (접수 즉시 가렸어요)" : "", frontUrl);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from.isBlank()) {
            log.info("신고 알림 (메일 설정 없음): {} / 작례 {}", e.reason(), e.photoId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (RuntimeException ex) {
            // 메일이 실패해도 신고는 이미 저장됐다. 관리자 화면에서 볼 수 있다
            log.warn("신고 알림 메일 실패 (신고 {}): {}", e.reportId(), ex.toString());
        }
    }
}
