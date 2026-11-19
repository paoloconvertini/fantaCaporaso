import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';

interface ChatMessage {
  author: string;
  text: string;
}

interface PresenceTeam {
  id: number;
  name: string;
  online: boolean;
}

interface Presence {
  blocking: boolean;
  teams: PresenceTeam[];
}

@Component({
  selector: 'app-auction-chat',
  template: `
    <section class="presence" *ngIf="admin && presence?.teams?.length" aria-label="Presenze asta">
      <header class="presence-header">
        <div class="presence-title">
          <mat-icon aria-hidden="true">groups</mat-icon>
          <div><strong>Presenze</strong><span>{{ onlineCount }} su {{ presence?.teams?.length }} squadre richieste collegate</span></div>
        </div>
        <button type="button" mat-icon-button class="presence-refresh" (click)="refreshPresence()"
                aria-label="Aggiorna presenze" matTooltip="Aggiorna presenze">
          <mat-icon aria-hidden="true">refresh</mat-icon>
        </button>
      </header>
      <ul class="presence-chips" *ngIf="missing.length" aria-label="Squadre mancanti">
        <li *ngFor="let team of missing">
          <span aria-hidden="true"></span><span class="team-name">{{ team.name }}</span>
          <button type="button" mat-icon-button class="invite-share" (click)="shareInvite(team)"
                  [disabled]="sharingTeamId !== undefined"
                  [attr.aria-label]="'Condividi su WhatsApp per ' + team.name"
                  matTooltip="Condividi invito su WhatsApp">
            <mat-icon aria-hidden="true">share</mat-icon>
          </button>
        </li>
      </ul>
      <p class="presence-note" *ngIf="presence?.blocking && missing.length">La prima chiamata richiede tutte le squadre.</p>
      <p *ngIf="inviteFeedback" class="presence-note" role="status">{{ inviteFeedback }}</p>
      <div *ngIf="inviteText" class="invite-fallback">
        <textarea aria-label="Invito WhatsApp" readonly [value]="inviteText" #invite></textarea>
        <a mat-icon-button [href]="whatsappUrl(inviteText)" target="_blank" rel="noopener noreferrer"
           aria-label="Apri WhatsApp" matTooltip="Apri WhatsApp"><mat-icon aria-hidden="true">share</mat-icon></a>
        <button type="button" mat-icon-button (click)="copyFallback(invite)"
                aria-label="Copia testo" matTooltip="Copia testo"><mat-icon aria-hidden="true">content_copy</mat-icon></button>
        <button type="button" mat-icon-button (click)="inviteText = ''"
                aria-label="Chiudi invito" matTooltip="Chiudi invito"><mat-icon aria-hidden="true">close</mat-icon></button>
      </div>
    </section>
    <details class="chat" (toggle)="onToggle($event)">
      <summary>
        <mat-icon aria-hidden="true">forum</mat-icon>
        <strong>Chat asta</strong>
        <span class="chat-status" *ngIf="!connected">Connessione in corso</span>
        <span *ngIf="unread" class="unread" role="status">{{ unread }} {{ unread === 1 ? 'nuovo messaggio' : 'nuovi messaggi' }}</span>
        <mat-icon class="expand" aria-hidden="true">expand_more</mat-icon>
      </summary>
      <div class="conversation">
        <ul #messageList aria-label="Messaggi della chat" aria-live="polite">
          <li class="empty" *ngIf="!messages.length">Ancora nessun messaggio</li>
          <li *ngFor="let message of messages" class="message" [class.own]="message.author === auth.username">
            <strong>{{ message.author }}</strong>
            <span>{{ message.text }}</span>
          </li>
        </ul>
        <form (ngSubmit)="send()">
          <input aria-label="Messaggio" name="message" [(ngModel)]="draft" maxlength="300" autocomplete="off" placeholder="Scrivi un messaggio…">
          <button type="submit" mat-raised-button color="primary" [disabled]="!connected || coolingDown || !draft.trim()">Invia</button>
        </form>
      </div>
    </details>
  `,
  styles: [`
    :host { display: block; min-width: 0; margin: .75rem 0; }
    .presence { margin-bottom: .75rem; padding: .75rem; border: 1px solid #38554a; border-radius: 14px; background: #14261f; color: #eef8f3; }
    .presence-header, .presence-title { display: flex; align-items: center; gap: .55rem; }
    .presence-header { justify-content: space-between; }
    .presence-title > mat-icon, .presence-refresh { color: #8ff0c3; }
    .presence-title span { display: block; color: #bbcdc5; font-size: .8rem; }
    .presence-refresh { flex: 0 0 auto; }
    ul.presence-chips { display: flex; flex-wrap: wrap; gap: .35rem; list-style: none; margin: 0; padding: .55rem 0 0; }
    .presence-chips li { display: inline-flex; align-items: center; gap: .35rem; min-width: 0; max-width: 100%; padding: .15rem .2rem .15rem .5rem; border: 1px solid #6d5642; border-radius: 999px; background: #392d23; color: #ffe0b9; font-size: .8rem; overflow-wrap: anywhere; }
    .presence-chips li > span[aria-hidden] { width: .4rem; height: .4rem; flex: 0 0 .4rem; border-radius: 50%; background: #ffc786; }
    .team-name { min-width: 0; }
    .invite-share { flex: 0 0 32px; width: 32px; height: 32px; line-height: 32px; background: #a3f0bc; color: #123522; }
    .invite-share:disabled { background: #4b6455; color: #e4eee8; }
    .invite-share mat-icon { vertical-align: middle; font-size: 1rem; width: 1rem; height: 1rem; line-height: 1rem; }
    .invite-fallback textarea { display: block; box-sizing: border-box; width: 100%; min-height: 8rem; margin-top: .5rem; }
    .presence-note { margin: .55rem 0 0; color: #ffe0b9; font-size: .8rem; }
    .chat { background: #14261f; border: 1px solid #38554a; border-radius: 14px; color: #eef8f3; }
    summary { display: flex; align-items: center; flex-wrap: wrap; gap: .55rem; min-height: 48px; padding: .35rem .8rem; cursor: pointer; list-style: none; }
    summary::-webkit-details-marker { display: none; }
    summary mat-icon { color: #8ff0c3; }
    summary .expand { margin-left: auto; }
    .chat[open] .expand { transform: rotate(180deg); }
    .chat-status { font-size: .75rem; color: #bbcdc5; }
    .unread { border-radius: 999px; padding: .2rem .5rem; background: #8ff0c3; color: #143328; font-size: .75rem; font-weight: 700; }
    .conversation { border-top: 1px solid #38554a; }
    .conversation ul { display: flex; flex-direction: column; gap: .5rem; height: clamp(11rem, 30vh, 17rem); overflow-y: auto; padding: .75rem; margin: 0; list-style: none; }
    .empty { margin: auto; color: #bbcdc5; font-size: .85rem; }
    .message { display: grid; gap: .15rem; align-self: flex-start; max-width: 85%; padding: .5rem .7rem; border-radius: 12px 12px 12px 3px; background: #2b4038; overflow-wrap: anywhere; }
    .message strong { color: #b9dfcd; font-size: .72rem; }
    .message.own { align-self: flex-end; border-radius: 12px 12px 3px 12px; background: #215642; }
    form { display: flex; align-items: center; gap: .5rem; padding: .65rem; border-top: 1px solid #38554a; }
    input { flex: 1; min-width: 0; padding: .55rem .6rem; border: 1px solid #607c70; border-radius: 8px; background: #0f201a; color: #eef8f3; font: inherit; font-size: 1rem; }
    input::placeholder { color: #bbcdc5; }
    button[type="submit"]:disabled { background: #3b5047; color: #c2d1ca; }
  `]
})
export class AuctionChatComponent implements OnInit, OnDestroy {
  @Input() admin = false;
  @ViewChild('messageList') private messageList?: ElementRef<HTMLUListElement>;
  unread = 0;
  private open = false;
  connected = false;
  coolingDown = false;
  draft = '';
  messages: ChatMessage[] = [];
  presence?: Presence;
  sharingTeamId?: number;
  inviteFeedback = '';
  inviteText = '';

  private socket?: WebSocket;
  private heartbeat?: ReturnType<typeof setInterval>;
  private reconnect?: ReturnType<typeof setTimeout>;
  private cooldown?: ReturnType<typeof setTimeout>;
  private statusRequest?: Subscription;
  private inviteRequest?: Subscription;
  private inviteWindow?: Window;
  private lastPong = 0;
  private destroyed = false;

  constructor(private http: HttpClient, public auth: AuthService, private clipboard: Clipboard) {}

  get missing(): PresenceTeam[] {
    return this.presence?.teams.filter(team => !team.online) ?? [];
  }

  get onlineCount(): number {
    return this.presence?.teams.filter(team => team.online).length ?? 0;
  }

  ngOnInit(): void {
    this.connect();
  }

  private connect(): void {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(protocol + '://' + location.host + '/ws/chat');
    this.socket = socket;
    socket.onopen = () => {
      this.connected = true;
      this.lastPong = Date.now();
      if (this.admin) this.refreshPresence();
      this.heartbeat = setInterval(() => {
        if (Date.now() - this.lastPong > 35000) {
          socket.close();
        } else if (socket.readyState === WebSocket.OPEN) {
          socket.send('ping');
        }
      }, 12000);
    };
    socket.onmessage = ({ data }) => {
      const event = JSON.parse(data);
      if (event.type === 'pong') this.lastPong = Date.now();
      if (event.type === 'history') {
        this.messages = event.messages;
        if (this.open) this.scrollToLatest();
      }
      if (event.type === 'message') {
        this.messages = [...this.messages.slice(-49), event.message];
        if (this.open) this.scrollToLatest();
        else if (event.message.author !== this.auth.username) this.unread++;
      }
      if (this.admin && (event.type === 'presence' || event.presenceChanged)) this.refreshPresence();
    };
    socket.onclose = () => {
      this.connected = false;
      this.presence = undefined;
      this.statusRequest?.unsubscribe();
      clearInterval(this.heartbeat);
      if (!this.destroyed) this.reconnect = setTimeout(() => this.connect(), 3000);
    };
  }

  onToggle(event: Event): void {
    this.open = (event.target as HTMLDetailsElement).open;
    if (this.open) {
      this.unread = 0;
      this.scrollToLatest();
    }
  }

  private scrollToLatest(): void {
    requestAnimationFrame(() => {
      const list = this.messageList?.nativeElement;
      if (list) list.scrollTop = list.scrollHeight;
    });
  }

  refreshPresence(): void {
    this.statusRequest?.unsubscribe();
    this.statusRequest = this.http.get<Presence>('/api/random/presence').subscribe({
      next: status => this.presence = status,
      error: () => this.presence = undefined
    });
  }

  shareInvite(team: PresenceTeam): void {
    this.sharingTeamId = team.id;
    this.inviteText = '';
    this.inviteFeedback = '';
    // The user gesture is still active before the verification request starts.
    const shareWindow = window.open('', '_blank') ?? undefined;
    if (shareWindow) shareWindow.opener = null;
    this.inviteWindow = shareWindow;
    this.inviteRequest = this.http.post<{ message: string }>(
      '/api/admin/participants/' + team.id + '/auction-invite', {}
    ).subscribe({
      next: ({ message }) => {
        this.sharingTeamId = undefined;
        this.inviteWindow = undefined;
        if (shareWindow && !shareWindow.closed) {
          shareWindow.location.replace(this.whatsappUrl(message));
        } else {
          this.inviteText = message;
          this.inviteFeedback = 'Apri WhatsApp oppure copia il testo dell’invito.';
        }
      },
      error: err => {
        this.sharingTeamId = undefined;
        this.inviteWindow = undefined;
        shareWindow?.close();
        this.inviteFeedback = err.error?.message || 'Impossibile preparare l’invito.';
      }
    });
  }

  whatsappUrl(message: string): string {
    return 'https://wa.me/?text=' + encodeURIComponent(message);
  }

  copyFallback(invite: HTMLTextAreaElement): void {
    if (this.clipboard.copy(this.inviteText)) {
      this.inviteText = '';
      this.inviteFeedback = 'Invito copiato';
    } else {
      invite.select();
    }
  }

  send(): void {
    const text = this.draft.trim();
    if (this.socket?.readyState !== WebSocket.OPEN || this.coolingDown || !text) return;
    this.socket.send(':' + text);
    this.draft = '';
    this.coolingDown = true;
    this.cooldown = setTimeout(() => this.coolingDown = false, 1100);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    clearInterval(this.heartbeat);
    clearTimeout(this.reconnect);
    clearTimeout(this.cooldown);
    this.statusRequest?.unsubscribe();
    this.inviteRequest?.unsubscribe();
    this.inviteWindow?.close();
    this.socket?.close();
  }
}
