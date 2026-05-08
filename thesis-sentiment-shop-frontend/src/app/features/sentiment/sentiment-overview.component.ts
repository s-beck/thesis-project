import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { DecimalPipe, TitleCasePipe } from '@angular/common';
import { SentimentService } from '../../core/api/sentiment.service';
import { Sentiment, SentimentSummary } from '../../core/models/api.models';

interface DistributionRow {
  label: Sentiment;
  count: number;
  percent: number;
  bgClass: string;
  textClass: string;
  barClass: string;
}

@Component({
  selector: 'app-sentiment-overview',
  standalone: true,
  imports: [DecimalPipe, TitleCasePipe],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Sentiment Overview</h1>
        <p class="text-sm text-slate-500 mt-1">
          Aggregate statistics across all reviews in the system.
        </p>
      </div>

      @if (loading()) {
        <p class="text-slate-500">Loading…</p>
      } @else if (error()) {
        <div class="rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-800">
          {{ error() }}
        </div>
      } @else {
        @if (summary(); as s) {
        <!-- Top-line metrics -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div class="bg-white rounded-lg border border-slate-200 p-5">
            <p class="text-xs uppercase tracking-wide text-slate-500">
              Total classified
            </p>
            <p class="text-3xl font-semibold mt-1">{{ s.totalClassified }}</p>
          </div>
          <div class="bg-white rounded-lg border border-slate-200 p-5">
            <p class="text-xs uppercase tracking-wide text-slate-500">
              Pending classification
            </p>
            <p class="text-3xl font-semibold mt-1">{{ s.totalPending }}</p>
            <p class="text-xs text-slate-500 mt-1">
              Reviews persisted but not yet classified — only non-zero for asynchronous variants.
            </p>
          </div>
          <div class="bg-white rounded-lg border border-slate-200 p-5">
            <p class="text-xs uppercase tracking-wide text-slate-500">
              Total reviews
            </p>
            <p class="text-3xl font-semibold mt-1">
              {{ s.totalClassified + s.totalPending }}
            </p>
          </div>
        </div>

        <!-- Distribution -->
        <div class="bg-white rounded-lg border border-slate-200 p-5">
          <h2 class="text-sm font-medium text-slate-700 mb-4">
            Sentiment distribution
          </h2>
          @if (s.totalClassified === 0) {
            <p class="text-sm text-slate-500 italic">
              No reviews classified yet.
            </p>
          } @else {
            <div class="space-y-3">
              @for (row of distribution(); track row.label) {
                <div>
                  <div class="flex justify-between text-sm mb-1">
                    <span class="font-medium" [class]="row.textClass">
                      {{ row.label | titlecase }}
                    </span>
                    <span class="text-slate-600">
                      {{ row.count }}
                      <span class="text-slate-400 ml-1">
                        ({{ row.percent | number:'1.0-1' }}%)
                      </span>
                    </span>
                  </div>
                  <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div class="h-full transition-all"
                         [class]="row.barClass"
                         [style.width.%]="row.percent"></div>
                  </div>
                </div>
              }
            </div>
          }
        </div>
        }
      }
    </div>
  `,
})
export class SentimentOverviewComponent implements OnInit {
  private readonly sentiment$ = inject(SentimentService);

  readonly summary = signal<SentimentSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly distribution = computed<DistributionRow[]>(() => {
    const s = this.summary();
    if (!s || s.totalClassified === 0) return [];

    const rows: Array<Omit<DistributionRow, 'percent'>> = [
      {
        label: 'POSITIVE',
        count: s.counts.POSITIVE ?? 0,
        bgClass: 'bg-emerald-50',
        textClass: 'text-emerald-700',
        barClass: 'bg-emerald-500',
      },
      {
        label: 'NEUTRAL',
        count: s.counts.NEUTRAL ?? 0,
        bgClass: 'bg-gray-100',
        textClass: 'text-gray-700',
        barClass: 'bg-gray-400',
      },
      {
        label: 'NEGATIVE',
        count: s.counts.NEGATIVE ?? 0,
        bgClass: 'bg-red-50',
        textClass: 'text-red-700',
        barClass: 'bg-red-500',
      },
    ];

    return rows.map(r => ({
      ...r,
      percent: (r.count / s.totalClassified) * 100,
    }));
  });

  ngOnInit(): void {
    this.sentiment$.summary().subscribe({
      next: s => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: err => {
        this.error.set('Failed to load summary: ' + (err.message ?? 'unknown error'));
        this.loading.set(false);
      },
    });
  }
}
