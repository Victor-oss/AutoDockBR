import { Component, OnInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';

import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/auth/account.model';

const initialAccount: Account = {} as Account;

@Component({
  selector: 'jhi-settings',
  templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
  success = false;

  settingsForm = new FormGroup({
    name: new FormControl(initialAccount.name as string, {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(4), Validators.maxLength(255)],
    }),
    description: new FormControl(initialAccount.description as string | null, {
      nonNullable: false,
      validators: [Validators.maxLength(255)],
    }),
    email: new FormControl(initialAccount.email, {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(5), Validators.maxLength(254), Validators.email],
    }),
  });

  constructor(private accountService: AccountService) {}

  ngOnInit(): void {
    this.accountService.identity().subscribe(account => {
      if (account) {
        this.settingsForm.patchValue({
          name: (account as any).name,
          description: (account as any).description,
          email: account.email,
        });
      }
    });
  }

  save(): void {
    this.success = false;

    const account = this.settingsForm.getRawValue();
    this.accountService.save(account).subscribe(() => {
      this.success = true;

      this.accountService.authenticate(account);
    });
  }
}
