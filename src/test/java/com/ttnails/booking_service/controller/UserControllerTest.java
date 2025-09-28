package com.ttnails.booking_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ttnails.booking_service.dto.UserRequest;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.enums.Gender;
import com.ttnails.booking_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDate;
import java.util.UUID;


@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;

    private UserRequest userRequest;
    private User user;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        userRequest = UserRequest.builder()
                .dob(LocalDate.of(2007, 9, 12))
                .gender(Gender.FEMALE)
                .address("No 27, X Street, Y City")
                .phone("0123456789")
                .name("Anna")
                .email("oppOopp@gmail.com")
                .password("12345678")
                .build();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Anna");
        user.setEmail("oppOopp@gmail.com");
        user.setPhone("0123456789");
        user.setDob(LocalDate.of(2007, 9, 12));
        user.setAddress("No 27, X Street, Y City");
        user.setGender(Gender.FEMALE);
    }


    @Test
    public void createUser() throws Exception{
        Mockito.when(userService.createUser(ArgumentMatchers.any()))
                .thenReturn(user);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Success"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.result.name").value("Anna"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.result.email").value("oppOopp@gmail.com"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.result.phone").value("0123456789"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.result.address").value("No 27, X Street, Y City"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.result.gender").value("FEMALE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.timeStamp").exists());
    }
}
