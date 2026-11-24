package br.edu.ifsul.oficynic.exception;

public class TelefoneDuplicadoException extends RuntimeException {

    public TelefoneDuplicadoException() {
        super("Já existe um cliente cadastrado com este telefone.");
    }
}
