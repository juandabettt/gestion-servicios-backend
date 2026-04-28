package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.UpdateProfileRequest;
import com.tuapp.servicios.application.dto.response.UserProfileResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (request.getNombre() != null && !request.getNombre().isBlank())
            user.setNombre(request.getNombre());

        if (request.getTelefono() != null && !request.getTelefono().isBlank())
            user.setTelefono(request.getTelefono());

        if (request.getCiudad() != null)
            user.setCiudad(request.getCiudad());

        if (request.getDocumento() != null)
            user.setDocumento(request.getDocumento());

        userRepository.save(user);
        return toProfileResponse(user);
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .telefono(user.getTelefono())
                .ciudad(user.getCiudad())
                .documento(user.getDocumento())
                .build();
    }
}
