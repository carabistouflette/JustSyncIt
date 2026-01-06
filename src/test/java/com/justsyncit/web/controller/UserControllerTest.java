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
}
