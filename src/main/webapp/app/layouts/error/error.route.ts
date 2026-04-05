import { Routes } from '@angular/router';

import { ErrorComponent } from './error.component';

export const errorRoute: Routes = [
  {
    path: 'error',
    component: ErrorComponent,
    data: {
      pageTitle: 'Página de erro!',
    },
  },
  {
    path: 'accessdenied',
    component: ErrorComponent,
    data: {
      pageTitle: 'Página de erro!',
      errorMessage: 'Você não tem autorização para acessar esta página.',
    },
  },
  {
    path: '404',
    component: ErrorComponent,
    data: {
      pageTitle: 'Página de erro!',
      errorMessage: 'A página não existe.',
    },
  },
  {
    path: '**',
    redirectTo: '/404',
  },
];
