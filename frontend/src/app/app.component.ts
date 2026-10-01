import { Component, OnDestroy } from '@angular/core';

@Component({
    selector: 'app-root',
    template: `
        <aside *ngIf="auctionNotice" class="auction-notice" role="status">
            {{ auctionNotice }}
        </aside>
        <router-outlet></router-outlet>
    `,
    styles: [`
        .auction-notice {
            box-sizing: border-box;
            width: 100vw;
            padding: 12px 16px;
            background: #fff3cd;
            color: #664d03;
            text-align: center;
            font-weight: 600;
            line-height: 1.5;
        }
    `]
})
export class AppComponent implements OnDestroy {
    auctionNotice = this.noticeForToday();
    private readonly noticeTimer = setInterval(() => {
        this.auctionNotice = this.noticeForToday();
    }, 60_000);

    ngOnDestroy(): void {
        clearInterval(this.noticeTimer);
    }

    private noticeForToday(): string {
        const today = new Intl.DateTimeFormat('sv-SE', {
            timeZone: 'Europe/Rome', year: 'numeric', month: '2-digit', day: '2-digit'
        }).format(new Date());
        if (today === '2026-09-30') {
            return 'Domani, 1 ottobre, si svolgerà l’asta del mercato di riparazione.';
        }
        if (today === '2026-10-01') {
            return 'Oggi, 1 ottobre, si svolge l’asta del mercato di riparazione.';
        }
        return '';
    }
}
