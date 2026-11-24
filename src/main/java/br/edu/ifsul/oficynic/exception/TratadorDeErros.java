package br.edu.ifsul.oficynic.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "br.edu.ifsul.oficynic.controller")
public class TratadorDeErros {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(
            MethodArgumentNotValidException exception) {

        Map<String, String> campos = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(erro ->
                        campos.putIfAbsent(
                                erro.getField(),
                                erro.getDefaultMessage()
                        )
                );

        return ResponseEntity.badRequest().body(Map.of(
                "mensagem", "Confira os campos indicados.",
                "campos", campos));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> tratarRegra(
            ResponseStatusException exception) {

        String mensagem = exception.getReason() == null
                ? "Não foi possível concluir."
                : exception.getReason();

        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler(TelefoneDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> tratarTelefoneDuplicado(
            TelefoneDuplicadoException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "mensagem", exception.getMessage(),
                "campos", Map.of("telefone", exception.getMessage())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> tratarDuplicidade() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", "Há um dado duplicado ou incompatível. Confira o cadastro."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> tratarFormato() {
        return ResponseEntity.badRequest()
                .body(Map.of("mensagem", "Os dados enviados estão em formato inválido."));
    }
}
