package com.justsyncit.web.controller;

import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.UserRequests.CreateUserRequest;
import com.justsyncit.web.dto.UserRequests.UserResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private WebServerContext webServerContext;

    @Mock
    private Context ctx;

    private UserController userController;
    private final Path configDir = Paths.get("config");

    @BeforeEach
    void setUp() throws Exception {
        cleanConfig();
        // The constructor creates default admin if empty, which writes to
        // config/.admin-password
        userController = new UserController(webServerContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanConfig();
    }

    private void cleanConfig() throws IOException {
        if (Files.exists(configDir)) {
            try (Stream<Path> walk = Files.walk(configDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Test
    void createUser_ValidRequest_ShouldCreateUser() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("newuser", "password123", "New User", "user");
        when(ctx.bodyAsClass(CreateUserRequest.class)).thenReturn(request);
        when(ctx.status(anyInt())).thenReturn(ctx);

        // Act
        userController.createUser(ctx);

        // Assert
        verify(ctx).status(201);
        ArgumentCaptor<UserResponse> responseCaptor = ArgumentCaptor.forClass(UserResponse.class);
        verify(ctx).json(responseCaptor.capture());

        UserResponse response = responseCaptor.getValue();
        assertEquals("newuser", response.username());
        assertEquals("New User", response.displayName());
        assertEquals("user", response.role());
        assertNotNull(response.id());
    }

    @Test
    void createUser_DuplicateUsername_ShouldReturn409() {
        // Arrange
        // Create user first
        CreateUserRequest request1 = new CreateUserRequest("dupuser", "password123", "User 1", "user");
        when(ctx.bodyAsClass(CreateUserRequest.class)).thenReturn(request1);
        when(ctx.status(anyInt())).thenReturn(ctx);
        userController.createUser(ctx);

        // Try creating duplicate
        reset(ctx);
        CreateUserRequest request2 = new CreateUserRequest("dupuser", "password123", "User 2", "user");
        when(ctx.bodyAsClass(CreateUserRequest.class)).thenReturn(request2);
        when(ctx.status(anyInt())).thenReturn(ctx);

        // Act
        userController.createUser(ctx);

        // Assert
        // Assert
        verify(ctx).status(409);
    }
}
