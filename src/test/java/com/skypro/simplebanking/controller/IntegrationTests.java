package com.skypro.simplebanking.controller;

import com.skypro.simplebanking.dto.CreateUserRequest;
import com.skypro.simplebanking.dto.UserDTO;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import org.json.JSONObject;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.validation.Valid;

import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class IntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest")
            .withUsername("postgres")
            .withPassword("73aberiv");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }


    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser() throws Exception {
        userRepository.deleteAll();
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", "Ivan");
        createUserRequest.put("password", "ivan1234");
        mockMvc.perform(post("/user/")
//                        .header("admin-token", "SUPER_SECRET_KEY_FROM_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .contentType(createUserRequest.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser_WhenUserIsExist() throws Exception {
        addUserToRepository();
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", "Ivan");
        createUserRequest.put("password", "ivan1234");
        mockMvc.perform(post("/user/")
//                        .header("admin-token", "SUPER_SECRET_KEY_FROM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .contentType(createUserRequest.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER", password = "user1234")
    public void createUser_WhenUserTryToCreate() throws Exception {
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", "Ivan");
        createUserRequest.put("password", "ivan1234");
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .contentType(createUserRequest.toString()))
                .andExpect(status().isForbidden());
    }

    public void addUserToRepository() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("Ivan");
        user.setPassword(passwordEncoder.encode("ivan1234"));
        userRepository.save(user);
    }

    @Test
    @WithMockUser(username = "Ivan", roles = "USER", password = "ivan1234")
    public void getListUser() throws Exception {
        addUserToRepository();
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Ivan"));
    }

    @Test
    @WithMockUser(username = "ADMIN", roles = "ADMIN", password = "admin1234")
    public void getListUser_WhenAdminTryToGet() throws Exception {
        addUserToRepository();
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isForbidden());
    }
}
