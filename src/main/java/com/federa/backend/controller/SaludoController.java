package com.federa.backend.controller;

import com.federa.backend.model.Saludo;
import com.federa.backend.repository.SaludoRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saludos")
@Tag(name = "Saludos (demo)", description =
        "Endpoint de prueba del andamiaje original, ajeno al padrón. Queda fuera de /api/v1 "
        + "a propósito para no romper al frontend que ya lo consume. Se puede borrar junto "
        + "con la tabla `saludos` cuando ya no haga falta.")
public class SaludoController {

    private final SaludoRepository repository;

    public SaludoController(SaludoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Saludo> listar() {
        return repository.findAll();
    }

    @PostMapping
    public Saludo crear(@RequestBody Saludo saludo) {
        return repository.save(saludo);
    }
}
