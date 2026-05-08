import { Component, input } from '@angular/core';
import { TitleCasePipe, DecimalPipe } from '@angular/common';
import { Sentiment } from '../core/models/api.models';

@Component({
  selector: 'app-sentiment-badge',
  standalone: true,
  imports: [TitleCasePipe, DecimalPipe],
  template: `
    @if (sentiment(); as s) {
      <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5
                   text-xs font-medium"
            [class.bg-emerald-50]="s === 'POSITIVE'"
            [class.text-emerald-700]="s === 'POSITIVE'"
            [class.bg-gray-100]="s === 'NEUTRAL'"
            [class.text-gray-700]="s === 'NEUTRAL'"
            [class.bg-red-50]="s === 'NEGATIVE'"
            [class.text-red-700]="s === 'NEGATIVE'">
        <span class="h-1.5 w-1.5 rounded-full"
              [class.bg-emerald-500]="s === 'POSITIVE'"
              [class.bg-gray-400]="s === 'NEUTRAL'"
              [class.bg-red-500]="s === 'NEGATIVE'"></span>
        {{ s | titlecase }}
        @if (confidence() !== null && confidence() !== undefined) {
          <span class="text-[10px] opacity-70">
            ({{ (confidence()! * 100) | number:'1.0-0' }}%)
          </span>
        }
      </span>
    } @else {
      <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5
                   text-xs font-medium bg-amber-50 text-amber-700">
        <span class="h-1.5 w-1.5 rounded-full bg-amber-400 animate-pulse"></span>
        Pending
      </span>
    }
  `,
})
export class SentimentBadgeComponent {
  readonly sentiment = input<Sentiment | null>(null);
  readonly confidence = input<number | null | undefined>(null);
}
