import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-screen flex flex-col">
      <header class="bg-white border-b border-slate-200">
        <div class="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <a routerLink="/" class="text-xl font-semibold tracking-tight text-slate-900">
            Sentiment Shop
          </a>
          <nav class="flex gap-2 text-sm">
            <a routerLink="/products"
               routerLinkActive="bg-slate-900 text-white"
               [routerLinkActiveOptions]="{ exact: false }"
               class="px-3 py-1.5 rounded-md text-slate-600 hover:bg-slate-100">
              Products
            </a>
            <a routerLink="/sentiment"
               routerLinkActive="bg-slate-900 text-white"
               class="px-3 py-1.5 rounded-md text-slate-600 hover:bg-slate-100">
              Sentiment Overview
            </a>
          </nav>
        </div>
      </header>

      <main class="flex-1 max-w-6xl w-full mx-auto px-6 py-8">
        <router-outlet />
      </main>

      <footer class="border-t border-slate-200 py-4 text-center text-xs text-slate-500">
        Reference application for the thesis on ML integration architectures.
      </footer>
    </div>
  `,
})
export class AppComponent {}
