import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
