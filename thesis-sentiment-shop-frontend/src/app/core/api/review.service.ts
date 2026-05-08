import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Review, SubmitReviewRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly http = inject(HttpClient);

  list(productId: number, page = 0, size = 20): Observable<Page<Review>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Review>>(
      `/api/products/${productId}/reviews`,
      { params }
    );
  }

  submit(productId: number, request: SubmitReviewRequest): Observable<Review> {
    return this.http.post<Review>(
      `/api/products/${productId}/reviews`,
      request
    );
  }
}
