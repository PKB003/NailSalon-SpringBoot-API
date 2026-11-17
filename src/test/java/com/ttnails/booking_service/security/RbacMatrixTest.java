package com.ttnails.booking_service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.support.TestSupport;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1-07 — ma trận phân quyền (RBAC) cho các endpoint mới.
 *
 * <p>Trọng tâm: NAIL_TECH chỉ thấy lịch của chính mình; RECEPTIONIST đọc lịch nhưng
 * không sửa cấu hình thợ; MANAGER quản trị thợ nhưng không quản trị user;
 * USER không vào được khu vực nội bộ.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacMatrixTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ServiceRepository serviceRepository;
    @Autowired
    com.ttnails.booking_service.staff.repository.StaffRepository staffRepository;

    String adminToken;
    String techToken;
    String receptionistToken;
    String managerToken;
    String customerToken;
    long techStaffId;
    long otherStaffId;
    long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestSupport.adminToken(mockMvc, mapper);
        serviceId = serviceRepository.findByActiveTrueOrderByIdAsc().get(0).getId();

        techToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "tech@ttnails.test", Role.NAIL_TECH);
        User tech = userRepository.findByEmail("tech@ttnails.test").orElseThrow();

        receptionistToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "reception@ttnails.test", Role.RECEPTIONIST);
        managerToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "manager@ttnails.test", Role.MANAGER);
        customerToken = TestSupport.userTokenWithRole(mockMvc, mapper, userRepository, passwordEncoder,
                "customer@ttnails.test", Role.USER);

        // Mỗi test method chạy lại setUp trên cùng DB → tái sử dụng hồ sơ thợ đã gắn với user tech
        techStaffId = staffRepository.findByUserId(tech.getId())
                .map(com.ttnails.booking_service.staff.entity.Staff::getId)
                .orElseGet(() -> createStaffSafely("RBAC Thợ", tech.getId()));
        otherStaffId = createStaffSafely("RBAC Thợ Khác", null);
        TestSupport.addFullDayShiftsForTwoWeeks(mockMvc, adminToken, techStaffId);
        TestSupport.addFullDayShiftsForTwoWeeks(mockMvc, adminToken, otherStaffId);
    }

    private long createStaffSafely(String name, java.util.UUID userId) {
        try {
            return createStaff(name, userId);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được staff test", e);
        }
    }

    private long createStaff(String name, java.util.UUID userId) throws Exception {
        String body = "{\"displayName\":\"" + name + "\",\"skills\":[{\"serviceId\":" + serviceId + "}]"
                + (userId != null ? ",\"userId\":\"" + userId + "\"" : "") + "}";
        MvcResult result = mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("id").asLong();
    }

    @Test
    void nailTechSeesOnlyOwnSchedule() throws Exception {
        mockMvc.perform(get("/staff/me").header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) techStaffId));

        mockMvc.perform(get("/staff/me/bookings").header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/staff/" + techStaffId + "/bookings")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk());

        // Thợ không được xem lịch của thợ khác
        mockMvc.perform(get("/staff/" + otherStaffId + "/bookings")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/staff/" + otherStaffId + "/shifts")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void nailTechCannotManageStaffOrSeeAllBookings() throws Exception {
        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Illegal\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/staff/" + otherStaffId + "/skills")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCanReadButNotConfigureStaff() throws Exception {
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/" + techStaffId).header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"No Permission\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/staff/" + techStaffId)
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"No Permission\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/staff/" + techStaffId)
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanManageStaffButNotUsers() throws Exception {
        MvcResult created = mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Manager Thợ\",\"skills\":[{\"serviceId\":" + serviceId + "}]}"))
                .andExpect(status().isOk())
                .andReturn();
        long newStaffId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(put("/staff/" + newStaffId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Manager Thợ 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Manager Thợ 2"));

        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotAccessInternalEndpoints() throws Exception {
        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/staff/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound()); // chưa gắn hồ sơ thợ

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\",\"email\":\"hack@test.de\",\"phone\":\"0176999888\"," +
                                "\"password\":\"12345678\",\"dob\":\"1990-01-01\"," +
                                "\"address\":\"Teststr. 1\",\"gender\":\"MALE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAccessRules() throws Exception {
        // Public
        mockMvc.perform(get("/availability")
                        .param("date", TestSupport.workdayAfter(1).toString())
                        .param("serviceIds", String.valueOf(serviceId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/services")).andExpect(status().isOk());

        // Bảo vệ: cần token
        mockMvc.perform(get("/staff")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());

        // S1-07: POST /users không còn public (trước đây cho phép tạo user không cần token)
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anon\",\"email\":\"anon@test.de\",\"phone\":\"0176777666\"," +
                                "\"password\":\"12345678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminKeepsFullAccess() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
