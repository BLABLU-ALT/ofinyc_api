package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.repository.ClienteRepository;
import br.com.oficynic.ofinyc_api.repository.MotocicletaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Totais da Visão Geral calculados direto no banco (sem contadores fictícios).
@RestController
@RequestMapping("/api/painel")
public class PainelController {
    private final ClienteRepository clientes;
    private final MotocicletaRepository motocicletas;

    public PainelController(ClienteRepository clientes, MotocicletaRepository motocicletas) {
        this.clientes = clientes;
        this.motocicletas = motocicletas;
    }

    public record Resumo(long clientes, long motocicletas) { }

    @GetMapping("/resumo")
    public Resumo resumo() {
        return new Resumo(clientes.count(), motocicletas.count());
    }
}
