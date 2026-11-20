package io.aidlink.interfaces.api;

import io.aidlink.simulation.application.SimulationCriteria;
import io.aidlink.simulation.application.SimulationService;
import io.aidlink.simulation.application.SimulationSort;
import io.aidlink.simulation.domain.SimulationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public SimulationResult simulate(
            @Valid @RequestBody SimulationRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort
    ) {
        return simulationService.simulate(
                request.currentProfile(),
                request.changes(),
                request.effectiveDate(),
                new SimulationCriteria(
                        page,
                        size,
                        SimulationCriteria.resolveStatus(status),
                        SimulationSort.resolve(sort)
                )
        );
    }
}

