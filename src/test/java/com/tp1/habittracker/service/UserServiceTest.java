package com.tp1.habittracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tp1.habittracker.domain.model.User;
import com.tp1.habittracker.dto.user.CreateUserRequest;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void createUserNormalizesFieldsAndSavesUser() {
        CreateUserRequest request = new CreateUserRequest("  Manu  ", "  USER@Example.COM  ");

        when(userRepository.existsByUsernameIgnoreCase("Manu")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId("user-1");
            return toSave;
        });

        User created = userService.createUser(request);

        assertEquals("user-1", created.getId());
        assertEquals("Manu", created.getUsername());
        assertEquals("user@example.com", created.getEmail());
        verify(userRepository).existsByUsernameIgnoreCase("Manu");
        verify(userRepository).existsByEmailIgnoreCase("user@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getUserByIdThrowsWhenUserDoesNotExist() {
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById("missing-user"));
    }
}
