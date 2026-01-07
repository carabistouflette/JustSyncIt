package com.justsyncit.web.controller;

import com.justsyncit.web.model.User;
import com.justsyncit.web.service.AuthService;
import com.justsyncit.web.service.SqliteAuthStore;
import com.justsyncit.web.dto.UserRequests.CreateUserRequest;
import com.justsyncit.web.dto.UserRequests.UserResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private SqliteAuthStore authStore;

    @Mock
    private AuthService authService;

    @Mock
    private Context ctx;

    private UserController userController;

    @BeforeEach
    void setUp() throws Exception {
        // Prevent default admin creation by simulating existing users
        when(authStore.listUsers())
                .thenReturn(java.util.Collections.singletonList(new User("admin", "admin", "Admin", "admin")));
        userController = new UserController(authStore, authService);

        // Clear invocations from constructor to ensure tests start fresh
        clearInvocations(authStore);
    }

    @Test
    void createUser_ValidRequest_ShouldCreateUser() throws Exception {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("newuser", "password123", "New User", "user");
        when(ctx.bodyAsClass(CreateUserRequest.class)).thenReturn(request);
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(authStore.getUserByUsername("newuser")).thenReturn(Optional.empty());

        // Act
        userController.createUser(ctx);

        // Assert
        verify(ctx).status(201);
        ArgumentCaptor<UserResponse> responseCaptor = ArgumentCaptor.forClass(UserResponse.class);
        verify(ctx).json(responseCaptor.capture());

        verify(authStore).createUser(any(User.class));

        UserResponse response = responseCaptor.getValue();
        assertEquals("newuser", response.username());
        assertEquals("New User", response.displayName());
        assertEquals("user", response.role());
        assertNotNull(response.id());
    }

    @Test
    void createUser_DuplicateUsername_ShouldReturn409() throws Exception {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("dupuser", "password123", "User 1", "user");
        when(ctx.bodyAsClass(CreateUserRequest.class)).thenReturn(request);
        when(ctx.status(anyInt())).thenReturn(ctx);

        // Simulate existing user
        when(authStore.getUserByUsername("dupuser"))
                .thenReturn(Optional.of(new User("id", "dupuser", "User 1", "user")));

        // Act
        userController.createUser(ctx);

        // Assert
        verify(ctx).status(409);
        // Verify we NEVER called createUser on store
        verify(authStore, never()).createUser(any(User.class));
    }

    @Test
    void login_ShouldSetSecureCookie() throws Exception {
        // Arrange
        com.justsyncit.web.dto.UserRequests.LoginRequest request = new com.justsyncit.web.dto.UserRequests.LoginRequest(
                "admin", "password");

        when(ctx.bodyAsClass(com.justsyncit.web.dto.UserRequests.LoginRequest.class)).thenReturn(request);
        when(ctx.scheme()).thenReturn("https"); // Secure context

        // Mock user existing
        User adminUser = new User("adminId", "admin", "Admin", "admin");
        adminUser.setPassword("password"); // This sets PBKDF2 hash by default, assumes simple setter in test harness?
        // Actually User.setPassword hashes it. verifyPassword uses stored hash.
        // We need authStore to return this user.
        when(authStore.getUserByUsername("admin")).thenReturn(Optional.of(adminUser));

        // Mock authService session creation
        when(authService.createSession(adminUser)).thenReturn("session-token");

        // Act
        userController.login(ctx);

        // Assert
        ArgumentCaptor<String> headerKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx).header(headerKeyCaptor.capture(), headerValueCaptor.capture());

        // Find Set-Cookie
        boolean cookieFound = false;
        java.util.List<String> keys = headerKeyCaptor.getAllValues();
        java.util.List<String> values = headerValueCaptor.getAllValues();

        for (int i = 0; i < keys.size(); i++) {
            if ("Set-Cookie".equalsIgnoreCase(keys.get(i))) {
                String cookieVal = values.get(i);
                assertTrue(cookieVal.contains("session=session-token"));
                assertTrue(cookieVal.contains("HttpOnly"));
                assertTrue(cookieVal.contains("SameSite=Strict"));
                assertTrue(cookieVal.contains("Secure")); // Because scheme is https
                cookieFound = true;
            }
        }
        assertTrue(cookieFound, "Set-Cookie header not found");
    }
}
