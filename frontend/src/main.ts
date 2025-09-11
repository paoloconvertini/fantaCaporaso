import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { AppModule } from './app/app.module';
import { ErrorHandler, Injectable } from '@angular/core';

@Injectable()
export class DetailedErrorHandler implements ErrorHandler {
    handleError(error: any): void {
        console.error('🔥 Angular error caught:', error);

        // 🔎 stampiamo anche le proprietà "nascoste"
        console.dir(error);

        if (error && typeof error === 'object') {
            for (const key of Object.keys(error)) {
                console.log(`👉 ${key}:`, (error as any)[key]);
            }
        }

        throw error;
    }
}

platformBrowserDynamic()
    .bootstrapModule(AppModule, {
        providers: [{ provide: ErrorHandler, useClass: DetailedErrorHandler }]
    })
    .catch(err => console.error(err));
