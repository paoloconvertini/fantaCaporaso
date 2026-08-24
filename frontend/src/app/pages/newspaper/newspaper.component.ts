import { Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../services/admin-api.service';

interface NewspaperMatch {
  home: string;
  away: string;
  result: string;
  goals?: string;
}

@Component({
  selector: 'app-newspaper',
  templateUrl: './newspaper.component.html',
  styleUrls: ['./newspaper.component.css']
})
export class NewspaperComponent {
  file: File | null = null;
  loading = false;
  preview: any = null;
  demo = true;
  generated = false;
  imageFile: File | null = null;
  imageUrl: string | null = null;
  publishing = false;
  publishStatus: any = { enabled: false, message: 'Verifica pubblicazione in corso…' };
  matchday = 'GIORNATA 1';
  kicker = 'IL CAMPIONATO RIPARTE TRA COLPI DI SCENA E VECCHIE CERTEZZE';
  headline = '';
  standfirst = '';
  leadTitle = 'La sentenza del lunedì';
  leadText = '';
  fantasfigaTitle = '';
  fantasfigaText = '';
  briefs: { title: string; text: string }[] = [];

  readonly demoMatches: NewspaperMatch[] = [
    { home: 'Atletico ma non troppo', away: 'HAVANA AMIGOS', result: '66,5 – 73', goals: '1 – 2' },
    { home: 'YOUNG BOYS UNITED', away: 'Ruverpool', result: '78 – 77,5', goals: '3 – 2' },
    { home: 'VIKING 84', away: 'KECAVOLI', result: '59,5 – 66', goals: '0 – 1' },
    { home: 'Em Fallet', away: 'MessiMale', result: '71 – 71', goals: '1 – 1' },
    { home: 'ASTON BIRRA', away: 'GenSim e 2 Monelli', result: '84 – 68', goals: '4 – 1' },
    { home: 'SOLO LEVELING', away: '34 e 1 Gazzosa', result: '65,5 – 60', goals: '0 – 0' },
    { home: 'Corto Muso', away: 'DanPao Salisburgo FC', result: '72 – 66', goals: '2 – 1' },
    { home: 'S.S. 30Lance', away: 'johnsons oil', result: '69 – 75', goals: '1 – 2' }
  ];

  readonly demoStanding = [
    ['1', 'ASTON BIRRA', '3'], ['2', 'YOUNG BOYS UNITED', '3'], ['3', 'johnsons oil', '3'],
    ['4', 'HAVANA AMIGOS', '3'], ['5', 'Corto Muso', '3'], ['6', 'KECAVOLI', '3']
  ];

  constructor(private api: AdminApiService, private snackBar: MatSnackBar) {
    this.api.getNewspaperPublishStatus().subscribe({
      next: status => this.publishStatus = status,
      error: () => this.publishStatus = { enabled: false, message: 'Stato pubblicazione non disponibile.' }
    });
    this.generatePreview();
  }

  selectFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] || null;
    this.preview = null;
  }

  analyze(): void {
    if (!this.file) return;
    this.loading = true;
    this.api.previewNewspaper(this.file).subscribe({
      next: preview => {
        this.preview = preview;
        this.demo = false;
        this.generated = false;
        this.loading = false;
      },
      error: error => {
        this.loading = false;
        this.snackBar.open(error?.error?.error || 'Impossibile analizzare il file', 'Chiudi', { duration: 4000 });
      }
    });
  }

  showDemo(): void {
    this.demo = true;
    this.generatePreview();
  }

  get matches(): NewspaperMatch[] {
    const source = this.demo ? this.demoMatches : (this.preview?.matches || []);
    return source.map((match: NewspaperMatch) => ({ ...match, goals: match.goals || this.resultInGoals(match.result) }));
  }

  generatePreview(): void {
    const matches = this.matches;
    if (!matches.length) return;
    const scored = matches.flatMap(match => {
      const points = this.points(match.result);
      return points ? [{ team: match.home, score: points[0] }, { team: match.away, score: points[1] }] : [];
    }).sort((a, b) => b.score - a.score);
    const best = scored[0];
    const closest = matches.map(match => ({ match, points: this.points(match.result) }))
      .filter(item => item.points).sort((a, b) => Math.abs(a.points![0] - a.points![1]) - Math.abs(b.points![0] - b.points![1]))[0];

    this.headline = best ? `${best.team}, partenza col botto` : 'La giornata è pronta per andare in prima pagina';
    this.standfirst = best
      ? `${best.team} firma il miglior punteggio con ${this.format(best.score)}. Otto sfide inaugurano una giornata già ricca di distacchi minimi.`
      : 'Risultati acquisiti: la redazione prepara titoli e approfondimenti.';
    this.leadText = best
      ? `Con ${this.format(best.score)} Magic punti, ${best.team} si prende la copertina della giornata. Un dato netto, ricavato direttamente dai risultati ufficiali.`
      : 'La prima pagina verrà completata utilizzando soltanto i dati presenti nell’export FantaMaster.';
    const luck = this.fantasfigaRows(matches);
    const luckiest = luck[0];
    const unluckiest = luck[luck.length - 1];
    this.fantasfigaTitle = unluckiest
      ? `${unluckiest.team}, il calendario presenta il conto`
      : 'Il verdetto della FantaSfiga';
    this.fantasfigaText = luckiest && unluckiest
      ? `${luckiest.team} raccoglie ${this.format(luckiest.actualPoints)} punti contro ${this.format(luckiest.expectedPoints)} attesi. `
        + `${unluckiest.team}, invece, si ferma a ${this.format(unluckiest.actualPoints)} nonostante una resa media da ${this.format(unluckiest.expectedPoints)} punti. `
        + this.benchContribution()
      : 'Servono risultati calcolati per misurare fortuna, calendario e bonus arrivati dalla panchina.';
    this.briefs = closest ? [
      { title: 'Questione di decimali', text: `${closest.match.home} e ${closest.match.away} sono separate da appena ${this.format(Math.abs(closest.points![0] - closest.points![1]))} punti.` },
      { title: 'Il numero della giornata', text: `${matches.length} partite analizzate e ${this.preview?.teamsFound || 16} squadre in campo: il campionato ha emesso i primi verdetti.` },
      { title: 'La voce della redazione', text: 'La stagione è lunga. Le prese in giro, molto di più.' }
    ] : [];
    this.generated = true;
  }

  selectImage(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] || null;
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.snackBar.open('Usa un’immagine JPG, PNG o WebP inferiore a 5 MB', 'Chiudi', { duration: 4000 });
      return;
    }
    this.imageFile = file;
    const reader = new FileReader();
    reader.onload = () => this.imageUrl = String(reader.result);
    reader.readAsDataURL(file);
  }

  publish(): void {
    if (!this.publishStatus?.enabled || !this.imageFile || !this.generated) return;
    if (!window.confirm('Pubblicare questa prima pagina sul progetto Cloudflare Gazzetta?')) return;
    this.publishing = true;
    this.api.publishNewspaper(this.edition(), this.imageFile).subscribe({
      next: () => {
        this.publishing = false;
        this.snackBar.open('Gazzetta pubblicata', 'Chiudi', { duration: 3500 });
      },
      error: error => {
        this.publishing = false;
        this.snackBar.open(error?.error?.error || 'Pubblicazione non riuscita', 'Chiudi', { duration: 4500 });
      }
    });
  }

  private edition(): any {
    return {
      matchday: this.matchday, kicker: this.kicker, headline: this.headline, standfirst: this.standfirst,
      leadTitle: this.leadTitle, leadText: this.leadText, fantasfigaTitle: this.fantasfigaTitle,
      fantasfigaText: this.fantasfigaText, briefs: this.briefs, matches: this.matches,
      standings: this.demo ? this.demoStanding.map(row => ({ position: +row[0], team: row[1], record: '', points: row[2] })) : this.preview?.standings || []
    };
  }

  private points(result: string): [number, number] | null {
    const values = String(result || '').split(/\s*[-–]\s*/).map(value => Number(value.replace(',', '.')));
    return values.length === 2 && values.every(Number.isFinite) ? [values[0], values[1]] : null;
  }

  private resultInGoals(result: string): string {
    const points = this.points(result);
    if (!points) return '–';
    let home = points[0] >= 66 ? Math.floor((points[0] - 66) / 6) + 1 : 0;
    let away = points[1] >= 66 ? Math.floor((points[1] - 66) / 6) + 1 : 0;
    if (points[0] < 66 && points[1] < 66 && Math.abs(points[0] - points[1]) >= 6) {
      if (points[0] > points[1]) home = 1; else away = 1;
    }
    return `${home} – ${away}`;
  }

  private format(value: number): string {
    return value.toLocaleString('it-IT', { maximumFractionDigits: 1 });
  }

  private fantasfigaRows(matches: NewspaperMatch[]): any[] {
    if (!this.demo && this.preview?.fantasfiga?.length) return this.preview.fantasfiga;
    const teams = matches.flatMap(match => {
      const scores = this.points(match.result);
      if (!scores) return [];
      const goals = this.goalPair(scores[0], scores[1]);
      return [
        { team: match.home, score: scores[0], actualPoints: this.resultPoints(goals[0], goals[1]) },
        { team: match.away, score: scores[1], actualPoints: this.resultPoints(goals[1], goals[0]) }
      ];
    });
    return teams.map(team => {
      const hypothetical = teams.filter(opponent => opponent !== team).reduce((total, opponent) => {
        const goals = this.goalPair(team.score, opponent.score);
        return total + this.resultPoints(goals[0], goals[1]);
      }, 0);
      const expectedPoints = hypothetical / Math.max(1, teams.length - 1);
      return { ...team, expectedPoints, delta: team.actualPoints - expectedPoints };
    }).sort((a, b) => b.delta - a.delta);
  }

  private goalPair(homeScore: number, awayScore: number): [number, number] {
    let home = homeScore >= 66 ? Math.floor((homeScore - 66) / 6) + 1 : 0;
    let away = awayScore >= 66 ? Math.floor((awayScore - 66) / 6) + 1 : 0;
    if (homeScore < 66 && awayScore < 66 && Math.abs(homeScore - awayScore) >= 6) {
      if (homeScore > awayScore) home = 1; else away = 1;
    }
    return [home, away];
  }

  private resultPoints(goals: number, opponentGoals: number): number {
    return goals > opponentGoals ? 3 : goals === opponentGoals ? 1 : 0;
  }

  private benchContribution(): string {
    const substitute = (this.preview?.teamSheets || []).flatMap((sheet: any) =>
      (sheet.players || []).filter((player: any) => !player.starter && player.counted)
        .map((player: any) => ({ ...player, team: sheet.team })))
      .sort((a: any, b: any) => this.numeric(b.fantasyVote) - this.numeric(a.fantasyVote))[0];
    return substitute
      ? `Dalla panchina spicca ${substitute.name} per ${substitute.team}: fantavoto ${substitute.fantasyVote || 'conteggiato'}.`
      : 'Gli episodi decisivi dalla panchina saranno evidenziati quando presenti nel calcolo FantaMaster.';
  }

  private numeric(value: string): number {
    const parsed = Number(String(value || '').replace(',', '.'));
    return Number.isFinite(parsed) ? parsed : -Infinity;
  }
}
