package com.musicweb.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.config.AdminBootstrapProperties;
import com.musicweb.entity.User;
import com.musicweb.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceImplTests {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void disabledBootstrapDoesNotReadOrChangeAccounts() {
        AdminAccountServiceImpl service = service(
                new AdminBootstrapProperties(false, null, null, null)
        );

        assertThat(service.ensureAdminAccount()).isNull();
        verifyNoInteractions(userService, passwordEncoder);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledBootstrapCreatesOnlyAMissingAdmin() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true,
                "BootstrapAdmin",
                "a-strong-bootstrap-password",
                "MeloSpace Admin"
        );
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(passwordEncoder.encode(properties.password())).thenReturn("encoded-password");
        when(userService.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return true;
        });

        User result = service(properties).ensureAdminAccount();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(result).isSameAs(created);
        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.getUsername()).isEqualTo("BootstrapAdmin");
        assertThat(created.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(created.getNickname()).isEqualTo("MeloSpace Admin");
        assertThat(created.getRole()).isEqualTo("ADMIN");
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingAdminPasswordRoleAndStatusAreNeverReset() {
        User existingAdmin = new User();
        existingAdmin.setId(7L);
        existingAdmin.setUsername("BootstrapAdmin");
        existingAdmin.setPasswordHash("existing-password-hash");
        existingAdmin.setRole("ADMIN");
        existingAdmin.setStatus(0);
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(existingAdmin));

        User result = service(new AdminBootstrapProperties(
                true,
                "BootstrapAdmin",
                "a-different-bootstrap-password",
                "Different Nickname"
        )).ensureAdminAccount();

        assertThat(result).isSameAs(existingAdmin);
        assertThat(existingAdmin.getPasswordHash()).isEqualTo("existing-password-hash");
        assertThat(existingAdmin.getRole()).isEqualTo("ADMIN");
        assertThat(existingAdmin.getStatus()).isZero();
        verify(passwordEncoder, never()).encode(any());
        verify(userService, never()).save(any());
        verify(userService, never()).updateById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootstrapNeverPromotesAnExistingRegularUser() {
        User existingUser = new User();
        existingUser.setUsername("BootstrapAdmin");
        existingUser.setRole("USER");
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(existingUser));

        AdminAccountServiceImpl service = service(new AdminBootstrapProperties(
                true,
                "BootstrapAdmin",
                "a-strong-bootstrap-password",
                null
        ));

        assertThatThrownBy(service::ensureAdminAccount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-admin");
        verify(passwordEncoder, never()).encode(any());
        verify(userService, never()).save(any());
        verify(userService, never()).updateById(any());
    }

    @Test
    void enabledBootstrapRequiresExplicitStrongCredentials() {
        assertThatThrownBy(() -> service(new AdminBootstrapProperties(
                true,
                "",
                "a-strong-bootstrap-password",
                null
        )).ensureAdminAccount())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_USERNAME");

        assertThatThrownBy(() -> service(new AdminBootstrapProperties(
                true,
                "BootstrapAdmin",
                "too-short",
                null
        )).ensureAdminAccount())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");

        verifyNoInteractions(userService, passwordEncoder);
    }

    private AdminAccountServiceImpl service(AdminBootstrapProperties properties) {
        return new AdminAccountServiceImpl(userService, passwordEncoder, properties);
    }
}
