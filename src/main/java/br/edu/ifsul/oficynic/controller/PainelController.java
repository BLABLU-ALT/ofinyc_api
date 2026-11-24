package br.edu.ifsul.oficynic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsul.oficynic.repository.ClienteRepository;
import br.edu.ifsul.oficynic.repository.MotocicletaRepository;

@RestController
@RequestMapping("/api/painel")
public class PainelController {

    private final ClienteRepository clienteRepository;
    private final MotocicletaRepository motocicletaRepository;

    public PainelController(
            ClienteRepository clienteRepository,
            MotocicletaRepository motocicletaRepository) {

        this.clienteRepository = clienteRepository;
        this.motocicletaRepository = motocicletaRepository;
    }

    public record Resumo(
            long clientes,
            long motocicletas) {
    }

    @GetMapping("/resumo")
    public Resumo resumo() {
        return new Resumo(clienteRepository.count(), motocicletaRepository.count());
    }
}
