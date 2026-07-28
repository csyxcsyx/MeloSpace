package com.musicweb.config;

import com.musicweb.service.AdminAccountService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "music-web.admin-bootstrap", name = "enabled", havingValue = "true")
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminAccountService adminAccountService;

    public AdminAccountInitializer(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminAccountService.ensureAdminAccount();
    }
}
