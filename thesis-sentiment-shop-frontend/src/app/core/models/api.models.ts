
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  imageUrl: string | null;
  category: string;
}

export interface Review {
  id: number;
  productId: number;
  author: string;
  text: string;
  rating: number;
  sentiment: Sentiment | null;
  sentimentConfidence: number | null;
  createdAt: string;
  classifiedAt: string | null;
}

export interface SentimentSummary {
  counts: Record<Sentiment, number>;
  totalClassified: number;
  totalPending: number;
}

/** Spring Data Page<T> JSON shape. */
export interface Page<T> {
  content: T[];
  number: number;            // page index (zero-based)
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface SubmitReviewRequest {
  text: string;
  rating: number;
}
