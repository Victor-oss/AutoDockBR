export interface ISimulacao {
  id?: number;
  nome?: string;
  status?: SimulacaoStatus;
  tamanhoBytes?: number | null;
  dataHoraPedido?: Date;
  dataHoraConclusao?: Date | null;
  usuarioId?: number;
  usuarioNome?: string;
}

export type SimulacaoStatus = 'EM_PROGRESSO' | 'CONCLUIDO' | 'ERRO';

export class Simulacao implements ISimulacao {
  constructor(
    public id?: number,
    public nome?: string,
    public status?: SimulacaoStatus,
    public tamanhoBytes?: number | null,
    public dataHoraPedido?: Date,
    public dataHoraConclusao?: Date | null,
    public usuarioId?: number,
    public usuarioNome?: string
  ) {}
}
