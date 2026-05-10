package br.com.autodockbr.service;

public class SimulacaoEmProgressoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SimulacaoEmProgressoException() {
        super("Já existe uma simulação em progresso para este usuário. Aguarde a conclusão antes de iniciar uma nova.");
    }
}
