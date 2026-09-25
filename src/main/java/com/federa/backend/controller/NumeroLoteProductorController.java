package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.NumeroLoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.service.LoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRutas.V1 + "/productores")
public class NumeroLoteProductorController {
    private final LoteService lotes;
    public NumeroLoteProductorController(LoteService lotes) { this.lotes = lotes; }

    @PutMapping("/{id}/numero-lote")
    public LoteResponse guardar(@PathVariable Long id, @Valid @RequestBody NumeroLoteRequest request) {
        return lotes.guardarSoloNumero(id, request);
    }
}
