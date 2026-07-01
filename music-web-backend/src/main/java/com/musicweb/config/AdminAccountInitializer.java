package com.musicweb.config;

import com.musicweb.service.AdminAccountService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
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
