package br.com.oficynic.ofinyc_api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PaginaController {
    @GetMapping("/")
    public String inicio(Model model) { return pagina(model, "inicio"); }
    @GetMapping("/clientes")
    public String clientes(Model model) { return pagina(model, "clientes"); }
    @GetMapping("/motocicletas")
    public String motocicletas(Model model) { return pagina(model, "motocicletas"); }
    @GetMapping("/produtos")
    public String produtos(Model model) { return pagina(model, "produtos"); }
    @GetMapping("/servicos")
    public String servicos(Model model) { return pagina(model, "servicos"); }
    @GetMapping("/ordens-servico")
    public String ordensServico(Model model) { return pagina(model, "ordens-servico"); }
    @GetMapping("/ordens-servico/{id}")
    public String detalheOrdem(@PathVariable Long id, Model model) {
        model.addAttribute("ordemId", id);
        return pagina(model, "os-detalhe");
    }
    @GetMapping("/funcionarios")
    public String funcionarios(Model model) { return pagina(model, "funcionarios"); }
    @GetMapping("/estoque")
    public String estoque(Model model) { return pagina(model, "estoque"); }
    @GetMapping("/login")
    public String login() { return "login"; }

    private String pagina(Model model, String secao) {
        model.addAttribute("secao", secao);
        return "cadastros";
    }
}
