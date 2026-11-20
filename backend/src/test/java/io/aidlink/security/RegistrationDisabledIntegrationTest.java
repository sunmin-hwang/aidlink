package io.aidlink.security;

import io.aidlink.user.application.UserAccountOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aidlink.security.registration-enabled=false")
@AutoConfigureMockMvc
class RegistrationDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountOperations accounts;

    @Test
    void blocksNewAccountsWithoutBlockingExistingAccountLogin() throws Exception {
        accounts.register("existing@example.com", "existing-password-123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"blocked@example.com","password":"blocked-password-123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("현재 회원가입이 잠시 중단되어 있습니다."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"blocked@example.com","password":"blocked-password-123"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"existing@example.com","password":"existing-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("existing@example.com"));
    }
}
