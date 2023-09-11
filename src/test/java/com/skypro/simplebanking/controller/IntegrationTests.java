package com.skypro.simplebanking.controller;

import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.service.AccountService;
import com.skypro.simplebanking.service.UserService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Base64Utils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserService userService;
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

    private JSONObject getCreateUserRequest(String username, String password) throws JSONException {
        JSONObject createUserRequest = new JSONObject();
        createUserRequest.put("username", username);
        createUserRequest.put("password", password);
        return createUserRequest;
    }

    private void addUserToRepository() {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        userService.createUser("Ivan", "ivan1234");
    }

    private void addTwoUsersToRepository() {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        userService.createUser("Ivan", "ivan1234");
        userService.createUser("Petr", "petr1234");
    }

    private JSONObject getBalanceChangeRequest(Long amount) throws JSONException {
        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", amount);
        return balanceChangeRequest;
    }

    private JSONObject getTransferRequest(String username, Long idForOtherUser) throws JSONException {
        JSONObject transferRequest = new JSONObject();
        User user = userRepository.findByUsername("Petr").orElseThrow();
        Long idRecipientUser = user.getId();
        transferRequest.put("fromAccountId", getAccountId(username) + idForOtherUser);
        transferRequest.put("toUserId", idRecipientUser);
        transferRequest.put("toAccountId", getAccountId("Petr"));
        transferRequest.put("amount", 0L);
        return transferRequest;
    }

    private long getAccountId(String username) {
        long userId = userRepository.findByUsername(username).orElseThrow().getId();
        Collection<Account> account = accountRepository.findByUserId(userId);
        List<Account> accountList = new ArrayList<>(account);
        return accountList.get(0).getId();
    }

    private String base64Encoded(String login, String password) {
        return Base64Utils.encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser() throws Exception {
        userRepository.deleteAll();
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getCreateUserRequest("Ivan", "ivan1234").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Ivan"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "admin1234")
    public void createUser_WhenUserIsExist() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getCreateUserRequest("Ivan", "ivan1234").toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER", password = "user1234")
    public void createUser_WhenUserTryToCreate() throws Exception {
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getCreateUserRequest("Ivan", "ivan1234").toString()))
                .andExpect(status().isForbidden());
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
        mockMvc.perform(get("/account/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234")))
                .andExpect(status().isOk());
    }

    @Test
    public void getUserAccount_WhenAccountNotFound() throws Exception {
        addUserToRepository();
        Long id = 0L;
        mockMvc.perform(get("/account/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234")))
                .andExpect(status().isNotFound());
    }

    @Test
    public void depositToAccount() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/account/deposit/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(500L).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(501L));
    }

    @Test
    public void withdrawToAccount_WhenInsufficientFunds() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/account/withdraw/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(5L).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void withdrawToAccount_WhenInvalidAmount() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/account/withdraw/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(-1L).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void depositToAccount_WhenInvalidAmount() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/account/deposit/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(-1L).toString()))
                .andExpect(status().isBadRequest());
    }


    @Test
    public void depositToAccount_WhenAccountNotFound() throws Exception {
        addUserToRepository();
        Long id = -1L;
        mockMvc.perform(post("/account/deposit/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(1L).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void withdrawToAccount_WhenAccountNotFound() throws Exception {
        addUserToRepository();
        Long id = -1L;
        mockMvc.perform(post("/account/withdraw/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(1L).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void withdrawToAccount() throws Exception {
        addUserToRepository();
        mockMvc.perform(post("/account/withdraw/{id}", getAccountId("Ivan"))
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(1L).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0L));
    }

    @Test
    public void transfer() throws Exception {
        addTwoUsersToRepository();
        mockMvc.perform(post("/transfer/")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getTransferRequest("Ivan", 0L).toString()))
                .andExpect(status().isOk());
    }

    @Test
    public void transfer_WhenOtherUserTryToDeposit() throws Exception {
        addTwoUsersToRepository();
        mockMvc.perform(post("/transfer/")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Encoded("Ivan", "ivan1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getTransferRequest("Ivan", 1L).toString()))
                .andExpect(status().isBadRequest());
    }


    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "****")
    public void transfer_WhenAdminTryToUse() throws Exception {
        addTwoUsersToRepository();
        userService.createUser("admin", "****");
        mockMvc.perform(post("/transfer/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getTransferRequest("admin", 0L).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "****")
    public void getUserAccount_WhenAdminTryToGet() throws Exception {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        userService.createUser("admin", "****");
        Long id = getAccountId("admin");
        mockMvc.perform(get("/account/{id}", id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "****")
    public void withdrawToAccount_WhenAdminTryToUse() throws Exception {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        userService.createUser("admin", "****");
        mockMvc.perform(post("/account/withdraw/{id}", getAccountId("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(1L).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN", password = "****")
    public void depositToAccount_WhenAdminTryToUse() throws Exception {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        userService.createUser("admin", "****");
        mockMvc.perform(post("/account/deposit/{id}", getAccountId("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getBalanceChangeRequest(500L).toString()))
                .andExpect(status().isForbidden());
    }

}
