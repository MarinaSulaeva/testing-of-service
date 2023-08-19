package com.skypro.simplebanking.controller;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.binary.Base64;
import com.skypro.simplebanking.dto.AccountDTO;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.dto.CreateUserRequest;
import com.skypro.simplebanking.dto.UserDTO;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.service.AccountService;
import com.skypro.simplebanking.service.UserService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.persistence.Basic;
import javax.validation.Valid;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
//@Testcontainers
public class IntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserService userService;
    private PasswordEncoder passwordEncoder;

//    @Container
//    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest")
//            .withUsername("postgres")
//            .withPassword("73aberiv");
//
//    @DynamicPropertySource
//    static void postgresProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//    }


    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser() throws Exception {
        userRepository.deleteAll();
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", "Ivan");
        createUserRequest.put("password", "ivan1234");
        mockMvc.perform(post("/user/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUserRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Ivan"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser_WhenUserIsExist() throws Exception {
        addUserToRepository();
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", "Ivan");
        createUserRequest.put("password", "ivan1234");
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest.toString()))
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
                        .content(createUserRequest.toString()))
                .andExpect(status().isForbidden());
    }

    public void addUserToRepository() {
        userRepository.deleteAll();
        userService.createUser("Ivan", "ivan1234");
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

    @Test
    public void getUserAccount() throws Exception {
        addUserToRepository();
        String login = "Ivan";
        String password = "ivan1234";
        Long id = 1L;
        String base64Encoded = Base64Utils.encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/account/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded))
                .andExpect(status().isOk());
    }


    @Test
    public void getListUser_withAuthentication() throws Exception {
        addUserToRepository();
        String login = "Ivan";
        String password = "ivan1234";
        String base64Encoded = Base64Utils.encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(
                        get("/user/list")
                                .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Ivan"));
    }


}
