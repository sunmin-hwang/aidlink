package io.aidlink.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicPoliciesRemainAnonymousWhileProfileUsesHttpSessionAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/policies/search").param("q", "월세"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.sort").value("relevance"));
        mockMvc.perform(get("/api/v1/policies/search")
                        .param("page", "99")
                        .param("size", "2")
                        .param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/v1/me/bookmarks"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"safe-password-123"}
                                """))
                .andExpect(status().isNoContent());

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"safe-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"safe-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));

        mockMvc.perform(put("/api/v1/me/profile")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"birthDate":"1998-05-01","regionCode":"SEOUL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionCode").value("SEOUL"));

        mockMvc.perform(get("/api/v1/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1998-05-01"));

        mockMvc.perform(post("/api/v1/eligibility/analyze")
                        .param("page", "0")
                        .param("size", "2")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"profile":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.summary.UNKNOWN").value(5));

        mockMvc.perform(post("/api/v1/simulations")
                        .param("page", "0")
                        .param("size", "2")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"currentProfile":{},"changes":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.summary.UNCHANGED_NOT_ELIGIBLE").value(5));

        mockMvc.perform(post("/api/v1/me/bookmarks/1")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/bookmarks/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
        mockMvc.perform(get("/api/v1/me/bookmarks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12));
        mockMvc.perform(delete("/api/v1/me/bookmarks/1")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/bookmarks/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(false));
        mockMvc.perform(get("/api/v1/me/bookmarks").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/admin/reviews").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/policies/lifecycle").session(session))
                .andExpect(status().isForbidden());
    }
}
