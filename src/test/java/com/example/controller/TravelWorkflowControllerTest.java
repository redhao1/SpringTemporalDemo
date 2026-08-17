package com.example.controller;

import com.example.dto.TravelRequest;
import com.example.starter.TravelBookingWorkflowStarter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Unit test of TravelWorkflowController
// @WebMvcTest loads only the web layer (this controller + Spring MVC infra), not the full
// context, so no Temporal client is built.
//
// The starter it depends on is replaced with a mock.
@WebMvcTest(TravelWorkflowController.class)
class TravelWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TravelBookingWorkflowStarter starter;

    @Test
    void bookTravelStartsWorkflowAndReturnsConfirmationMessage() throws Exception {
        TravelRequest request = new TravelRequest("user-1", "Paris", "2026-09-01");

        mockMvc.perform(post("/travel/book")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user-1")));

        verify(starter).startWorkFlow(eq(request));
    }

    @Test
    void confirmBookingSendsSignalAndReturnsConfirmationMessage() throws Exception {
        mockMvc.perform(post("/travel/confirm/{userId}", "user-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("confirmed")));

        verify(starter).sendConfirmationSignal("user-1");
    }
}
