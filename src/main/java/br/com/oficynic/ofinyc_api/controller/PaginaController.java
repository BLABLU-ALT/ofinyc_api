package br.com.oficynic.ofinyc_api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
    @GetMapping("/login")
    public String login() { return "login"; }

    private String pagina(Model model, String secao) {
        model.addAttribute("secao", secao);
        return "cadastros";
    }
}
