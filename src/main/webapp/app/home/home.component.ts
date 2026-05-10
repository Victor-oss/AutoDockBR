import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/auth/account.model';
import { SimulacaoService } from './simulacao.service';
import { ISimulacao } from './simulacao.model';
import { NovaSimulacaoModalComponent } from './nova-simulacao-modal.component';

@Component({
  selector: 'jhi-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent implements OnInit, OnDestroy {
  account: Account | null = null;
  simulacoes: ISimulacao[] = [];
  isLoading = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private accountService: AccountService,
    private router: Router,
    private simulacaoService: SimulacaoService,
    private modalService: NgbModal
  ) {}

  ngOnInit(): void {
    this.accountService
      .getAuthenticationState()
      .pipe(takeUntil(this.destroy$))
      .subscribe(account => {
        this.account = account;
        if (account) {
          this.loadSimulacoes();
        }
      });
  }

  login(): void {
    this.router.navigate(['/login']);
  }

  loadSimulacoes(): void {
    this.isLoading = true;
    this.simulacaoService.findAll().subscribe({
      next: response => {
        this.simulacoes = response.body || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      },
    });
  }

  openNovaSimulacaoModal(): void {
    const modalRef = this.modalService.open(NovaSimulacaoModalComponent, {
      size: 'lg',
      backdrop: 'static',
    });
    modalRef.result.then(
      result => {
        if (result === 'success') {
          this.loadSimulacoes();
        }
      },
      () => {}
    );
  }

  downloadResultado(simulacao: ISimulacao): void {
    if (simulacao.id && simulacao.status === 'CONCLUIDO') {
      this.simulacaoService.download(simulacao.id).subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${simulacao.nome}_resultado.dlg`;
        a.click();
        window.URL.revokeObjectURL(url);
      });
    }
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'CONCLUIDO':
        return 'badge bg-success';
      case 'EM_PROGRESSO':
        return 'badge bg-warning';
      case 'ERRO':
        return 'badge bg-danger';
      default:
        return 'badge bg-secondary';
    }
  }

  getStatusLabel(status?: string): string {
    switch (status) {
      case 'CONCLUIDO':
        return 'Concluído';
      case 'EM_PROGRESSO':
        return 'Em Progresso';
      case 'ERRO':
        return 'Erro';
      default:
        return status || '';
    }
  }

  formatSize(bytes?: number | null): string {
    if (bytes == null) {
      return '-';
    }
    const kb = bytes / 1024;
    return kb.toFixed(2) + ' kB';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
