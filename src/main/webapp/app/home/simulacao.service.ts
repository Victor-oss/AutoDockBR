import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { ISimulacao } from './simulacao.model';

type EntityResponseType = HttpResponse<ISimulacao>;
type EntityArrayResponseType = HttpResponse<ISimulacao[]>;

@Injectable({ providedIn: 'root' })
export class SimulacaoService {
  protected resourceUrl: string;

  constructor(private http: HttpClient, private applicationConfigService: ApplicationConfigService) {
    this.resourceUrl = this.applicationConfigService.getEndpointFor('api/simulacoes');
  }

  create(nome: string, receptorFile: File, liganteFile: File): Observable<EntityResponseType> {
    const formData = new FormData();
    formData.append('nome', nome);
    formData.append('receptor', receptorFile);
    formData.append('ligante', liganteFile);

    return this.http
      .post<ISimulacao>(this.resourceUrl, formData, { observe: 'response' })
      .pipe(map(res => this.convertResponseFromServer(res)));
  }

  findAll(): Observable<EntityArrayResponseType> {
    return this.http
      .get<ISimulacao[]>(this.resourceUrl, { observe: 'response' })
      .pipe(map(res => this.convertArrayResponseFromServer(res)));
  }

  find(id: number): Observable<EntityResponseType> {
    return this.http
      .get<ISimulacao>(`${this.resourceUrl}/${id}`, { observe: 'response' })
      .pipe(map(res => this.convertResponseFromServer(res)));
  }

  download(id: number): Observable<Blob> {
    return this.http.get(`${this.resourceUrl}/${id}/download`, {
      responseType: 'blob',
    });
  }

  downloadDump(resourcePath: string): Observable<Blob> {
    return this.http.get(this.applicationConfigService.getEndpointFor(resourcePath), {
      responseType: 'blob',
    });
  }

  protected convertResponseFromServer(res: HttpResponse<ISimulacao>): HttpResponse<ISimulacao> {
    if (res.body) {
      res.body.dataHoraPedido = res.body.dataHoraPedido ? new Date(res.body.dataHoraPedido) : undefined;
      res.body.dataHoraConclusao = res.body.dataHoraConclusao ? new Date(res.body.dataHoraConclusao) : undefined;
    }
    return res;
  }

  protected convertArrayResponseFromServer(res: HttpResponse<ISimulacao[]>): HttpResponse<ISimulacao[]> {
    if (res.body) {
      res.body.forEach(simulacao => {
        simulacao.dataHoraPedido = simulacao.dataHoraPedido ? new Date(simulacao.dataHoraPedido) : undefined;
        simulacao.dataHoraConclusao = simulacao.dataHoraConclusao ? new Date(simulacao.dataHoraConclusao) : undefined;
      });
    }
    return res;
  }
}
