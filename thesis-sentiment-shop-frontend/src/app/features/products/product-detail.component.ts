import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ProductService } from '../../core/api/product.service';
import { ReviewService } from '../../core/api/review.service';
import { Product, Review } from '../../core/models/api.models';
import { SentimentBadgeComponent } from '../../shared/sentiment-badge.component';
import { StarRatingComponent } from '../../shared/star-rating.component';

@Component({
    selector: 'app-product-detail',
    standalone: true,
    imports: [
        RouterLink,
        CurrencyPipe,
        DatePipe,
        SentimentBadgeComponent,
        StarRatingComponent,
    ],
    template: `
        <div class="space-y-8">
            <a routerLink="/products"
               class="text-sm text-slate-500 hover:text-slate-900 inline-flex items-center gap-1">
                ← Back to products
            </a>

            @if (loading()) {
                <p class="text-slate-500">Loading…</p>
            } @else if (error()) {
                <div class="rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-800">
                    {{ error() }}
                </div>
            } @else {
                @if (product(); as p) {
                    <!-- Product summary -->
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                        <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
                            @if (p.imageUrl) {
                                <img [src]="p.imageUrl" [alt]="p.name"
                                     class="w-full aspect-[4/3] object-cover">
                            } @else {
                                <div class="w-full aspect-[4/3] bg-slate-100 flex items-center
                            justify-center text-slate-400">
                                    No image
                                </div>
                            }
                        </div>
                        <div>
                            <p class="text-xs uppercase tracking-wide text-slate-500">{{ p.category }}</p>
                            <h1 class="text-2xl font-semibold tracking-tight mt-1">{{ p.name }}</h1>
                            <p class="text-2xl font-semibold mt-3">{{ p.price | currency:'EUR' }}</p>
                            <p class="text-slate-600 mt-4 leading-relaxed">{{ p.description }}</p>
                        </div>
                    </div>

                    <!-- Reviews section -->
                    <section class="space-y-4">
                        <h2 class="text-xl font-semibold tracking-tight">
                            Customer reviews
                            <span class="text-sm font-normal text-slate-500">
                ({{ reviews().length }})
              </span>
                        </h2>

                        <!-- Submission form -->
                        <div class="bg-white rounded-lg border border-slate-200 p-5 space-y-3">
                            <h3 class="text-sm font-medium text-slate-700">Write a review</h3>
                            <div class="flex items-center gap-3">
                                <span class="text-sm text-slate-600">Rating:</span>
                                <app-star-rating
                                        [value]="newRating()"
                                        [interactive]="true"
                                        [size]="22"
                                        (valueChange)="newRating.set($event)" />
                            </div>
                            <textarea [value]="newText()"
                                      (input)="newText.set($any($event.target).value)"
                                      rows="3"
                                      placeholder="Share your thoughts about this product…"
                                      class="w-full border border-slate-300 rounded px-3 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-slate-400">
              </textarea>
                            <div class="flex justify-end items-center gap-3">
                                @if (submitError()) {
                                    <span class="text-xs text-red-700">{{ submitError() }}</span>
                                }
                                <button (click)="submit()"
                                        [disabled]="submitting() || !newText().trim() || newRating() === 0"
                                        class="px-4 py-1.5 rounded-md bg-slate-900 text-white text-sm
                               font-medium hover:bg-slate-800 disabled:opacity-50
                               disabled:cursor-not-allowed">
                                    {{ submitting() ? 'Submitting…' : 'Submit review' }}
                                </button>
                            </div>
                        </div>

                        <!-- Review list -->
                        @if (reviews().length === 0) {
                            <p class="text-sm text-slate-500 italic px-1">
                                No reviews yet — be the first.
                            </p>
                        } @else {
                            <ul class="divide-y divide-slate-200 bg-white rounded-lg border border-slate-200">
                                @for (r of reviews(); track r.id) {
                                    <li class="p-5">
                                        <div class="flex items-start justify-between gap-3">
                                            <div>
                                                <div class="flex items-center gap-2">
                                                    <p class="text-sm font-medium text-slate-900">{{ r.author }}</p>
                                                    <app-star-rating [value]="r.rating" [size]="14" />
                                                </div>
                                                <p class="text-xs text-slate-500 mt-0.5">
                                                    {{ r.createdAt | date:'medium' }}
                                                </p>
                                            </div>
                                            <app-sentiment-badge
                                                    [sentiment]="r.sentiment"
                                                    [confidence]="r.sentimentConfidence" />
                                        </div>
                                        <p class="text-sm text-slate-700 mt-3 leading-relaxed whitespace-pre-line">
                                            {{ r.text }}
                                        </p>
                                    </li>
                                }
                            </ul>
                        }
                    </section>
                }
            }
        </div>
    `,
})
export class ProductDetailComponent implements OnInit {
    /** Bound from the route via withComponentInputBinding(). */
    readonly id = input.required<string>();

    private readonly products$ = inject(ProductService);
    private readonly reviews$ = inject(ReviewService);

    readonly product = signal<Product | null>(null);
    readonly reviews = signal<Review[]>([]);
    readonly loading = signal(true);
    readonly error = signal<string | null>(null);

    readonly newText = signal('');
    readonly newRating = signal(0);
    readonly submitting = signal(false);
    readonly submitError = signal<string | null>(null);

    ngOnInit(): void {
        this.loadAll();
    }

    private loadAll(): void {
        const productId = Number(this.id());
        this.loading.set(true);
        this.error.set(null);

        this.products$.get(productId).subscribe({
            next: p => this.product.set(p),
            error: err => {
                this.error.set('Failed to load product: ' + (err.message ?? 'unknown error'));
                this.loading.set(false);
            },
        });

        this.reviews$.list(productId).subscribe({
            next: page => {
                this.reviews.set(page.content);
                this.loading.set(false);
            },
            error: err => {
                this.error.set('Failed to load reviews: ' + (err.message ?? 'unknown error'));
                this.loading.set(false);
            },
        });
    }

    submit(): void {
        const productId = Number(this.id());
        const text = this.newText().trim();
        const rating = this.newRating();
        if (!text || rating === 0) return;

        this.submitting.set(true);
        this.submitError.set(null);

        this.reviews$.submit(productId, { text, rating }).subscribe({
            next: created => {
                this.reviews.update(rs => [created, ...rs]);
                this.newText.set('');
                this.newRating.set(0);
                this.submitting.set(false);
            },
            error: err => {
                this.submitError.set('Failed to submit: ' + (err.message ?? 'unknown error'));
                this.submitting.set(false);
            },
        });
    }
}
