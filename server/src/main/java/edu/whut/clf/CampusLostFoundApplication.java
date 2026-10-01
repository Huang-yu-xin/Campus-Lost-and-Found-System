package edu.whut.clf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园失物招领系统后端启动类。
 * 单体服务；业务模块按 auth/user/admin/post/search/match/claim/handover/message/lead/dispute/file/audit/backup/common 组织。
 */
@SpringBootApplication
@EnableScheduling
public class CampusLostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLostFoundApplication.class, args);
    }
}
