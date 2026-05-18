import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SentimentSummary } from '../models/api.models';

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@Injectable({ providedIn: 'root' })
export class SentimentService {
  private readonly http = inject(HttpClient);

  summary(): Observable<SentimentSummary> {
    return this.http.get<SentimentSummary>('/api/sentiment/summary');
  }
}
