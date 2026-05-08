import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'products',
  },
  {
    path: 'products',
    loadComponent: () =>
      import('./features/products/product-list.component')
        .then(m => m.ProductListComponent),
    title: 'Products',
  },
  {
    path: 'products/:id',
    loadComponent: () =>
      import('./features/products/product-detail.component')
        .then(m => m.ProductDetailComponent),
    title: 'Product',
  },
  {
    path: 'sentiment',
    loadComponent: () =>
      import('./features/sentiment/sentiment-overview.component')
        .then(m => m.SentimentOverviewComponent),
    title: 'Sentiment Overview',
  },
  {
    path: '**',
    redirectTo: 'products',
  },
];
