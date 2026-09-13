package br.com.oficynic.ofinyc_api.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "br.com.oficynic.ofinyc_api.controller")
public class TratadorDeErros {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validacao(MethodArgumentNotValidException exception) {
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(erro ->
                campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira os campos indicados.", "campos", campos));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> regra(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                Map.of("mensagem", exception.getReason() == null ? "Não foi possível concluir." : exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicidade() {
        return ResponseEntity.status(409).body(Map.of("mensagem", "Há um dado duplicado ou incompatível. Confira o cadastro."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> formato() {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Os dados enviados estão em formato inválido."));
    }
}
