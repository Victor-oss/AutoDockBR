import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { SimulacaoService } from './simulacao.service';

@Component({
  selector: 'jhi-nova-simulacao-modal',
  template: `
    <div class="modal-header">
      <h4 class="modal-title">
        <fa-icon icon="flask"></fa-icon>
        Nova Simulação de Docking
      </h4>
      <button type="button" class="btn-close" aria-label="Close" (click)="cancel()"></button>
    </div>
    <div class="modal-body">
      <form [formGroup]="form">
        <div class="mb-3">
          <label class="form-label" for="nome">Nome da Simulação *</label>
          <input
            type="text"
            class="form-control"
            id="nome"
            formControlName="nome"
            placeholder="Digite um nome para identificar esta simulação"
            [class.is-invalid]="form.get('nome')?.invalid && form.get('nome')?.touched"
          />
          <div class="invalid-feedback" *ngIf="form.get('nome')?.errors?.['required']">O nome da simulação é obrigatório.</div>
          <div class="invalid-feedback" *ngIf="form.get('nome')?.errors?.['maxlength']">O nome deve ter no máximo 255 caracteres.</div>
        </div>

        <div class="mb-3">
          <label class="form-label" for="receptor">Arquivo do Receptor (.pdb) *</label>
          <input
            type="file"
            class="form-control"
            id="receptor"
            accept=".pdb"
            (change)="onReceptorFileSelected($event)"
            [class.is-invalid]="!receptorFile && formSubmitted"
          />
          <div class="form-text">Selecione o arquivo PDB da proteína receptora.</div>
          <div class="invalid-feedback" *ngIf="!receptorFile && formSubmitted">O arquivo do receptor é obrigatório.</div>
        </div>

        <div class="mb-3">
          <label class="form-label" for="ligante">Arquivo do Ligante (.pdb) *</label>
          <input
            type="file"
            class="form-control"
            id="ligante"
            accept=".pdb"
            (change)="onLiganteFileSelected($event)"
            [class.is-invalid]="!liganteFile && formSubmitted"
          />
          <div class="form-text">Selecione o arquivo PDB da molécula ligante.</div>
          <div class="invalid-feedback" *ngIf="!liganteFile && formSubmitted">O arquivo do ligante é obrigatório.</div>
        </div>

        <div class="alert alert-info" *ngIf="!errorMessage">
          <fa-icon icon="info-circle"></fa-icon>
          A simulação de docking molecular será executada em segundo plano. Você pode acompanhar o progresso na lista de simulações e baixar
          o resultado quando concluído.
        </div>

        <div class="alert alert-danger" *ngIf="errorMessage">
          <fa-icon icon="exclamation-triangle"></fa-icon>
          {{ errorMessage }}
        </div>
      </form>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-secondary" (click)="cancel()" [disabled]="isSaving">
        <fa-icon icon="ban"></fa-icon>
        Cancelar
      </button>
      <button type="button" class="btn btn-primary" (click)="submit()" [disabled]="isSaving">
        <fa-icon icon="spinner" [spin]="true" *ngIf="isSaving"></fa-icon>
        <fa-icon icon="play" *ngIf="!isSaving"></fa-icon>
        {{ isSaving ? 'Iniciando...' : 'Iniciar Simulação' }}
      </button>
    </div>
  `,
  styles: [
    `
      :host ::ng-deep .modal-content {
        background-color: #0b1739;
        border: 1px solid #1e293b;
        color: #e2e8f0;
      }

      .modal-header {
        background-color: #0b1739;
        border-bottom: 1px solid #1e293b;
        padding: 16px 20px;
      }

      .modal-title {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 16px;
        font-weight: 600;
        color: #e2e8f0;
        background: #0b1739;
      }

      :host ::ng-deep .btn-close {
        filter: invert(1) grayscale(100%) brightness(200%);
      }

      .modal-body {
        background-color: #0b1739;
        padding: 20px;
      }

      :host ::ng-deep .form-label {
        color: #94a3b8;
        font-size: 13px;
        font-weight: 500;
        margin-bottom: 6px;
      }

      :host ::ng-deep .form-control {
        background-color: #162040;
        border: 1px solid #1e293b;
        color: #e2e8f0;
        border-radius: 6px;
      }

      :host ::ng-deep .form-control:focus {
        background-color: #162040;
        border-color: #cb3cff;
        color: #e2e8f0;
        box-shadow: 0 0 0 0.2rem rgba(203, 60, 255, 0.15);
      }

      :host ::ng-deep .form-control::placeholder {
        color: #475569;
      }

      :host ::ng-deep .form-control.is-invalid {
        border-color: #ff5a65;
      }

      :host ::ng-deep .form-text {
        color: #64748b;
        font-size: 12px;
      }

      :host ::ng-deep .invalid-feedback {
        color: #ff5a65;
        font-size: 12px;
      }

      :host ::ng-deep .alert-info {
        background-color: rgba(99, 102, 241, 0.1);
        border: 1px solid rgba(99, 102, 241, 0.4);
        color: #a5b4fc;
        border-radius: 6px;
        font-size: 13px;
      }

      :host ::ng-deep .alert-danger {
        background-color: rgba(255, 90, 101, 0.1);
        border: 1px solid rgba(255, 90, 101, 0.4);
        color: #ff5a65;
        border-radius: 6px;
        font-size: 13px;
      }

      .modal-footer {
        background-color: #0b1739;
        border-top: 1px solid #1e293b;
        padding: 14px 20px;
      }

      :host ::ng-deep .btn-secondary {
        background-color: transparent;
        border: 1px solid #334155;
        color: #94a3b8;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 500;
        border-radius: 4px;
      }

      :host ::ng-deep .btn-secondary:hover:not(:disabled) {
        background-color: #1e293b;
        border-color: #475569;
        color: #e2e8f0;
      }

      :host ::ng-deep .btn-primary {
        background-color: #cb3cff;
        border: none;
        color: #fff;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 500;
        border-radius: 4px;
      }

      :host ::ng-deep .btn-primary:hover:not(:disabled) {
        background-color: #4f46e5;
      }

      :host ::ng-deep .btn-primary:disabled,
      :host ::ng-deep .btn-secondary:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class NovaSimulacaoModalComponent implements OnInit {
  form!: FormGroup;
  receptorFile: File | null = null;
  liganteFile: File | null = null;
  isSaving = false;
  formSubmitted = false;
  errorMessage: string | null = null;

  constructor(private activeModal: NgbActiveModal, private fb: FormBuilder, private simulacaoService: SimulacaoService) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(255)]],
    });
  }

  onReceptorFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.receptorFile = input.files[0];
    }
  }

  onLiganteFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.liganteFile = input.files[0];
    }
  }

  submit(): void {
    this.formSubmitted = true;
    this.errorMessage = null;

    if (this.form.invalid || !this.receptorFile || !this.liganteFile) {
      return;
    }

    this.isSaving = true;
    const nome = this.form.get('nome')?.value;

    this.simulacaoService.create(nome, this.receptorFile, this.liganteFile).subscribe({
      next: () => {
        this.isSaving = false;
        this.activeModal.close('success');
      },
      error: error => {
        this.isSaving = false;
        if (error.error?.message) {
          this.errorMessage = error.error.message;
        } else if (error.error?.title) {
          this.errorMessage = error.error.title;
        } else {
          this.errorMessage = 'Ocorreu um erro ao iniciar a simulação. Tente novamente.';
        }
      },
    });
  }

  cancel(): void {
    this.activeModal.dismiss('cancel');
  }
}
