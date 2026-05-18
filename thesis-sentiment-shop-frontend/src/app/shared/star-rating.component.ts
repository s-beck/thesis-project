import { Component, computed, input, output, signal } from '@angular/core';

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@Component({
  selector: 'app-star-rating',
  standalone: true,
  template: `
    <div class="inline-flex items-center gap-0.5"
         [class.cursor-pointer]="interactive()">
      @for (s of stars(); track s.index) {
        @if (interactive()) {
          <button type="button"
                  (click)="select(s.index)"
                  (mouseenter)="hover.set(s.index)"
                  (mouseleave)="hover.set(0)"
                  [attr.aria-label]="s.index + ' star' + (s.index === 1 ? '' : 's')"
                  class="p-0.5 hover:scale-110 transition-transform">
            <svg viewBox="0 0 20 20"
                 [attr.width]="size()"
                 [attr.height]="size()"
                 [class.text-amber-400]="s.index <= effectiveValue()"
                 [class.text-slate-300]="s.index > effectiveValue()"
                 fill="currentColor"
                 xmlns="http://www.w3.org/2000/svg">
              <path d="M10 1.5l2.59 5.25 5.79.84-4.19 4.08.99 5.77L10 14.71l-5.18 2.73.99-5.77L1.62 7.59l5.79-.84L10 1.5z"/>
            </svg>
          </button>
        } @else {
          <svg viewBox="0 0 20 20"
               [attr.width]="size()"
               [attr.height]="size()"
               [class.text-amber-400]="s.index <= value()"
               [class.text-slate-300]="s.index > value()"
               fill="currentColor"
               xmlns="http://www.w3.org/2000/svg">
            <path d="M10 1.5l2.59 5.25 5.79.84-4.19 4.08.99 5.77L10 14.71l-5.18 2.73.99-5.77L1.62 7.59l5.79-.84L10 1.5z"/>
          </svg>
        }
      }
    </div>
  `,
})
export class StarRatingComponent {
  readonly value = input(0);
  readonly interactive = input(false);
  readonly size = input(16);
  readonly valueChange = output<number>();

  /** Hover state for interactive mode — preview rating before commit. */
  protected readonly hover = signal(0);

  protected readonly stars = computed(() =>
    [1, 2, 3, 4, 5].map(i => ({ index: i }))
  );

  /** What stars to highlight in interactive mode: hover overrides value. */
  protected readonly effectiveValue = computed(() =>
    this.hover() > 0 ? this.hover() : this.value()
  );

  protected select(rating: number): void {
    this.valueChange.emit(rating);
  }
}
