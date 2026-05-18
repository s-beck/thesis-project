import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { ProductService } from '../../core/api/product.service';
import { Product } from '../../core/models/api.models';

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [RouterLink, CurrencyPipe],
  template: `
    <div class="space-y-6">
      <div class="flex items-end justify-between">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight">Products</h1>
          <p class="text-sm text-slate-500 mt-1">
            Browse the catalog and read customer reviews.
          </p>
        </div>
      </div>

      @if (loading()) {
        <p class="text-slate-500">Loading…</p>
      } @else if (error()) {
        <div class="rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-800">
          {{ error() }}
        </div>
      } @else if (products().length === 0) {
        <div class="rounded-md bg-slate-100 border border-slate-200 p-8 text-center">
          <p class="text-slate-600">No products yet.</p>
          <p class="text-xs text-slate-500 mt-1">
            Run the sample data loader to populate the catalog.
          </p>
        </div>
      } @else {
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          @for (p of products(); track p.id) {
            <a [routerLink]="['/products', p.id]"
               class="block bg-white rounded-lg border border-slate-200
                      hover:border-slate-300 hover:shadow-sm
                      transition overflow-hidden">
              @if (p.imageUrl) {
                <img [src]="p.imageUrl" [alt]="p.name"
                     class="w-full aspect-[4/3] object-cover bg-slate-100">
              } @else {
                <div class="w-full aspect-[4/3] bg-slate-100 flex items-center
                            justify-center text-slate-400 text-xs">
                  No image
                </div>
              }
              <div class="p-4">
                <p class="text-xs uppercase tracking-wide text-slate-500">
                  {{ p.category }}
                </p>
                <h2 class="font-medium text-slate-900 mt-1 line-clamp-1">
                  {{ p.name }}
                </h2>
                <p class="text-sm text-slate-600 mt-1 line-clamp-2">
                  {{ p.description }}
                </p>
                <p class="mt-3 font-semibold">
                  {{ p.price | currency:'EUR' }}
                </p>
              </div>
            </a>
          }
        </div>
      }
    </div>
  `,
})
export class ProductListComponent implements OnInit {
  private readonly products$ = inject(ProductService);

  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.products$.list().subscribe({
      next: page => {
        this.products.set(page.content);
        this.loading.set(false);
      },
      error: err => {
        this.error.set('Failed to load products: ' + (err.message ?? 'unknown error'));
        this.loading.set(false);
      },
    });
  }
}
