package com.tuapp.servicios.application.port;

import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;

public interface AiAnalysisPort {
    AiAnalysisPortResult analyzeConsumption(ConsumptionHistoryContext context);
}
