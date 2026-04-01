package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.UpdatePreferencesRequest;
import com.tuapp.servicios.application.dto.response.UserPreferencesResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.UserPreferencesMapper;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.model.UserPreferences;
import com.tuapp.servicios.domain.repository.UserPreferencesRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final UserPreferencesMapper preferencesMapper;

    @Transactional(readOnly = true)
    public UserPreferencesResponse get(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .map(preferencesMapper::toResponse)
                .orElseGet(() -> createDefault(userId));
    }

    @Transactional
    public UserPreferencesResponse update(UpdatePreferencesRequest request, UUID userId) {
        UserPreferences prefs = preferencesRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
            return UserPreferences.builder().user(user).build();
        });
        if (request.getDiasAnticipacionAlerta() != null) prefs.setDiasAnticipacionAlerta(request.getDiasAnticipacionAlerta());
        if (request.getMetodoPagoDefault() != null) prefs.setMetodoPagoDefault(request.getMetodoPagoDefault());
        if (request.getPresupuestoMensualAgua() != null) prefs.setPresupuestoMensualAgua(request.getPresupuestoMensualAgua());
        if (request.getPresupuestoMensualEnergia() != null) prefs.setPresupuestoMensualEnergia(request.getPresupuestoMensualEnergia());
        if (request.getPresupuestoMensualGas() != null) prefs.setPresupuestoMensualGas(request.getPresupuestoMensualGas());
        if (request.getPresupuestoMensualInternet() != null) prefs.setPresupuestoMensualInternet(request.getPresupuestoMensualInternet());
        if (request.getNotificacionesEmail() != null) prefs.setNotificacionesEmail(request.getNotificacionesEmail());
        if (request.getNotificacionesPush() != null) prefs.setNotificacionesPush(request.getNotificacionesPush());
        return preferencesMapper.toResponse(preferencesRepository.save(prefs));
    }

    private UserPreferencesResponse createDefault(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        return preferencesMapper.toResponse(
                preferencesRepository.save(UserPreferences.builder().user(user).build()));
    }
}
